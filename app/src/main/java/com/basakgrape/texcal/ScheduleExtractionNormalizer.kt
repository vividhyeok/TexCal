package com.basakgrape.texcal

import org.json.JSONObject
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId

object ScheduleExtractionNormalizer {
    private val fields = listOf("title", "description", "location", "start_date", "end_date", "start_time", "end_time", "all_day", "time_zone", "recurrence", "reminder_minutes", "evidence", "uncertain")

    fun parse(root: JSONObject, rawText: String, deviceZone: ZoneId = ZoneId.systemDefault()): List<ScheduleDraft> {
        val items = root.optJSONArray("events") ?: throw IllegalArgumentException("events 배열이 없습니다")
        if (items.length() > 30) throw IllegalArgumentException("일정이 너무 많습니다")
        return (0 until items.length()).map { index ->
            val item = items.optJSONObject(index) ?: throw IllegalArgumentException("일정 형식이 잘못되었습니다")
            fields.forEach { if (!item.has(it)) throw IllegalArgumentException("$it 필드가 없습니다") }
            fun string(key: String): String = item.opt(key) as? String ?: throw IllegalArgumentException("$key 형식이 잘못되었습니다")
            val title = string("title").trim()
            val evidence = string("evidence").trim()
            val startDate = string("start_date")
            val endDate = string("end_date")
            val startTime = string("start_time")
            val endTime = string("end_time")
            val zone = string("time_zone").ifBlank { deviceZone.id }
            val recurrence = string("recurrence")
            val allDay = item.opt("all_day") as? Boolean ?: throw IllegalArgumentException("all_day 형식이 잘못되었습니다")
            val reminder = item.opt("reminder_minutes").let { if (it == JSONObject.NULL) null else (it as? Number)?.toInt() ?: throw IllegalArgumentException("알림 형식이 잘못되었습니다") }
            val uncertainty = item.optJSONArray("uncertain") ?: throw IllegalArgumentException("uncertain 배열이 없습니다")
            val uncertain = (0 until uncertainty.length()).map { uncertainty.optString(it) }
            if (title.isBlank() || evidence.isBlank() || !rawText.contains(evidence)) throw IllegalArgumentException("원문 근거가 없습니다")
            if (startDate.isNotBlank()) LocalDate.parse(startDate)
            if (endDate.isNotBlank()) LocalDate.parse(endDate)
            if (startTime.isNotBlank()) LocalTime.parse(startTime)
            if (endTime.isNotBlank()) LocalTime.parse(endTime)
            ZoneId.of(zone)
            if (endDate.isNotBlank() && startDate.isBlank()) throw IllegalArgumentException("시작 날짜가 없습니다")
            if (startDate.isNotBlank() && endDate.isNotBlank() && endDate < startDate) throw IllegalArgumentException("종료 날짜가 빠릅니다")
            if (startDate == endDate && startTime.isNotBlank() && endTime.isNotBlank() && endTime <= startTime) throw IllegalArgumentException("종료 시간이 빠릅니다")
            if (allDay && (startTime.isNotBlank() || endTime.isNotBlank())) throw IllegalArgumentException("종일 일정에 시간이 있습니다")
            if (reminder != null && reminder !in 0..40320) throw IllegalArgumentException("알림 범위가 잘못되었습니다")
            if (recurrence.isNotBlank() && !Regex("^FREQ=(DAILY|WEEKLY|MONTHLY|YEARLY)(;[A-Z]+=[A-Z0-9,]+)*$").matches(recurrence)) throw IllegalArgumentException("반복 규칙이 잘못되었습니다")
            val warnings = uncertain.toMutableList()
            if (startDate.isBlank()) warnings.add("시작 날짜")
            if (!allDay && startTime.isBlank()) warnings.add("시작 시간")
            if (!allDay && endTime.isBlank()) warnings.add("종료 시간: 기본 1시간 제안")
            ScheduleDraft(title, string("description"), string("location"), startDate, endDate, startTime, endTime, allDay, zone, recurrence, reminder, evidence, warnings.distinct(), startDate.isNotBlank())
        }
    }
}
