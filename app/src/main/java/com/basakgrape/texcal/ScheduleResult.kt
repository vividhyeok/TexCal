package com.basakgrape.texcal

data class ScheduleDraft(
    val title: String, val description: String, val location: String,
    val startDate: String, val endDate: String, val startTime: String, val endTime: String,
    val allDay: Boolean, val timeZone: String, val recurrence: String,
    val reminderMinutes: Int?, val evidence: String, val uncertain: List<String>,
    val selected: Boolean = true
)
