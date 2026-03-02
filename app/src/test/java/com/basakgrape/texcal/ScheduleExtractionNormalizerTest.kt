package com.basakgrape.texcal

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneId

class ScheduleExtractionNormalizerTest {

    private val zone: ZoneId = ZoneId.of("UTC")
    private val today: LocalDate = LocalDate.of(2026, 3, 2)

    @Test
    fun validateScheduleSchema_validJson_passes() {
        val json = validBaseJson()
        ScheduleExtractionNormalizer.validateScheduleSchema(json)
    }

    @Test
    fun validateScheduleSchema_missingKey_fails() {
        val json = validBaseJson().apply { remove("confidence") }

        val error = org.junit.Assert.assertThrows(IllegalArgumentException::class.java) {
            ScheduleExtractionNormalizer.validateScheduleSchema(json)
        }

        assertTrue(error.message?.contains("missing key") == true)
    }

    @Test
    fun validateScheduleSchema_outOfRangeConfidence_fails() {
        val json = validBaseJson().put("confidence", 1.2)

        val error = org.junit.Assert.assertThrows(IllegalArgumentException::class.java) {
            ScheduleExtractionNormalizer.validateScheduleSchema(json)
        }

        assertTrue(error.message?.contains("confidence") == true)
    }

    @Test
    fun convertToScheduleResult_withoutTime_forcesAllDay() {
        val json = validBaseJson()
            .put("start_date", "2026-03-10")
            .put("end_date", "")
            .put("start_time", "")
            .put("end_time", "")
            .put("all_day", false)

        val result = ScheduleExtractionNormalizer.convertToScheduleResult(
            json = json,
            rawText = "테스트",
            zone = zone,
            today = today
        )

        val expectedStart = LocalDate.of(2026, 3, 10)
            .atStartOfDay(zone)
            .toInstant()
            .toEpochMilli()
        val expectedEnd = LocalDate.of(2026, 3, 11)
            .atStartOfDay(zone)
            .toInstant()
            .toEpochMilli()

        assertTrue(result.allDay)
        assertEquals(expectedStart, result.startMillis)
        assertEquals(expectedEnd, result.endMillis)
    }

    @Test
    fun convertToScheduleResult_whenEndBeforeStart_adjustsToOneHour() {
        val json = validBaseJson()
            .put("start_date", "2026-03-10")
            .put("end_date", "2026-03-10")
            .put("start_time", "15:00")
            .put("end_time", "14:00")
            .put("all_day", false)

        val result = ScheduleExtractionNormalizer.convertToScheduleResult(
            json = json,
            rawText = "테스트",
            zone = zone,
            today = today
        )

        assertTrue(!result.allDay)
        assertEquals(60 * 60 * 1000L, result.endMillis - result.startMillis)
    }

    private fun validBaseJson(): JSONObject {
        return JSONObject()
            .put("title", "회의")
            .put("description", "테스트 설명")
            .put("location", "회의실")
            .put("start_date", "2026-03-10")
            .put("end_date", "2026-03-10")
            .put("start_time", "10:00")
            .put("end_time", "11:00")
            .put("all_day", false)
            .put("confidence", 0.9)
    }
}
