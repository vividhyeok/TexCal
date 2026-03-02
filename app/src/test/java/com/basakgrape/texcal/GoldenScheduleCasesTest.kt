package com.basakgrape.texcal

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneId

class GoldenScheduleCasesTest {

    private val zone: ZoneId = ZoneId.of("UTC")
    private val today: LocalDate = LocalDate.of(2026, 3, 2)

    @Test
    fun goldenCases_matchExpectedNormalization() {
        val cases = listOf(
            Triple(
                JSONObject()
                    .put("title", "팀 점심")
                    .put("description", "점심 모임")
                    .put("location", "강남")
                    .put("start_date", "2026-03-15")
                    .put("end_date", "2026-03-15")
                    .put("start_time", "")
                    .put("end_time", "")
                    .put("all_day", true)
                    .put("confidence", 0.88),
                true,
                24 * 60 * 60 * 1000L
            ),
            Triple(
                JSONObject()
                    .put("title", "고객 미팅")
                    .put("description", "제안서 설명")
                    .put("location", "판교")
                    .put("start_date", "2026-03-16")
                    .put("end_date", "2026-03-16")
                    .put("start_time", "14:30")
                    .put("end_time", "16:00")
                    .put("all_day", false)
                    .put("confidence", 0.91),
                false,
                90 * 60 * 1000L
            ),
            Triple(
                JSONObject()
                    .put("title", "")
                    .put("description", "")
                    .put("location", "")
                    .put("start_date", "")
                    .put("end_date", "")
                    .put("start_time", "")
                    .put("end_time", "")
                    .put("all_day", false)
                    .put("confidence", 0.2),
                true,
                24 * 60 * 60 * 1000L
            )
        )

        cases.forEach { (json, expectedAllDay, expectedDurationMs) ->
            ScheduleExtractionNormalizer.validateScheduleSchema(json)
            val result = ScheduleExtractionNormalizer.convertToScheduleResult(
                json = json,
                rawText = "원문",
                zone = zone,
                today = today
            )

            assertEquals(expectedAllDay, result.allDay)
            assertEquals(expectedDurationMs, result.endMillis - result.startMillis)
            assertTrue(result.confidence in 0.0..1.0)
        }
    }

    @Test
    fun normalization_isDeterministic_forSameInput() {
        val json = JSONObject()
            .put("title", "주간 회의")
            .put("description", "스프린트 공유")
            .put("location", "회의실 A")
            .put("start_date", "2026-03-20")
            .put("end_date", "2026-03-20")
            .put("start_time", "09:00")
            .put("end_time", "10:00")
            .put("all_day", false)
            .put("confidence", 0.95)

        val first = ScheduleExtractionNormalizer.convertToScheduleResult(
            json = json,
            rawText = "원문",
            zone = zone,
            today = today
        )

        repeat(50) {
            val next = ScheduleExtractionNormalizer.convertToScheduleResult(
                json = json,
                rawText = "원문",
                zone = zone,
                today = today
            )
            assertEquals(first, next)
        }
    }
}
