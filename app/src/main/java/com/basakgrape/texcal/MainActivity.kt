package com.basakgrape.texcal

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.basakgrape.texcal.ui.theme.TexCalTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

open class MainActivity : ComponentActivity() {
    private val vm by viewModels<TexCalViewModel>()
    private val calendar by lazy { CalendarRepository(this) }
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        receive(intent)
        setContent { TexCalTheme { Screen() } }
    }
    override fun onNewIntent(intent: Intent) { super.onNewIntent(intent); setIntent(intent); receive(intent) }
    private fun receive(intent: Intent?) {
        when (intent?.action) {
            Intent.ACTION_SEND -> if (intent.type?.startsWith("image/") == true) {
                @Suppress("DEPRECATION") val uri = intent.getParcelableExtra<android.net.Uri>(Intent.EXTRA_STREAM)
                if (uri != null) vm.sharedImage(uri)
            } else intent.getCharSequenceExtra(Intent.EXTRA_TEXT)?.toString()?.let { vm.input(it); vm.analyze() }
            Intent.ACTION_PROCESS_TEXT -> intent.getCharSequenceExtra(Intent.EXTRA_PROCESS_TEXT)?.toString()?.let { vm.input(it); vm.analyze() }
        }
    }
    @OptIn(ExperimentalMaterial3Api::class)
    @Composable private fun Screen() {
        val state by vm.state.collectAsStateWithLifecycle()
        val scope = rememberCoroutineScope()
        var settings by remember { mutableStateOf(false) }
        var choices by remember { mutableStateOf(emptyList<CalendarChoice>()) }
        var selectedCalendar by remember { mutableLongStateOf(calendar.preferredId()) }
        var lastIds by remember { mutableStateOf(emptyList<Long>()) }
        var pendingConflicts by remember { mutableStateOf<List<ScheduleDraft>?>(null) }
        val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { result ->
            if (result.values.all { it }) scope.launch { choices = withContext(Dispatchers.IO) { calendar.calendars() }; if (selectedCalendar < 0) selectedCalendar = choices.firstOrNull()?.id ?: -1 }
            else vm.message("캘린더 권한이 없어 캘린더 앱에서 개별 등록할 수 있습니다")
        }
        fun hasPermission() = listOf(Manifest.permission.READ_CALENDAR, Manifest.permission.WRITE_CALENDAR).all { ContextCompat.checkSelfPermission(this, it) == PackageManager.PERMISSION_GRANTED }
        LaunchedEffect(state.drafts, settings) {
            if (!settings && state.drafts.isNotEmpty() && hasPermission()) { choices = withContext(Dispatchers.IO) { calendar.calendars() }; if (selectedCalendar < 0) selectedCalendar = choices.firstOrNull()?.id ?: -1 }
        }
        fun commit(drafts: List<ScheduleDraft>) {
            scope.launch { try {
                val report = withContext(Dispatchers.IO) { calendar.save(selectedCalendar, drafts) }
                lastIds = report.ids
                vm.message("${report.ids.size}개 저장, 중복 ${report.skipped}개 건너뜀, 충돌 ${report.conflicts}개")
            } catch (e: Exception) { vm.message(e.message ?: "저장하지 못했습니다") } }
        }
        pendingConflicts?.let { drafts -> AlertDialog(onDismissRequest = { pendingConflicts = null }, title = { Text("기존 일정과 시간이 겹칩니다") }, text = { Text("${drafts.size}개 일정 중 기존 일정과 겹치는 항목이 있습니다. 그래도 등록할까요?") }, confirmButton = { TextButton(onClick = { pendingConflicts = null; commit(drafts) }) { Text("등록") } }, dismissButton = { TextButton(onClick = { pendingConflicts = null }) { Text("취소") } }) }
        Scaffold(topBar = { TopAppBar(title = { Text("TexCal") }, actions = { TextButton(onClick = { settings = !settings }) { Text(if (settings) "닫기" else "설정") } }) }) { padding ->
            Column(Modifier.fillMaxSize().padding(padding).padding(16.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                if (settings) {
                    SettingsView()
                } else {
                    OutlinedTextField(value = state.input, onValueChange = vm::input, label = { Text("공지 또는 일정 텍스트") }, modifier = Modifier.fillMaxWidth(), minLines = 4)
                    Button(onClick = vm::analyze, enabled = !state.busy && state.input.isNotBlank()) { Text("일정 분석") }
                    if (state.busy) LinearProgressIndicator(Modifier.fillMaxWidth())
                    if (state.message.isNotBlank()) Text(state.message)
                    if (state.drafts.isNotEmpty()) {
                        Text("검토할 일정", style = MaterialTheme.typography.titleLarge)
                        state.drafts.forEachIndexed { i, draft -> DraftEditor(draft) { vm.update(i, it) } }
                        if (choices.isNotEmpty()) {
                            var expanded by remember { mutableStateOf(false) }
                            Box { OutlinedButton(onClick = { expanded = true }) { Text("저장할 캘린더: ${choices.find { it.id == selectedCalendar }?.name ?: "선택"}") }
                                DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) { choices.forEach { choice -> DropdownMenuItem(text = { Text(choice.name) }, onClick = { selectedCalendar = choice.id; calendar.prefer(choice.id); expanded = false }) } }
                            }
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Button(onClick = {
                                if (!hasPermission()) { permission.launch(arrayOf(Manifest.permission.READ_CALENDAR, Manifest.permission.WRITE_CALENDAR)); return@Button }
                                val drafts = state.drafts.filter { it.selected }
                                if (drafts.isEmpty() || selectedCalendar < 0) { vm.message("일정과 캘린더를 선택해 주세요"); return@Button }
                                scope.launch { try {
                                    val conflict = withContext(Dispatchers.IO) { drafts.any { calendar.existing(selectedCalendar, it).second } }
                                    if (conflict) pendingConflicts = drafts else commit(drafts)
                                } catch (e: Exception) { vm.message(e.message ?: "일정 검증에 실패했습니다") } }
                            }) { Text("선택 일정 등록") }
                            OutlinedButton(onClick = { state.drafts.firstOrNull { it.selected }?.let { try { startActivity(calendar.insertIntent(it)) } catch (e: Exception) { vm.message(e.message ?: "캘린더 앱을 열 수 없습니다") } } }) { Text("캘린더 앱") }
                        }
                        if (lastIds.isNotEmpty()) TextButton(onClick = { scope.launch { withContext(Dispatchers.IO) { calendar.undo(lastIds) }; lastIds = emptyList(); vm.message("방금 저장한 일정을 삭제했습니다") } }) { Text("저장 실행 취소") }
                        TextButton(onClick = { if (intent.action != Intent.ACTION_MAIN) finish() else vm.message("입력 내용을 수정할 수 있습니다") }) { Text("취소") }
                    }
                }
            }
        }
    }
    @Composable private fun SettingsView() {
        var provider by remember { mutableStateOf(ApiKeyStore.provider(this)) }
        var key by remember(provider) { mutableStateOf(ApiKeyStore.key(this, provider)) }
        var model by remember(provider) { mutableStateOf(ApiKeyStore.model(this, provider)) }
        var saved by remember { mutableStateOf(false) }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) { Provider.entries.forEach { p -> FilterChip(selected = provider == p, onClick = { provider = p; ApiKeyStore.saveProvider(this@MainActivity, p) }, label = { Text(p.label) }) } }
        OutlinedTextField(key, { key = it }, label = { Text("${provider.label} API 키") }, modifier = Modifier.fillMaxWidth(), visualTransformation = androidx.compose.ui.text.input.PasswordVisualTransformation())
        OutlinedTextField(model, { model = it }, label = { Text("모델 ID") }, modifier = Modifier.fillMaxWidth())
        Button(onClick = { ApiKeyStore.saveKey(this, provider, key); ApiKeyStore.saveModel(this, provider, model); saved = true }) { Text("설정 저장") }
        if (saved) Text("저장되었습니다")
    }
}
