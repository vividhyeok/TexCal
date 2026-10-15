package com.basakgrape.texcal

import android.content.ContentUris
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.provider.CalendarContract
import java.time.Duration
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZoneOffset

data class CalendarChoice(val id: Long, val name: String)
data class SaveReport(val ids: List<Long>, val skipped: Int, val conflicts: Int)

class CalendarRepository(private val context: Context) {
    private val resolver = context.contentResolver
    fun calendars(): List<CalendarChoice> {
        val result = mutableListOf<CalendarChoice>()
        resolver.query(CalendarContract.Calendars.CONTENT_URI, arrayOf(CalendarContract.Calendars._ID, CalendarContract.Calendars.CALENDAR_DISPLAY_NAME), "${CalendarContract.Calendars.CALENDAR_ACCESS_LEVEL}>=? AND ${CalendarContract.Calendars.VISIBLE}=1", arrayOf(CalendarContract.Calendars.CAL_ACCESS_CONTRIBUTOR.toString()), null)?.use { cursor ->
            while (cursor.moveToNext()) result.add(CalendarChoice(cursor.getLong(0), cursor.getString(1) ?: "캘린더"))
        }
        return result
    }
    fun preferredId(): Long = context.getSharedPreferences("calendar", Context.MODE_PRIVATE).getLong("id", -1)
    fun prefer(id: Long) { context.getSharedPreferences("calendar", Context.MODE_PRIVATE).edit().putLong("id", id).apply() }

    fun bounds(d: ScheduleDraft): Pair<Long, Long> {
        require(d.startDate.isNotBlank()) { "시작 날짜를 확인해 주세요" }
        val date = LocalDate.parse(d.startDate)
        val endDate = d.endDate.ifBlank { d.startDate }.let(LocalDate::parse)
        val zone = ZoneId.of(d.timeZone.ifBlank { ZoneId.systemDefault().id })
        val start: Long
        val end: Long
        if (d.allDay) {
            start = date.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()
            end = endDate.plusDays(1).atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()
        } else {
            require(d.startTime.isNotBlank()) { "시작 시간을 확인하거나 종일 일정을 선택해 주세요" }
            start = date.atTime(LocalTime.parse(d.startTime)).atZone(zone).toInstant().toEpochMilli()
            end = if (d.endTime.isBlank()) start + 3_600_000 else endDate.atTime(LocalTime.parse(d.endTime)).atZone(zone).toInstant().toEpochMilli()
        }
        require(end > start) { "종료 시각은 시작 시각보다 늦어야 합니다" }
        return start to end
    }

    fun existing(calendarId: Long, d: ScheduleDraft): Pair<Boolean, Boolean> {
        val (start, end) = bounds(d)
        var duplicate = false
        var conflict = false
        val uri = CalendarContract.Instances.CONTENT_URI.buildUpon().also { ContentUris.appendId(it, start); ContentUris.appendId(it, end) }.build()
        resolver.query(uri, arrayOf(CalendarContract.Instances.TITLE, CalendarContract.Instances.BEGIN, CalendarContract.Instances.END), "${CalendarContract.Instances.CALENDAR_ID}=?", arrayOf(calendarId.toString()), null)?.use { c ->
            while (c.moveToNext()) {
                if (c.getString(0) == d.title && c.getLong(1) == start && c.getLong(2) == end) duplicate = true
                else if (c.getLong(1) < end && c.getLong(2) > start) conflict = true
            }
        }
        return duplicate to conflict
    }

    fun save(calendarId: Long, drafts: List<ScheduleDraft>): SaveReport {
        val ids = mutableListOf<Long>()
        var skipped = 0
        var conflicts = 0
        for (d in drafts) {
            val (duplicate, conflict) = existing(calendarId, d)
            if (duplicate) { skipped++; continue }
            if (conflict) conflicts++
            val (start, end) = bounds(d)
            val values = ContentValues().apply {
                put(CalendarContract.Events.CALENDAR_ID, calendarId)
                put(CalendarContract.Events.TITLE, d.title)
                put(CalendarContract.Events.DESCRIPTION, d.description)
                put(CalendarContract.Events.EVENT_LOCATION, d.location)
                put(CalendarContract.Events.DTSTART, start)
                put(CalendarContract.Events.EVENT_TIMEZONE, if (d.allDay) "UTC" else d.timeZone)
                put(CalendarContract.Events.ALL_DAY, if (d.allDay) 1 else 0)
                if (d.recurrence.isBlank()) put(CalendarContract.Events.DTEND, end)
                else { put(CalendarContract.Events.RRULE, d.recurrence); put(CalendarContract.Events.DURATION, "P${Duration.ofMillis(end - start).seconds}S") }
            }
            val uri = resolver.insert(CalendarContract.Events.CONTENT_URI, values) ?: throw IllegalStateException("일정을 저장하지 못했습니다")
            val id = ContentUris.parseId(uri)
            ids.add(id)
            d.reminderMinutes?.let { minutes ->
                resolver.insert(CalendarContract.Reminders.CONTENT_URI, ContentValues().apply {
                    put(CalendarContract.Reminders.EVENT_ID, id)
                    put(CalendarContract.Reminders.MINUTES, minutes)
                    put(CalendarContract.Reminders.METHOD, CalendarContract.Reminders.METHOD_ALERT)
                })
            }
        }
        return SaveReport(ids, skipped, conflicts)
    }
    fun undo(ids: List<Long>) { ids.forEach { resolver.delete(ContentUris.withAppendedId(CalendarContract.Events.CONTENT_URI, it), null, null) } }
    fun insertIntent(d: ScheduleDraft): Intent {
        val (start, end) = bounds(d)
        return Intent(Intent.ACTION_INSERT).setData(CalendarContract.Events.CONTENT_URI)
            .putExtra(CalendarContract.Events.TITLE, d.title)
            .putExtra(CalendarContract.Events.DESCRIPTION, d.description)
            .putExtra(CalendarContract.Events.EVENT_LOCATION, d.location)
            .putExtra(CalendarContract.EXTRA_EVENT_BEGIN_TIME, start)
            .putExtra(CalendarContract.EXTRA_EVENT_END_TIME, end)
            .putExtra(CalendarContract.EXTRA_EVENT_ALL_DAY, d.allDay)
    }
}
