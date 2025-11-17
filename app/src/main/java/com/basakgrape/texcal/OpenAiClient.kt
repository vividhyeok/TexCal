package com.basakgrape.texcal

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZonedDateTime

object OpenAiClient {

    private const val MODEL = "gpt-4o-mini"
    private const val API_URL = "https://api.openai.com/v1/chat/completions"

    private val client = OkHttpClient()

    suspend fun parseSchedule(apiKey: String, rawText: String): ScheduleResult =
        withContext(Dispatchers.IO) {

            // 🕒 0. 오늘 날짜/타임존 구해서 프롬프트에 박아 넣기
            val zone = ZoneId.systemDefault()
            val today = LocalDate.now(zone)
            val todayStr = today.toString()

            // 1. 프롬프트: 상대 날짜 + all_day 규칙까지 명시
            val systemPrompt = """
You are a scheduling assistant for Google Calendar.
The user shares arbitrary text, often in Korean.
Your job is to extract ONE likely schedule only if the text is reasonably unambiguous.

Today is $todayStr in the user's local timezone.
When the text uses relative expressions like "오늘", "내일", "모레", "이번 주 토요일",
you MUST convert them into concrete calendar dates based on this date.

Output STRICTLY a JSON object with these fields:

- title: short title (string)
- description: longer explanation (string)
- location: place name or "장소 없음"
- start_date: "YYYY-MM-DD" if identifiable, otherwise "" 
- end_date: same rule. If only one date is known, set end_date = start_date.
- start_time: "HH:MM" 24h format if a specific time of day is clearly mentioned, otherwise "".
- end_time: "HH:MM" if a specific end time is mentioned, otherwise "".
- all_day:
    - true if the time of day is not clearly specified (date-only or full-day context),
    - false only when there is an explicit or strongly implied time range (e.g. "10시에", "7시부터 9시까지").
- confidence: number between 0 and 1  
  - 1.0 = very clear schedule  
  - 0.0 = cannot identify any schedule  
  - If the text is vague (e.g., "나중에 하자", "일단 잡아두자"), confidence MUST be below 0.3

If schedule cannot be determined with high certainty, set confidence low (<0.3)
and leave date/time empty. In that case, do NOT fabricate times.
""".trimIndent()

            // 2. 요청 JSON
            val messages = JSONArray()
                .put(JSONObject().put("role", "system").put("content", systemPrompt))
                .put(JSONObject().put("role", "user").put("content", rawText))

            val bodyJson = JSONObject()
                .put("model", MODEL)
                .put("response_format", JSONObject().put("type", "json_object"))
                .put("messages", messages)

            val mediaType = "application/json; charset=utf-8".toMediaType()
            val requestBody = bodyJson.toString().toRequestBody(mediaType)

            val request = Request.Builder()
                .url(API_URL)
                .header("Authorization", "Bearer $apiKey")
                .post(requestBody)
                .build()

            val response = client.newCall(request).execute()
            if (!response.isSuccessful) {
                val errorBody = response.body?.string()
                throw IOException("OpenAI error ${response.code}: $errorBody")
            }

            val responseStr = response.body?.string()
                ?: throw IOException("Empty OpenAI response")

            // 3. message.content 안의 JSON 파싱
            val root = JSONObject(responseStr)
            val content = root
                .getJSONArray("choices")
                .getJSONObject(0)
                .getJSONObject("message")
                .getString("content")

            val json = JSONObject(content)

            // 4. 날짜/시간 파싱 + 디폴트 규칙
            val defaultTime = LocalTime.of(13, 0) // 시간이 있는데 애매하면 13:00을 기본값으로

            fun parseDate(str: String?): LocalDate? =
                try {
                    if (str.isNullOrBlank()) null else LocalDate.parse(str)
                } catch (_: Exception) {
                    null
                }

            fun parseTime(str: String?): LocalTime? =
                try {
                    if (str.isNullOrBlank()) null else LocalTime.parse(str)
                } catch (_: Exception) {
                    null
                }

            val rawStartDateStr = json.optString("start_date", null)
            val rawEndDateStr = json.optString("end_date", null)

            val startDate = parseDate(rawStartDateStr) ?: today
            val endDate = parseDate(rawEndDateStr) ?: startDate

            val rawStartTimeStr = json.optString("start_time", null)
            val rawEndTimeStr = json.optString("end_time", null)

            // 👉 어떤 식으로든 시간 문자열이 있으면 "시간 정보 있음" 으로 판단
            val hasTimeInfo =
                !rawStartTimeStr.isNullOrBlank() || !rawEndTimeStr.isNullOrBlank()

            val allDayFromJson = json.optBoolean("all_day", false)

            // 🔥 핵심: 시간이 하나도 없으면 무조건 종일(all-day)로 보정
            val allDay = if (!hasTimeInfo) true else allDayFromJson

            val (startMillis, endMillis) =
                if (allDay) {
                    // 종일 일정: [start 00:00, end+1 00:00)
                    val startZdt = startDate.atStartOfDay(zone)
                    val endZdt = endDate.plusDays(1).atStartOfDay(zone)
                    startZdt.toInstant().toEpochMilli() to endZdt.toInstant().toEpochMilli()
                } else {
                    val startTime =
                        parseTime(rawStartTimeStr) ?: defaultTime
                    val endTime =
                        parseTime(rawEndTimeStr) ?: startTime.plusHours(1)

                    val startZdt = ZonedDateTime.of(startDate, startTime, zone)
                    val endZdt = ZonedDateTime.of(endDate, endTime, zone)
                    startZdt.toInstant().toEpochMilli() to endZdt.toInstant().toEpochMilli()
                }

            val title = json.optString("title").ifBlank { "제목 없음" }
            val description = json.optString("description").ifBlank { rawText.take(500) }
            val location = json.optString("location").ifBlank { "장소 없음" }
            val confidence = json.optDouble("confidence", 0.0)

            ScheduleResult(
                title = title,
                description = description,
                location = location,
                startMillis = startMillis,
                endMillis = endMillis,
                allDay = allDay,
                confidence = confidence
            )
        }
}
