package com.basakgrape.texcal

data class ScheduleResult(
    val title: String,
    val description: String,
    val location: String?,
    val startMillis: Long,
    val endMillis: Long,
    val allDay: Boolean,
    val confidence: Double  // ★ 추가
)
