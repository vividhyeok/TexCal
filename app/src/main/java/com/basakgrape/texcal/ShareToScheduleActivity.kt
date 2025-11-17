package com.basakgrape.texcal

import android.content.Intent
import android.os.Bundle
import android.provider.CalendarContract
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch

class ShareToScheduleActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val apiKey = ApiKeyStore.getKey(this)
        if (apiKey.isNullOrBlank()) {
            Toast.makeText(
                this,
                "TexCal 설정에서 OpenAI API Key를 먼저 등록하세요.",
                Toast.LENGTH_LONG
            ).show()
            finish()
            return
        }

        val sharedText = intent?.getStringExtra(Intent.EXTRA_TEXT)
        if (sharedText.isNullOrBlank()) {
            Toast.makeText(this, "텍스트를 받지 못했습니다.", Toast.LENGTH_SHORT).show()
            finish()
            return
        }

        lifecycleScope.launch {
            try {
                // 1. OpenAI로부터 스케줄 결과 받기
                val result = OpenAiClient.parseSchedule(apiKey, sharedText)

                // 🔥 1-1. 애매한 일정이면 캘린더 열지 않고 토스트만 표시
                if (result.confidence < 0.4) {
                    Toast.makeText(
                        this@ShareToScheduleActivity,
                        "내용이 너무 애매하여 일정으로 만들 수 없습니다.",
                        Toast.LENGTH_LONG
                    ).show()
                    // 여기서 바로 종료
                    finish()
                    return@launch
                }

                // 2. 결과를 이용해 캘린더 "새 일정 추가" 화면 열기
                openCalendarInsert(result)
            } catch (e: Exception) {
                e.printStackTrace()
                Toast.makeText(
                    this@ShareToScheduleActivity,
                    "OpenAI 오류: ${e.message}",
                    Toast.LENGTH_LONG
                ).show()
            } finally {
                // 캘린더 화면으로 넘어갔으니 이 액티비티는 정리
                // (위에서 이미 finish() 호출했다면 그냥 한 번 더 호출되는 셈이라 문제 없음)
                finish()
            }
        }
    }

    /** ScheduleResult를 이용해 캘린더 '일정 추가' 화면을 여는 함수 */
    private fun openCalendarInsert(result: ScheduleResult) {
        val intent = Intent(Intent.ACTION_INSERT).apply {
            data = CalendarContract.Events.CONTENT_URI
            putExtra(CalendarContract.Events.TITLE, result.title)
            putExtra(CalendarContract.Events.DESCRIPTION, result.description)

            result.location?.let { location ->
                putExtra(CalendarContract.Events.EVENT_LOCATION, location)
            }

            putExtra(CalendarContract.EXTRA_EVENT_BEGIN_TIME, result.startMillis)
            putExtra(CalendarContract.EXTRA_EVENT_END_TIME, result.endMillis)
            putExtra(CalendarContract.EXTRA_EVENT_ALL_DAY, result.allDay)
        }

        startActivity(intent)
    }
}
