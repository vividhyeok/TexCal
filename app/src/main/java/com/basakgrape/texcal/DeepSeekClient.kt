package com.basakgrape.texcal

import android.util.Log
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
import java.time.ZoneId

object DeepSeekClient {

    private const val TAG = "DeepSeekClient"
    private const val API_URL = "https://api.deepseek.com/chat/completions"
    private const val PROMPT_VERSION = "v2"
    private const val MAX_SCHEMA_RETRY = 1
    private const val WARN_LATENCY_MS = 7_000L
    private const val WARN_TOTAL_TOKENS = 3_000

    private val client = OkHttpClient()

    private data class CompletionResponse(
        val content: String,
        val latencyMs: Long,
        val promptTokens: Int?,
        val completionTokens: Int?,
        val totalTokens: Int?
    )

    suspend fun parseSchedule(
        apiKey: String,
        model: String = ApiKeyStore.DEFAULT_DEEPSEEK_MODEL,
        rawText: String
    ): ScheduleParseOutput = withContext(Dispatchers.IO) {
        var lastValidationError = ""

        repeat(MAX_SCHEMA_RETRY + 1) { attempt ->
            val strictMode = attempt > 0
            val response = requestScheduleJson(
                apiKey = apiKey,
                model = model,
                rawText = rawText,
                strictMode = strictMode,
                validationHint = lastValidationError
            )

            try {
                val json = JSONObject(response.content)
                ScheduleExtractionNormalizer.validateScheduleSchema(json)

                val result = ScheduleExtractionNormalizer.convertToScheduleResult(
                    json = json,
                    rawText = rawText,
                    zone = ZoneId.systemDefault()
                )

                Log.i(
                    TAG,
                    "parse ok model=$model prompt=$PROMPT_VERSION retry=$strictMode latencyMs=${response.latencyMs} " +
                        "promptTokens=${response.promptTokens ?: -1} completionTokens=${response.completionTokens ?: -1} " +
                        "totalTokens=${response.totalTokens ?: -1} confidence=${"%.2f".format(result.confidence)}"
                )
                if (response.latencyMs >= WARN_LATENCY_MS) {
                    Log.w(
                        TAG,
                        "latency warn model=$model latencyMs=${response.latencyMs} thresholdMs=$WARN_LATENCY_MS"
                    )
                }
                val totalTokens = response.totalTokens ?: -1
                if (totalTokens >= WARN_TOTAL_TOKENS) {
                    Log.w(
                        TAG,
                        "token warn model=$model totalTokens=$totalTokens threshold=$WARN_TOTAL_TOKENS"
                    )
                }

                return@withContext ScheduleParseOutput(
                    result = result,
                    promptVersion = PROMPT_VERSION,
                    retriedForSchema = strictMode
                )
            } catch (e: Exception) {
                lastValidationError = e.message ?: "unknown schema error"
                Log.w(
                    TAG,
                    "schema invalid model=$model prompt=$PROMPT_VERSION retry=$strictMode reason=$lastValidationError"
                )

                if (attempt == MAX_SCHEMA_RETRY) {
                    throw DeepSeekResponseFormatException("응답 형식 검증 실패: $lastValidationError")
                }
            }
        }

        throw DeepSeekResponseFormatException("응답 형식 검증 실패: unknown")
    }

    private fun buildSystemPrompt(
        todayStr: String,
        strictMode: Boolean,
        validationHint: String
    ): String {
        val basePrompt = """
You are a scheduling assistant for Google Calendar.
The user shares arbitrary text, often in Korean.
Your job is to extract ONE likely schedule only if the text is reasonably unambiguous.

Today is $todayStr in the user's local timezone.
When the text uses relative expressions like "오늘", "내일", "모레", "이번 주 토요일",
you MUST convert them into concrete calendar dates based on this date.

Output STRICTLY a JSON object with these exact keys:
- title (string)
- description (string)
- location (string)
- start_date (string, format YYYY-MM-DD or "")
- end_date (string, format YYYY-MM-DD or "")
- start_time (string, format HH:MM or "")
- end_time (string, format HH:MM or "")
- all_day (boolean)
- confidence (number between 0 and 1)

Rules:
- Always include all keys.
- If value is unknown, use empty string for date/time/location/title/description.
- If time of day is unclear, all_day should be true.
- If schedule is vague, confidence must be below 0.3.
- Never output markdown, explanation, or code block. Output JSON only.
""".trimIndent()

        if (!strictMode) return basePrompt

        return """
$basePrompt

Previous output failed schema validation: $validationHint
Fix format strictly and return valid JSON only.
""".trimIndent()
    }

    private fun requestScheduleJson(
        apiKey: String,
        model: String,
        rawText: String,
        strictMode: Boolean,
        validationHint: String
    ): CompletionResponse {
        val todayStr = LocalDate.now(ZoneId.systemDefault()).toString()
        val systemPrompt = buildSystemPrompt(todayStr, strictMode, validationHint)

        val messages = JSONArray()
            .put(JSONObject().put("role", "system").put("content", systemPrompt))
            .put(JSONObject().put("role", "user").put("content", rawText))

        val bodyJson = JSONObject()
            .put("model", model)
            .put("response_format", JSONObject().put("type", "json_object"))
            .put("temperature", 0.0)
            .put("top_p", 0.1)
            .put("messages", messages)

        val mediaType = "application/json; charset=utf-8".toMediaType()
        val requestBody = bodyJson.toString().toRequestBody(mediaType)

        val request = Request.Builder()
            .url(API_URL)
            .header("Authorization", "Bearer $apiKey")
            .post(requestBody)
            .build()

        val startNanos = System.nanoTime()
        val response = try {
            client.newCall(request).execute()
        } catch (e: IOException) {
            throw DeepSeekNetworkException("네트워크 오류로 DeepSeek 호출에 실패했습니다.", e)
        }
        val latencyMs = (System.nanoTime() - startNanos) / 1_000_000

        val responseStr = response.body?.string().orEmpty()

        if (!response.isSuccessful) {
            val message = "DeepSeek error ${response.code}: ${responseStr.take(300)}"
            when (response.code) {
                401, 403 -> throw DeepSeekAuthException(message)
                429 -> throw DeepSeekRateLimitException(message)
                in 500..599 -> throw DeepSeekServerException(message)
                else -> throw DeepSeekApiException(message)
            }
        }

        if (responseStr.isBlank()) {
            throw DeepSeekResponseFormatException("Empty DeepSeek response")
        }

        val root = try {
            JSONObject(responseStr)
        } catch (e: Exception) {
            throw DeepSeekResponseFormatException("응답 JSON 파싱 실패: ${e.message}")
        }

        val content = runCatching {
            root
                .getJSONArray("choices")
                .getJSONObject(0)
                .getJSONObject("message")
                .getString("content")
        }.getOrElse {
            throw DeepSeekResponseFormatException("응답 content 필드 파싱 실패: ${it.message}")
        }

        if (content.isBlank()) {
            throw DeepSeekResponseFormatException("응답 content가 비어 있습니다.")
        }

        val usage = root.optJSONObject("usage")
        return CompletionResponse(
            content = content,
            latencyMs = latencyMs,
            promptTokens = usage?.optInt("prompt_tokens")?.takeIf { it >= 0 },
            completionTokens = usage?.optInt("completion_tokens")?.takeIf { it >= 0 },
            totalTokens = usage?.optInt("total_tokens")?.takeIf { it >= 0 }
        )
    }
}
