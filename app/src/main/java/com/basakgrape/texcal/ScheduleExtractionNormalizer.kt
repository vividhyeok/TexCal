package com.basakgrape.texcal

import org.json.JSONObject
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZonedDateTime

object ScheduleExtractionNormalizer {

    fun validateScheduleSchema(json: JSONObject) {
        val requiredKeys = listOf(
            "title",
            "description",
            "location",
            "start_date",
            "end_date",
            "start_time",
            "end_time",
            "all_day",
            "confidence"
        )

        requiredKeys.forEach { key ->
            if (!json.has(key)) {
                throw IllegalArgumentException("missing key: $key")
            }
        }

        val title = json.get("title")
        val description = json.get("description")
        val location = json.get("location")
        if (title !is String) throw IllegalArgumentException("title must be string")
        if (description !is String) throw IllegalArgumentException("description must be string")
        if (location !is String) throw IllegalArgumentException("location must be string")

        val startDate = json.get("start_date")
        val endDate = json.get("end_date")
        val startTime = json.get("start_time")
        val endTime = json.get("end_time")

        if (startDate !is String) throw IllegalArgumentException("start_date must be string")
        if (endDate !is String) throw IllegalArgumentException("end_date must be string")
        if (startTime !is String) throw IllegalArgumentException("start_time must be string")
        if (endTime !is String) throw IllegalArgumentException("end_time must be string")

        if (startDate.isNotBlank()) {
            try {
                LocalDate.parse(startDate)
            } catch (_: Exception) {
                throw IllegalArgumentException("start_date format must be YYYY-MM-DD")
            }
        }

        if (endDate.isNotBlank()) {
            try {
                LocalDate.parse(endDate)
            } catch (_: Exception) {
                throw IllegalArgumentException("end_date format must be YYYY-MM-DD")
            }
        }

        if (startTime.isNotBlank()) {
            try {
                LocalTime.parse(startTime)
            } catch (_: Exception) {
                throw IllegalArgumentException("start_time format must be HH:MM")
            }
        }

        if (endTime.isNotBlank()) {
            try {
                LocalTime.parse(endTime)
            } catch (_: Exception) {
                throw IllegalArgumentException("end_time format must be HH:MM")
            }
        }

        val allDayValue = json.get("all_day")
        if (allDayValue !is Boolean) {
            throw IllegalArgumentException("all_day must be boolean")
        }

        val confidenceValue = json.get("confidence")
        if (confidenceValue !is Number) {
            throw IllegalArgumentException("confidence must be number")
        }

        val confidence = confidenceValue.toDouble()
        if (confidence < 0.0 || confidence > 1.0) {
            throw IllegalArgumentException("confidence must be 0..1")
        }
    }

    fun convertToScheduleResult(
        json: JSONObject,
        rawText: String,
        zone: ZoneId = ZoneId.systemDefault(),
        today: LocalDate = LocalDate.now(zone)
    ): ScheduleResult {
        val defaultTime = LocalTime.of(13, 0)

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

        val rawStartDateStr = json.optString("start_date").takeIf { it.isNotBlank() }
        val rawEndDateStr = json.optString("end_date").takeIf { it.isNotBlank() }

        val startDate = parseDate(rawStartDateStr) ?: today
        val endDateParsed = parseDate(rawEndDateStr) ?: startDate
        val endDate = if (endDateParsed.isBefore(startDate)) startDate else endDateParsed

        val rawStartTimeStr = json.optString("start_time").takeIf { it.isNotBlank() }
        val rawEndTimeStr = json.optString("end_time").takeIf { it.isNotBlank() }

        val hasTimeInfo = !rawStartTimeStr.isNullOrBlank() || !rawEndTimeStr.isNullOrBlank()
        val allDayFromJson = json.optBoolean("all_day", false)
        val allDay = if (!hasTimeInfo) true else allDayFromJson

        val (startMillis, endMillis) =
            if (allDay) {
                val startZdt = startDate.atStartOfDay(zone)
                val endZdt = endDate.plusDays(1).atStartOfDay(zone)
                startZdt.toInstant().toEpochMilli() to endZdt.toInstant().toEpochMilli()
            } else {
                val startTime = parseTime(rawStartTimeStr) ?: defaultTime
                val endTime = parseTime(rawEndTimeStr) ?: startTime.plusHours(1)

                val startZdt = ZonedDateTime.of(startDate, startTime, zone)
                val endCandidate = ZonedDateTime.of(endDate, endTime, zone)
                val endZdt = if (endCandidate.isAfter(startZdt)) endCandidate else startZdt.plusHours(1)

                startZdt.toInstant().toEpochMilli() to endZdt.toInstant().toEpochMilli()
            }

        val title = json.optString("title").ifBlank { "제목 없음" }
        val description = json.optString("description").ifBlank { rawText.take(500) }
        val location = json.optString("location").ifBlank { "장소 없음" }
        val confidence = json.optDouble("confidence", 0.0).coerceIn(0.0, 1.0)

        return ScheduleResult(
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
