package com.basakgrape.texcal

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.basakgrape.texcal.ui.theme.TexCalTheme
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            TexCalTheme {
                val context = LocalContext.current
                val scope = rememberCoroutineScope()
                val snackbarHostState = remember { SnackbarHostState() }

                var apiKey by remember {
                    mutableStateOf(ApiKeyStore.getKey(context) ?: "")
                }

                Scaffold(
                    snackbarHost = { SnackbarHost(snackbarHostState) }
                ) { innerPadding ->
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(innerPadding)
                            .padding(16.dp)
                    ) {
                        // 앱 제목
                        Text(
                            text = "TexCal 설정",
                            style = MaterialTheme.typography.headlineSmall,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(bottom = 8.dp)
                        )

                        // 🔹 간단 도움말
                        Text(
                            text = "1) 아래 버튼으로 OpenAI 키를 발급합니다.\n" +
                                    "2) 발급된 키를 복사해서 아래 입력칸에 붙여넣고 저장하세요.\n" +
                                    "3) 이후엔 카톡·브라우저 등에서 텍스트 공유 시 TexCal을 선택하면 됩니다.",
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.padding(bottom = 16.dp)
                        )

                        // 🔹 키 발급 페이지 열기 버튼
                        Button(
                            onClick = {
                                val url =
                                    "https://platform.openai.com/settings/organization/api-keys"
                                val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
                                context.startActivity(intent)
                            },
                            modifier = Modifier.padding(bottom = 16.dp)
                        ) {
                            Text("OpenAI API Key 발급 페이지 열기")
                        }

                        // 🔹 API Key 입력 필드
                        OutlinedTextField(
                            value = apiKey,
                            onValueChange = { apiKey = it },
                            label = { Text("OpenAI API Key (sk-...)") },
                            modifier = Modifier.padding(bottom = 16.dp)
                        )

                        // 🔹 저장 버튼
                        Button(
                            onClick = {
                                if (apiKey.isBlank()) {
                                    Toast.makeText(
                                        context,
                                        "키를 입력하세요.",
                                        Toast.LENGTH_SHORT
                                    ).show()
                                } else {
                                    ApiKeyStore.saveKey(context, apiKey)
                                    Toast.makeText(
                                        context,
                                        "API Key가 저장되었습니다.",
                                        Toast.LENGTH_SHORT
                                    ).show()
                                }
                            },
                            modifier = Modifier.padding(bottom = 8.dp)
                        ) {
                            Text("API Key 저장")
                        }

                        // 🔹 키 삭제 버튼
                        Button(
                            onClick = {
                                if (ApiKeyStore.hasKey(context)) {
                                    ApiKeyStore.clearKey(context)
                                    apiKey = ""
                                    Toast.makeText(
                                        context,
                                        "API Key가 삭제되었습니다.",
                                        Toast.LENGTH_SHORT
                                    ).show()
                                } else {
                                    Toast.makeText(
                                        context,
                                        "삭제할 키가 없습니다.",
                                        Toast.LENGTH_SHORT
                                    ).show()
                                }
                            }
                        ) {
                            Text("API Key 삭제")
                        }
                    }
                }

                // 처음 실행 시 안내 스낵바 (키 없을 때만)
                LaunchedEffect(Unit) {
                    if (!ApiKeyStore.hasKey(context)) {
                        scope.launch {
                            snackbarHostState.showSnackbar("먼저 OpenAI API Key를 설정하세요.")
                        }
                    }
                }
            }
        }
    }
}
