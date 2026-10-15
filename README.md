# TexCal

개인용 Android 일정 추출 도구입니다. [최신 APK](https://github.com/vividhyeok/TexCal/releases/latest)를 다운로드해 설치합니다. 기존 디버그 APK는 서명 인증서가 달라 최초 전환 때 삭제 후 설치해야 합니다. 이후 릴리스 APK는 동일한 키로 서명되어 업데이트할 수 있습니다.

## 사용

1. 앱 설정에서 OpenAI API 키를 입력합니다. 기본 모델은 공식 문서에서 Structured Outputs를 지원하는 것으로 확인된 `gpt-4.1-mini`입니다. DeepSeek와 `deepseek-chat`도 선택할 수 있습니다. 모델 ID는 수정할 수 있습니다.
2. 앱에 텍스트를 붙여넣거나 다른 앱의 공유·텍스트 선택 메뉴에서 TexCal을 엽니다. 스크린샷이나 이미지를 공유하면 ML Kit 한국어 OCR을 거쳐 분석합니다.
3. 여러 일정 중 필요한 것만 선택하고 제목·장소·날짜·시간을 확인합니다. 미확정 정보는 반드시 검토합니다. 시간이 빠진 일정은 종일 일정으로 자동 확정하지 않습니다.
4. 캘린더 권한을 허용하고 저장할 캘린더를 고른 다음 등록합니다. 권한을 허용하지 않으면 캘린더 앱 버튼으로 개별 등록할 수 있습니다. 저장 직후 실행 취소가 가능합니다.

API 키는 Android Keystore 기반 암호화 설정에 저장하고 백업에서 제외합니다. 원문과 키를 로그에 기록하지 않습니다. 입력 텍스트는 선택한 API 공급자에게 전송됩니다.

## 빌드 및 배포

`main` 푸시나 Actions 수동 실행으로 단위 테스트, 서명된 Release APK 검증, GitHub Release 게시가 순서대로 실행됩니다. 버전은 `1.0.<Actions 실행 번호>`이며 APK 파일명도 같습니다. 실패한 빌드는 게시하지 않습니다.

GitHub Actions 저장소 Secrets 네 가지가 필요합니다: `ANDROID_KEYSTORE_BASE64`, `ANDROID_KEYSTORE_PASSWORD`, `ANDROID_KEY_ALIAS`, `ANDROID_KEY_PASSWORD`. 매 빌드에서 같은 키스토어를 사용해야 업데이트가 가능합니다. 키스토어와 암호를 저장소 밖의 암호화된 백업 장소에도 복사해 두세요. 키를 잃으면 동일한 앱 ID의 업데이트 APK를 더는 만들 수 없습니다.

로컬 검증: JDK 17, Android SDK 36에서 `./gradlew testDebugUnitTest assembleDebug`. 실제 OpenAI·DeepSeek 호출은 사용자가 앱에서 입력한 키로만 실행됩니다.
