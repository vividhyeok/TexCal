package com.basakgrape.texcal

import android.app.DatePickerDialog
import android.app.TimePickerDialog
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import java.time.LocalDate
import java.time.LocalTime

@Composable fun DraftEditor(d: ScheduleDraft, change: (ScheduleDraft) -> Unit) {
    val context = LocalContext.current
    var expanded by remember { mutableStateOf(false) }
    Card(Modifier.fillMaxWidth()) { Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row { Checkbox(d.selected, { change(d.copy(selected = it)) }); Text(d.title, modifier = Modifier.padding(top = 12.dp)) }
        OutlinedTextField(d.title, { change(d.copy(title = it)) }, label = { Text("제목") }, modifier = Modifier.fillMaxWidth())
        OutlinedTextField(d.location, { change(d.copy(location = it)) }, label = { Text("장소") }, modifier = Modifier.fillMaxWidth())
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = { val date = runCatching { LocalDate.parse(d.startDate) }.getOrDefault(LocalDate.now()); DatePickerDialog(context, { _, y, m, day -> change(d.copy(startDate = LocalDate.of(y, m + 1, day).toString())) }, date.year, date.monthValue - 1, date.dayOfMonth).show() }) { Text("시작: ${d.startDate.ifBlank { "날짜 선택" }}") }
            OutlinedButton(onClick = { val date = runCatching { LocalDate.parse(d.endDate.ifBlank { d.startDate }) }.getOrDefault(LocalDate.now()); DatePickerDialog(context, { _, y, m, day -> change(d.copy(endDate = LocalDate.of(y, m + 1, day).toString())) }, date.year, date.monthValue - 1, date.dayOfMonth).show() }) { Text("종료: ${d.endDate.ifBlank { "같은 날" }}") }
        }
        Row { Checkbox(d.allDay, { change(d.copy(allDay = it, startTime = if (it) "" else d.startTime, endTime = if (it) "" else d.endTime)) }); Text("종일 일정", Modifier.padding(top = 12.dp)) }
        if (!d.allDay) Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = { val time = runCatching { LocalTime.parse(d.startTime) }.getOrDefault(LocalTime.of(9, 0)); TimePickerDialog(context, { _, h, m -> change(d.copy(startTime = "%02d:%02d".format(h, m))) }, time.hour, time.minute, true).show() }) { Text("시작 ${d.startTime.ifBlank { "선택" }}") }
            OutlinedButton(onClick = { val time = runCatching { LocalTime.parse(d.endTime) }.getOrDefault(LocalTime.of(10, 0)); TimePickerDialog(context, { _, h, m -> change(d.copy(endTime = "%02d:%02d".format(h, m))) }, time.hour, time.minute, true).show() }) { Text("종료 ${d.endTime.ifBlank { "1시간 제안" }}") }
        }
        if (d.uncertain.isNotEmpty()) Text("확인 필요: ${d.uncertain.joinToString()}", color = MaterialTheme.colorScheme.error)
        Text("원문: ${d.evidence}")
        TextButton(onClick = { expanded = !expanded }) { Text(if (expanded) "상세 접기" else "설명·반복·알림") }
        if (expanded) {
            OutlinedTextField(d.description, { change(d.copy(description = it)) }, label = { Text("설명") }, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(d.recurrence, { change(d.copy(recurrence = it)) }, label = { Text("반복 규칙 (예: FREQ=WEEKLY)") }, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(d.reminderMinutes?.toString().orEmpty(), { change(d.copy(reminderMinutes = it.toIntOrNull())) }, label = { Text("몇 분 전 알림") }, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(d.timeZone, { change(d.copy(timeZone = it)) }, label = { Text("시간대") }, modifier = Modifier.fillMaxWidth())
        }
    } }
}
