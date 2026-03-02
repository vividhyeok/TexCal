package com.basakgrape.texcal

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.CalendarContract
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.basakgrape.texcal.ui.theme.TexCalTheme
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale

enum class MainScreen {
    HOME,
    SETTINGS,
    REVIEW
}

private data class ReviewDraft(
    val title: String,
    val description: String,
    val location: String,
    val startDate: String,
    val endDate: String,
    val startTime: String,
    val endTime: String,
    val allDay: Boolean,
    val confidence: Double,
    val promptVersion: String,
    val retriedForSchema: Boolean
)

class MainActivity : ComponentActivity() {

    companion object {
        private const val CONFIDENCE_THRESHOLD = 0.4
        private val DATE_FORMATTER: DateTimeFormatter = DateTimeFormatter.ISO_LOCAL_DATE
        private val TIME_FORMATTER: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm")
    }

    private val sharedTextEvent = mutableStateOf<String?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        sharedTextEvent.value = extractSharedText(intent)

        setContent {
            TexCalTheme {
                MainContent()
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        sharedTextEvent.value = extractSharedText(intent)
    }

    private fun extractSharedText(intent: Intent?): String? {
        if (intent?.action != Intent.ACTION_SEND) return null
        return intent.getStringExtra(Intent.EXTRA_TEXT)?.trim().orEmpty().ifBlank { null }
    }

    private fun openCalendarInsert(result: ScheduleResult) {
        val intent = Intent(Intent.ACTION_INSERT).apply {
            data = CalendarContract.Events.CONTENT_URI
            putExtra(CalendarContract.Events.TITLE, result.title)
            putExtra(CalendarContract.Events.DESCRIPTION, result.description)
            result.location?.let { putExtra(CalendarContract.Events.EVENT_LOCATION, it) }
            putExtra(CalendarContract.EXTRA_EVENT_BEGIN_TIME, result.startMillis)
            putExtra(CalendarContract.EXTRA_EVENT_END_TIME, result.endMillis)
            putExtra(CalendarContract.EXTRA_EVENT_ALL_DAY, result.allDay)
        }
        startActivity(intent)
    }

    private fun buildReviewDraft(parseOutput: ScheduleParseOutput): ReviewDraft {
        val result = parseOutput.result
        val zone = ZoneId.systemDefault()
        val startZdt = Instant.ofEpochMilli(result.startMillis).atZone(zone)
        val endZdt = Instant.ofEpochMilli(result.endMillis).atZone(zone)

        val endDateForDisplay =
            if (result.allDay) {
                val candidate = endZdt.toLocalDate().minusDays(1)
                if (candidate.isBefore(startZdt.toLocalDate())) startZdt.toLocalDate() else candidate
            } else {
                endZdt.toLocalDate()
            }

        return ReviewDraft(
            title = result.title,
            description = result.description,
            location = result.location.orEmpty(),
            startDate = startZdt.toLocalDate().format(DATE_FORMATTER),
            endDate = endDateForDisplay.format(DATE_FORMATTER),
            startTime = if (result.allDay) "" else startZdt.toLocalTime().format(TIME_FORMATTER),
            endTime = if (result.allDay) "" else endZdt.toLocalTime().format(TIME_FORMATTER),
            allDay = result.allDay,
            confidence = result.confidence,
            promptVersion = parseOutput.promptVersion,
            retriedForSchema = parseOutput.retriedForSchema
        )
    }

    private fun buildScheduleFromDraft(draft: ReviewDraft): ScheduleResult {
        val zone = ZoneId.systemDefault()

        val startDate = try {
            LocalDate.parse(draft.startDate)
        } catch (_: Exception) {
            throw IllegalArgumentException("시작 날짜 형식이 올바르지 않습니다. (YYYY-MM-DD)")
        }

        val endDate = try {
            LocalDate.parse(draft.endDate)
        } catch (_: Exception) {
            throw IllegalArgumentException("종료 날짜 형식이 올바르지 않습니다. (YYYY-MM-DD)")
        }

        val (startMillis, endMillis) = if (draft.allDay) {
            val startZdt = startDate.atStartOfDay(zone)
            val endZdt = endDate.plusDays(1).atStartOfDay(zone)
            startZdt.toInstant().toEpochMilli() to endZdt.toInstant().toEpochMilli()
        } else {
            val startTime = try {
                LocalTime.parse(draft.startTime)
            } catch (_: Exception) {
                throw IllegalArgumentException("시작 시간 형식이 올바르지 않습니다. (HH:MM)")
            }

            val endTime =
                if (draft.endTime.isBlank()) {
                    startTime.plusHours(1)
                } else {
                    try {
                        LocalTime.parse(draft.endTime)
                    } catch (_: Exception) {
                        throw IllegalArgumentException("종료 시간 형식이 올바르지 않습니다. (HH:MM)")
                    }
                }

            val startZdt = ZonedDateTime.of(startDate, startTime, zone)
            val endZdt = ZonedDateTime.of(endDate, endTime, zone)

            if (!endZdt.isAfter(startZdt)) {
                throw IllegalArgumentException("종료 시각은 시작 시각보다 늦어야 합니다.")
            }

            startZdt.toInstant().toEpochMilli() to endZdt.toInstant().toEpochMilli()
        }

        return ScheduleResult(
            title = draft.title.ifBlank { "제목 없음" },
            description = draft.description.ifBlank { "" },
            location = draft.location.ifBlank { null },
            startMillis = startMillis,
            endMillis = endMillis,
            allDay = draft.allDay,
            confidence = draft.confidence
        )
    }

    @OptIn(ExperimentalMaterial3Api::class)
    @androidx.compose.runtime.Composable
    private fun MainContent() {
        val context = LocalContext.current
        val scope = rememberCoroutineScope()
        val snackbarHostState = remember { SnackbarHostState() }

        var currentScreen by rememberSaveable { mutableStateOf(MainScreen.HOME) }
        var inputText by rememberSaveable { mutableStateOf("") }
        var apiKey by rememberSaveable { mutableStateOf(ApiKeyStore.getDeepSeekKey(context).orEmpty()) }
        var model by rememberSaveable { mutableStateOf(ApiKeyStore.getDeepSeekModel(context)) }
        var isParsing by rememberSaveable { mutableStateOf(false) }
        var reviewDraft by remember { mutableStateOf<ReviewDraft?>(null) }

        val incomingSharedText = sharedTextEvent.value
        LaunchedEffect(incomingSharedText) {
            if (!incomingSharedText.isNullOrBlank()) {
                inputText = incomingSharedText
                currentScreen = MainScreen.HOME
                snackbarHostState.showSnackbar("공유된 텍스트를 입력창에 불러왔습니다.")
                sharedTextEvent.value = null
            }
        }

        fun toUserMessage(error: Exception): String {
            return when (error) {
                is DeepSeekAuthException -> "API Key 인증에 실패했습니다. 설정에서 키를 다시 확인해 주세요."
                is DeepSeekRateLimitException -> "요청 한도를 초과했습니다. 잠시 후 다시 시도해 주세요."
                is DeepSeekServerException -> "DeepSeek 서버 오류입니다. 잠시 후 다시 시도해 주세요."
                is DeepSeekNetworkException -> "네트워크 오류입니다. 연결 상태를 확인해 주세요."
                is DeepSeekResponseFormatException -> "응답 형식이 불안정합니다. 다시 시도해 주세요."
                is DeepSeekApiException -> "API 요청 오류가 발생했습니다. 설정값을 확인해 주세요."
                else -> "일정 추출 중 오류가 발생했습니다: ${error.message}"
            }
        }

        fun parseSchedule() {
            if (isParsing) return

            if (inputText.isBlank()) {
                scope.launch {
                    snackbarHostState.showSnackbar("텍스트를 입력하거나 공유해 주세요.")
                }
                return
            }

            if (apiKey.isBlank()) {
                currentScreen = MainScreen.SETTINGS
                scope.launch {
                    snackbarHostState.showSnackbar("DeepSeek API Key를 먼저 저장해 주세요.")
                }
                return
            }

            val selectedModel = model.trim().ifBlank { ApiKeyStore.DEFAULT_DEEPSEEK_MODEL }
            isParsing = true

            scope.launch {
                try {
                    val parseOutput = DeepSeekClient.parseSchedule(
                        apiKey = apiKey.trim(),
                        model = selectedModel,
                        rawText = inputText
                    )

                    if (parseOutput.retriedForSchema) {
                        snackbarHostState.showSnackbar("응답 형식 오류를 자동 보정해 재시도 후 추출했습니다.")
                    }

                    if (parseOutput.result.confidence < CONFIDENCE_THRESHOLD) {
                        reviewDraft = buildReviewDraft(parseOutput)
                        currentScreen = MainScreen.REVIEW
                        snackbarHostState.showSnackbar("확신도가 낮아 검토 화면으로 이동했습니다.")
                        return@launch
                    }

                    openCalendarInsert(parseOutput.result)
                } catch (e: Exception) {
                    snackbarHostState.showSnackbar(toUserMessage(e))
                } finally {
                    isParsing = false
                }
            }
        }

        Scaffold(
            topBar = {
                TopAppBar(
                    title = { Text("TexCal") },
                    actions = {
                        TextButton(
                            onClick = {
                                currentScreen = if (currentScreen == MainScreen.HOME) {
                                    MainScreen.SETTINGS
                                } else {
                                    MainScreen.HOME
                                }
                            }
                        ) {
                            Text(if (currentScreen == MainScreen.HOME) "설정" else "홈")
                        }
                    }
                )
            },
            snackbarHost = { SnackbarHost(snackbarHostState) }
        ) { innerPadding ->
            when (currentScreen) {
                MainScreen.HOME -> {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(innerPadding)
                            .padding(16.dp)
                            .verticalScroll(rememberScrollState()),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Text(
                            text = "텍스트를 넣고 DeepSeek로 일정 추출",
                            style = MaterialTheme.typography.headlineSmall,
                            fontWeight = FontWeight.Bold
                        )

                        Text(
                            text = "공유 텍스트/직접 입력 모두 같은 추출 파이프라인을 사용합니다.",
                            style = MaterialTheme.typography.bodyMedium
                        )

                        OutlinedTextField(
                            value = inputText,
                            onValueChange = { inputText = it },
                            label = { Text("일정으로 변환할 텍스트") },
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(min = 200.dp),
                            minLines = 8
                        )

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Button(
                                onClick = { parseSchedule() },
                                enabled = !isParsing,
                                modifier = Modifier.weight(1f)
                            ) {
                                Text(if (isParsing) "처리 중..." else "일정 추출")
                            }

                            OutlinedButton(
                                onClick = { inputText = "" },
                                enabled = !isParsing,
                                modifier = Modifier.weight(1f)
                            ) {
                                Text("입력 지우기")
                            }
                        }

                        TextButton(onClick = { currentScreen = MainScreen.SETTINGS }) {
                            Text("DeepSeek 설정 열기")
                        }
                    }
                }

                MainScreen.SETTINGS -> {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(innerPadding)
                            .padding(16.dp)
                            .verticalScroll(rememberScrollState()),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Text(
                            text = "DeepSeek 설정",
                            style = MaterialTheme.typography.headlineSmall,
                            fontWeight = FontWeight.Bold
                        )

                        Text(
                            text = "기본 모델은 deepseek-chat입니다.",
                            style = MaterialTheme.typography.bodyMedium
                        )

                        OutlinedTextField(
                            value = apiKey,
                            onValueChange = { apiKey = it },
                            label = { Text("DeepSeek API Key") },
                            modifier = Modifier.fillMaxWidth()
                        )

                        OutlinedTextField(
                            value = model,
                            onValueChange = { model = it },
                            label = { Text("Model ID") },
                            supportingText = { Text("기본값: ${ApiKeyStore.DEFAULT_DEEPSEEK_MODEL}") },
                            modifier = Modifier.fillMaxWidth()
                        )

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Button(
                                onClick = {
                                    val normalizedModel = model.trim().ifBlank { ApiKeyStore.DEFAULT_DEEPSEEK_MODEL }
                                    ApiKeyStore.saveDeepSeekModel(context, normalizedModel)
                                    model = normalizedModel

                                    if (apiKey.isNotBlank()) {
                                        ApiKeyStore.saveDeepSeekKey(context, apiKey)
                                        scope.launch {
                                            snackbarHostState.showSnackbar("DeepSeek 설정이 저장되었습니다.")
                                        }
                                    } else {
                                        scope.launch {
                                            snackbarHostState.showSnackbar("API Key가 비어 있어 모델만 저장했습니다.")
                                        }
                                    }
                                },
                                modifier = Modifier.weight(1f)
                            ) {
                                Text("저장")
                            }

                            OutlinedButton(
                                onClick = {
                                    ApiKeyStore.clearDeepSeekKey(context)
                                    apiKey = ""
                                    scope.launch {
                                        snackbarHostState.showSnackbar("API Key를 삭제했습니다.")
                                    }
                                },
                                modifier = Modifier.weight(1f)
                            ) {
                                Text("키 삭제")
                            }
                        }

                        TextButton(
                            onClick = {
                                val docsUrl = "https://api-docs.deepseek.com/"
                                startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(docsUrl)))
                            }
                        ) {
                            Text("DeepSeek API 문서 열기")
                        }

