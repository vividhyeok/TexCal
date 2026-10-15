package com.basakgrape.texcal

import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

class ScheduleExtractionTest {
    private fun event(evidence: String, start: String = "2026-12-31", end: String = "", startTime: String = "10:00", endTime: String = "", recurrence: String = ""): JSONObject = JSONObject()
        .put("title", "시험").put("description", "").put("location", "")
        .put("start_date", start).put("end_date", end).put("start_time", startTime).put("end_time", endTime)
        .put("all_day", false).put("time_zone", "Asia/Seoul").put("recurrence", recurrence)
        .put("reminder_minutes", JSONObject.NULL).put("evidence", evidence).put("uncertain", JSONArray())
    private fun parse(text: String, vararg events: JSONObject) = ScheduleExtractionNormalizer.parse(JSONObject().put("events", JSONArray().apply { events.forEach { put(it) } }), text)
    @Test fun singleAndMultiple() {
        assertEquals(1, parse("12월 31일 시험", event("시험")).size)
        assertEquals(2, parse("시험과 과제", event("시험"), event("과제")).size)
    }
    @Test fun noDateRemainsUnselected() {
        val draft = parse("시험 예정", event("시험", start = "", startTime = ""))[0]
        assertFalse(draft.selected)
        assertTrue(draft.uncertain.contains("시작 날짜"))
        assertTrue(draft.uncertain.contains("시작 시간"))
    }
    @Test fun nonEventAndUngroundedEvidence() {
        assertTrue(parse("안녕하세요").isEmpty())
        assertThrows(IllegalArgumentException::class.java) { parse("안녕하세요", event("시험")) }
    }
    @Test fun yearBoundaryAndRecurrence() {
        assertEquals("2027-01-01", parse("12월31일부터 1월1일까지 시험", event("시험", end = "2027-01-01"))[0].endDate)
        assertEquals("FREQ=WEEKLY", parse("매주 시험", event("시험", recurrence = "FREQ=WEEKLY"))[0].recurrence)
    }
    @Test fun invalidTimeAndSchema() {
        assertThrows(IllegalArgumentException::class.java) { parse("시험", event("시험", end = "2026-12-31", endTime = "09:00")) }
        assertThrows(IllegalArgumentException::class.java) { ScheduleExtractionNormalizer.parse(JSONObject(), "시험") }
    }
    @Test fun ocrLineBreakEvidence() {
        assertEquals(1, parse("공지\n시험 12월 31일", event("시험 12월 31일" )).size)
    }
    @Test fun universityNoticeAndRelativeDay() {
        val notice = "학사 공지: 내일 오후 2시 강의실에서 시험, 모레 과제 마감"
        assertEquals(2, parse(notice, event("내일 오후 2시", start = "2026-10-16", startTime = "14:00"), event("모레 과제 마감", start = "2026-10-17", startTime = "")).size)
    }
    @Test fun missingEndIsFlaggedNotInvented() {
        val draft = parse("시험 10시", event("시험 10시"))[0]
        assertEquals("", draft.endTime)
        assertTrue(draft.uncertain.any { it.contains("종료 시간") })
    }
    @Test fun malformedFieldsRejected() {
        val bad = event("시험").put("start_date", "2026-13-99")
        assertThrows(Exception::class.java) { parse("시험", bad) }
        assertThrows(IllegalArgumentException::class.java) { parse("시험", event("시험", recurrence = "EVERY=MONDAY")) }
    }
}
