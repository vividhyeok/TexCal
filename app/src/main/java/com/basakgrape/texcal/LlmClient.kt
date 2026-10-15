package com.basakgrape.texcal

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.io.InterruptedIOException
import java.time.ZonedDateTime
import java.util.concurrent.TimeUnit

enum class Provider(val label: String, val url: String, val defaultModel: String) {
    OPENAI("OpenAI", "https://api.openai.com/v1/chat/completions", "gpt-4.1-mini"),
    DEEPSEEK("DeepSeek", "https://api.deepseek.com/chat/completions", "deepseek-chat")
}

class LlmFailure(val kind: String) : Exception(kind)

object LlmClient {
    private val client = OkHttpClient.Builder().callTimeout(45, TimeUnit.SECONDS).build()
    private val names = listOf("title", "description", "location", "start_date", "end_date", "start_time", "end_time", "all_day", "time_zone", "recurrence", "reminder_minutes", "evidence", "uncertain")
    private fun schema(): JSONObject {
        val props = JSONObject()
        names.forEach { name ->
            val type = when (name) {
                "all_day" -> JSONObject().put("type", "boolean")
                "reminder_minutes" -> JSONObject().put("type", JSONArray().put("integer").put("null"))
                "uncertain" -> JSONObject().put("type", "array").put("items", JSONObject().put("type", "string"))
                else -> JSONObject().put("type", "string")
            }
            props.put(name, type)
        }
        val event = JSONObject().put("type", "object").put("properties", props).put("required", JSONArray(names)).put("additionalProperties", false)
        return JSONObject().put("type", "object").put("properties", JSONObject().put("events", JSONObject().put("type", "array").put("items", event))).put("required", JSONArray().put("events")).put("additionalProperties", false)
    }

    suspend fun extract(provider: Provider, key: String, model: String, raw: String): List<ScheduleDraft> = withContext(Dispatchers.IO) {
        val now = ZonedDateTime.now()
        val instruction = """Extract every distinct calendar event from the Korean or English text. Return JSON only, with an events array; use [] for non-events. Current local datetime: $now. Resolve relative dates from this date and timezone. Never invent dates or places. Missing values must be empty strings. all_day true only if the source explicitly indicates a full day, never merely because time is missing. evidence must be an exact contiguous quote from the source. Put every uncertain or inferred field name in uncertain. If end time is absent leave it empty. If the text crosses midnight set the next end_date. recurrence is an RFC5545 RRULE without RRULE: or empty. reminder_minutes is null unless explicitly requested. Every event has title, description, location, start_date, end_date, start_time, end_time, all_day, time_zone, recurrence, reminder_minutes, evidence, uncertain. Dates ISO yyyy-MM-dd and times HH:mm."""
        val body = JSONObject().put("model", model).put("messages", JSONArray().put(JSONObject().put("role", "system").put("content", instruction)).put(JSONObject().put("role", "user").put("content", raw)))
        body.put("response_format", if (provider == Provider.OPENAI) JSONObject().put("type", "json_schema").put("json_schema", JSONObject().put("name", "schedule_events").put("strict", true).put("schema", schema())) else JSONObject().put("type", "json_object"))
        val request = Request.Builder().url(provider.url).header("Authorization", "Bearer $key").post(body.toString().toRequestBody("application/json; charset=utf-8".toMediaType())).build()
        val response = try { client.newCall(request).execute() } catch (_: InterruptedIOException) { throw LlmFailure("요청 시간이 초과되었습니다") } catch (_: Exception) { throw LlmFailure("네트워크 오류가 발생했습니다") }
        response.use {
            if (!it.isSuccessful) throw LlmFailure(when (it.code) { 401, 403 -> "API 인증에 실패했습니다"; 429 -> "요청 한도를 초과했습니다"; in 500..599 -> "공급자 서버 오류가 발생했습니다"; else -> "API 요청이 거부되었습니다 (${it.code})" })
            val content = try { JSONObject(it.body?.string().orEmpty()).getJSONArray("choices").getJSONObject(0).getJSONObject("message").getString("content") } catch (_: Exception) { throw LlmFailure("API 응답을 읽을 수 없습니다") }
            try { ScheduleExtractionNormalizer.parse(JSONObject(content), raw) } catch (_: Exception) { throw LlmFailure("일정 응답의 형식 또는 내용이 잘못되었습니다. 다시 시도해 주세요") }
        }
    }
}
