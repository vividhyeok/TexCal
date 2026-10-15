package com.basakgrape.texcal

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.korean.KoreanTextRecognizerOptions
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext

data class TexCalState(val input: String = "", val drafts: List<ScheduleDraft> = emptyList(), val busy: Boolean = false, val message: String = "")

class TexCalViewModel(application: Application) : AndroidViewModel(application) {
    private val mutable = MutableStateFlow(TexCalState())
    val state = mutable.asStateFlow()
    private var job: Job? = null
    fun input(text: String) { mutable.value = mutable.value.copy(input = text) }
    fun update(index: Int, draft: ScheduleDraft) { mutable.value = mutable.value.copy(drafts = mutable.value.drafts.toMutableList().also { it[index] = draft }) }
    fun message(text: String) { mutable.value = mutable.value.copy(message = text) }
    fun analyze() {
        job?.cancel()
        val raw = mutable.value.input.trim()
        if (raw.isBlank()) { message("텍스트를 입력해 주세요"); return }
        val context = getApplication<Application>()
        val provider = ApiKeyStore.provider(context)
        val key = ApiKeyStore.key(context, provider)
        if (key.isBlank()) { message("설정에서 ${provider.label} API 키를 입력해 주세요"); return }
        job = viewModelScope.launch {
            mutable.value = mutable.value.copy(busy = true, drafts = emptyList(), message = "일정을 분석하고 있습니다")
            try {
                val drafts = LlmClient.extract(provider, key, ApiKeyStore.model(context, provider), raw)
                mutable.value = mutable.value.copy(drafts = drafts, message = if (drafts.isEmpty()) "일정을 찾지 못했습니다" else "${drafts.size}개 일정을 검토해 주세요")
            } catch (_: CancellationException) { throw CancellationException() }
            catch (e: LlmFailure) { message(e.kind) }
            catch (_: Exception) { message("분석 중 오류가 발생했습니다") }
            finally { mutable.value = mutable.value.copy(busy = false) }
        }
    }
    fun sharedImage(uri: Uri) {
        job?.cancel()
        job = viewModelScope.launch {
            mutable.value = mutable.value.copy(busy = true, message = "이미지에서 글자를 읽고 있습니다")
            try {
                val context = getApplication<Application>()
                val image = withContext(Dispatchers.IO) { InputImage.fromFilePath(context, uri) }
                val recognizer = TextRecognition.getClient(KoreanTextRecognizerOptions.Builder().build())
                val text = try { recognizer.process(image).await().text } finally { recognizer.close() }
                if (text.isBlank()) { message("이미지에서 글자를 찾지 못했습니다"); return@launch }
                input(text)
                analyze()
            } catch (_: CancellationException) { throw CancellationException() }
            catch (_: Exception) { message("이미지를 읽지 못했습니다. 공유 권한을 확인해 주세요") }
            finally { mutable.value = mutable.value.copy(busy = false) }
        }
    }
}