                        TextButton(onClick = { currentScreen = MainScreen.HOME }) {
                            Text("홈으로")
                        }
                    }
                }

                MainScreen.REVIEW -> {
                    val draft = reviewDraft
                    if (draft == null) {
                        Column(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(innerPadding)
                                .padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Text("검토할 일정이 없습니다.")
                            TextButton(onClick = { currentScreen = MainScreen.HOME }) {
                                Text("홈으로")
                            }
                        }
                    } else {
                        Column(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(innerPadding)
                                .padding(16.dp)
                                .verticalScroll(rememberScrollState()),
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Text(
                                text = "일정 검토",
                                style = MaterialTheme.typography.headlineSmall,
                                fontWeight = FontWeight.Bold
                            )

                            Text(
                                text = "confidence ${"%.2f".format(Locale.US, draft.confidence)} / prompt ${draft.promptVersion}" +
                                    if (draft.retriedForSchema) " / schema-retry" else "",
                                style = MaterialTheme.typography.bodyMedium
                            )

                            OutlinedTextField(
                                value = draft.title,
                                onValueChange = { reviewDraft = draft.copy(title = it) },
                                label = { Text("제목") },
                                modifier = Modifier.fillMaxWidth()
                            )

                            OutlinedTextField(
                                value = draft.description,
                                onValueChange = { reviewDraft = draft.copy(description = it) },
                                label = { Text("설명") },
                                modifier = Modifier.fillMaxWidth(),
                                minLines = 3
                            )

                            OutlinedTextField(
                                value = draft.location,
                                onValueChange = { reviewDraft = draft.copy(location = it) },
                                label = { Text("장소") },
                                modifier = Modifier.fillMaxWidth()
                            )

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text("종일 일정")
                                Switch(
                                    checked = draft.allDay,
                                    onCheckedChange = { checked ->
                                        val nextStartTime =
                                            if (checked) "" else draft.startTime.ifBlank { "09:00" }
                                        val nextEndTime =
                                            if (checked) "" else draft.endTime.ifBlank { "10:00" }
                                        reviewDraft = draft.copy(
                                            allDay = checked,
                                            startTime = nextStartTime,
                                            endTime = nextEndTime
                                        )
                                    }
                                )
                            }

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                OutlinedTextField(
                                    value = draft.startDate,
                                    onValueChange = { reviewDraft = draft.copy(startDate = it) },
                                    label = { Text("시작 날짜") },
                                    supportingText = { Text("YYYY-MM-DD") },
                                    modifier = Modifier.weight(1f)
                                )

                                OutlinedTextField(
                                    value = draft.endDate,
                                    onValueChange = { reviewDraft = draft.copy(endDate = it) },
                                    label = { Text("종료 날짜") },
                                    supportingText = { Text("YYYY-MM-DD") },
                                    modifier = Modifier.weight(1f)
                                )
                            }

                            if (!draft.allDay) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    OutlinedTextField(
                                        value = draft.startTime,
                                        onValueChange = { reviewDraft = draft.copy(startTime = it) },
                                        label = { Text("시작 시간") },
                                        supportingText = { Text("HH:MM") },
                                        modifier = Modifier.weight(1f)
                                    )

                                    OutlinedTextField(
                                        value = draft.endTime,
                                        onValueChange = { reviewDraft = draft.copy(endTime = it) },
                                        label = { Text("종료 시간") },
                                        supportingText = { Text("HH:MM") },
                                        modifier = Modifier.weight(1f)
                                    )
                                }
                            }

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Button(
                                    onClick = {
                                        val currentDraft = reviewDraft ?: return@Button
                                        try {
                                            val schedule = buildScheduleFromDraft(currentDraft)
                                            openCalendarInsert(schedule)
                                        } catch (e: Exception) {
                                            scope.launch {
                                                snackbarHostState.showSnackbar(e.message ?: "검토 입력을 확인해 주세요.")
                                            }
                                        }
                                    },
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Text("캘린더 열기")
                                }

                                OutlinedButton(
                                    onClick = { currentScreen = MainScreen.HOME },
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Text("홈으로")
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
