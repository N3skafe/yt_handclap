# Sena Cue

## Summary

### English

Sena Cue controls a video in the installed YouTube app, including Shorts when that session advertises next and previous. Say **HI SENA** in English, then hold an open palm to pause, a thumbs-up to play, a thumb down for the next video, or a closed fist for the previous video. Speech and hand recognition stay on the phone. The screen language can be English, Korean, Simplified Chinese, or Japanese, and the appearance can follow the system or be light or dark. The longer guide below is written separately in English and Korean.

### 한국어

Sena Cue는 폰에 설치된 유튜브 앱의 영상을 제어합니다. 세션이 다음·이전을 알려 주면 Shorts도 같은 손 모양으로 제어합니다. 영어로 **HI SENA**라고 말한 뒤, 손바닥은 일시정지, 엄지 올림은 재생, 엄지 내림은 다음, 주먹은 이전입니다. 음성과 손 인식은 폰 안에서 이루어집니다. 화면 언어는 영어, 한국어, 중국어 간체, 일본어 중에서 고를 수 있고, 화면 모드는 시스템을 따르거나 라이트 또는 다크로 둘 수 있습니다. 아래의 긴 안내는 영어와 한국어로 나누어 적었습니다.

### 中文

Sena Cue 可以控制已安装的 YouTube 应用中的视频。会话提供下一个和上一个时，Shorts 也用同样的手势控制。请用英语说 **HI SENA**，然后张开手掌暂停，竖起大拇指播放，拇指向下切换到下一个，握拳切换到上一个。语音和手势识别都在手机上完成。界面语言可以是英语、韩语、简体中文或日语，外观可以跟随系统，也可以设为浅色或深色。下面的详细说明分为英语和韩语。

### 日本語

Sena Cue は、インストール済みの YouTube アプリの動画を制御します。セッションが次へ・前へを示すときは、Shorts も同じ手の形で制御します。英語で **HI SENA** と言ってから、開いた手のひらで一時停止、親指を上げて再生、親指を下げて次へ、拳で前へです。音声と手の認識はこの端末の中で行います。画面の言語は英語、韓国語、簡体字中国語、日本語から選べます。画面モードはシステムに従うか、ライトまたはダークにできます。詳しい説明は下に英語と韓国語で分けてあります。

## English

### What this app is

Sena Cue listens for the English wake word **HI SENA**, then opens the front camera for a few seconds. An open palm sends pause, a thumbs-up sends play, a thumb down sends next, and a closed fist sends previous. Speech and hand recognition stay on the phone. There is no account and no API key.

The screen language can be English, Korean, Simplified Chinese, or Japanese. Appearance can follow the system, or be light or dark. The button names in this English guide match the English screen.

The app sends those commands only to the YouTube app package `com.google.android.youtube`. A regular video and a Short use the same gestures and the same session commands. Shorts are controlled when that session advertises skip next and skip previous. Each wake accepts one command.

Audio is used only to decide whether **HI SENA** was said. Camera frames are used only to classify the hand. Neither is saved to a file or uploaded. The notification listener is used to find YouTube's media session.

### How to use it

You need Android 8.0 or later, a front camera, a microphone, and the YouTube app. A debug APK is built at `app/build/outputs/apk/debug/app-debug.apk`. The build command is in [Build](#build).

1. Install and open **Sena Cue**.
2. At the top of the screen, choose the language and the appearance.
3. Tap **Request permissions**. Allow the camera, the microphone, and, on Android 13 or later, notifications.
4. Tap **Notification access**. Turn this app on, then come back. Android shows other apps' media sessions only to apps with this access. Sena Cue uses that list to find YouTube and request pause or play.
5. Optional: tap **Display over other apps** and allow this app. When **HI SENA** is heard, a blue banner appears briefly over YouTube and the phone vibrates. The banner does not take touches. If you skip this, control still starts and the wake word is reported in the status notification.
6. Open YouTube and start a regular video.
7. Return to Sena Cue while its screen is visible and tap **Start control**. Control can start only from that visible screen. It does not start on boot.
8. Switch back to YouTube. A status notification stays up. While the app is waiting for **HI SENA**, the camera stays closed.
9. Say **HI SENA** in English, then pause for a moment. Speak closer to the phone if the status says the microphone is quiet.
10. Within about 10 seconds, hold one hand toward the front camera:
    - Open palm, fingers spread: pause.
    - Thumbs-up: play.
    - Thumb down: next video.
    - Closed fist: previous video.
    Hold the same shape for about a quarter of a second. The camera closes, the command goes to YouTube, and the app waits for **HI SENA** again.
11. Tap **Stop control** in the app or in the notification when you are done. If notifications are off, open the app and use the button there.

If 10 seconds pass with no confirmed gesture, no command is sent and the app listens for **HI SENA** again. If YouTube has no active session, the status says so. Play a regular video and try again.

### What it does not do

Sena Cue does not play, embed, or download video. It does not control YouTube Music or other audio apps. It does not seek, change the volume, or close YouTube. It does not like, comment, share, or subscribe. Ads and live streams are outside this control. One wake sends at most one command.

### Privacy

Microphone audio is used only to judge the wake word. Camera frames are used only to classify the hand. Neither is saved or uploaded. The notification listener looks up the media session and does not collect notification text. App data is excluded from cloud backup and device-to-device transfer.

### License

The source code of this repository is released under the [MIT License](LICENSE). Copyright (c) 2026 neskafe3112.

Bundled models and third-party libraries stay under their own upstream licenses. That includes the sherpa-onnx keyword model, the MediaPipe gesture model, and the Android, ONNX Runtime, sherpa-onnx, and MediaPipe dependencies. This MIT grant covers this app's own source code.

### Build

Requirements:

- JDK 17 or later.
- Android SDK, with `sdk.dir` in `local.properties`.
- Compile SDK 37.2, target SDK 37, minimum SDK 26.

```
.\gradlew.bat :app:testDebugUnitTest :app:lintDebug :app:assembleDebug
```

The debug APK is `app/build/outputs/apk/debug/app-debug.apk`.

## 한국어

### 이 앱은 무엇인가

Sena Cue는 영어 호출어 **HI SENA**를 들은 뒤 전면 카메라를 몇 초 열고, 손 모양으로 유튜브에 명령을 보냅니다. 손바닥은 일시정지, 엄지 올림은 재생, 엄지 내림은 다음, 주먹은 이전입니다. 음성과 손 인식은 폰 안에서 이루어지고, 계정이나 API 키는 없습니다.

화면 언어는 영어, 한국어, 중국어 간체, 일본어 중에서 고릅니다. 화면 모드는 시스템을 따르거나 라이트 또는 다크입니다. 이 한국어 안내의 버튼 이름은 한국어 화면과 같습니다.

명령은 유튜브 앱 패키지 `com.google.android.youtube`로만 갑니다. 일반 동영상과 Shorts는 같은 손 모양과 같은 세션 명령을 씁니다. Shorts는 그 세션이 다음·이전을 알려 줄 때 제어합니다. 호출어 한 번에는 명령을 한 번만 보냅니다.

마이크 소리는 **HI SENA**를 말했는지 판정하는 데만 쓰고, 카메라 화면은 손 모양을 판정하는 데만 씁니다. 둘 다 파일로 저장하거나 올리지 않습니다. 알림 접근은 유튜브 미디어 세션을 찾는 데 씁니다.

### 사용 방법

Android 8.0 이상, 전면 카메라, 마이크, 유튜브 앱이 필요합니다. 디버그 APK는 `app/build/outputs/apk/debug/app-debug.apk` 입니다. 빌드 명령은 [빌드](#빌드)에 있습니다.

1. **Sena Cue**를 설치하고 엽니다.
2. 화면 위쪽에서 언어와 화면 모드를 고릅니다.
3. **권한 요청**을 눌러 카메라, 마이크, Android 13 이상이면 알림을 허용합니다.
4. **알림 접근 설정**을 눌러 이 앱을 켠 뒤 돌아옵니다. 안드로이드는 이 접근을 허용한 앱에만 다른 앱의 미디어 세션 목록을 보여 줍니다. Sena Cue는 그 목록에서 유튜브를 찾아 일시정지 또는 재생을 요청합니다.
5. 선택 사항: **화면 위 표시 설정**에서 이 앱을 허용합니다. **HI SENA**가 들리면 유튜브 위에 파란 표시가 잠깐 뜨고 짧게 진동합니다. 그 표시는 터치를 받지 않습니다. 허용하지 않아도 제어는 시작되고, 호출어는 상태 알림으로 알려 줍니다.
6. 유튜브를 열고 일반 동영상을 재생합니다.
7. Sena Cue 화면이 보이는 상태에서 **제어 시작**을 누릅니다. 시작은 그 화면이 보일 때만 됩니다. 부팅할 때 저절로 켜지지 않습니다.
8. 유튜브로 돌아갑니다. 상태 알림은 남아 있습니다. **HI SENA**를 기다리는 동안에는 카메라를 열지 않습니다.
9. 영어로 **HI SENA**라고 말한 뒤 잠깐 멈춥니다. 상태가 마이크가 조용하다고 하면 폰에 더 가까이에서 말합니다.
10. 약 10초 안에 전면 카메라에 손 하나를 보여 줍니다.
    - 손가락을 편 손바닥: 일시정지.
    - 엄지 올림: 재생.
    - 엄지 내림: 다음 영상.
    - 주먹: 이전 영상.
    같은 모양을 약 0.25초 유지합니다. 카메라가 닫히고 유튜브에 명령이 간 뒤, 다시 **HI SENA**를 기다립니다.
11. 끝나면 앱이나 알림의 **제어 중지**를 누릅니다. 알림 권한이 없으면 앱으로 돌아와 그 버튼을 누릅니다.

10초 안에 손 모양이 확인되지 않으면 명령을 보내지 않고 다시 **HI SENA**를 기다립니다. 유튜브 세션이 없으면 화면에 그 내용이 나옵니다. 일반 동영상을 재생한 뒤 다시 말하면 됩니다.

### 하지 않는 일

Sena Cue는 영상을 재생하거나 유튜브를 내장하거나 영상을 내려받지 않습니다. 유튜브 뮤직과 다른 오디오 앱은 제어하지 않습니다. 탐색, 음량, 유튜브 종료도 없습니다. 좋아요, 댓글, 공유, 구독도 없습니다. 광고와 라이브는 이 제어에 넣지 않습니다. 호출어 한 번에 명령은 최대 한 번입니다.

### 개인정보

마이크 소리는 호출어 판정에만 씁니다. 카메라 화면은 손 모양 판정에만 씁니다. 둘 다 저장하거나 올리지 않습니다. 알림 접근은 미디어 세션을 찾는 데 쓰고 알림 본문은 모으지 않습니다. 클라우드 백업과 기기 간 전송에서도 앱 데이터를 제외합니다.

### 라이선스

이 저장소의 소스 코드는 [MIT 라이선스](LICENSE)로 공개합니다. Copyright (c) 2026 neskafe3112.

함께 넣은 모델과 서드파티 라이브러리는 각자의 업스트림 라이선스를 유지합니다. sherpa-onnx 키워드 모델, MediaPipe 제스처 모델, 그리고 Android, ONNX Runtime, sherpa-onnx, MediaPipe 의존성이 여기에 해당합니다. 이 MIT 허가는 이 앱 자신의 소스 코드에 적용됩니다.

### 빌드

필요 환경:

- JDK 17 이상.
- Android SDK. `local.properties`의 `sdk.dir`이 필요합니다.
- 컴파일 SDK 37.2, 대상 SDK 37, 최소 SDK 26.

```
.\gradlew.bat :app:testDebugUnitTest :app:lintDebug :app:assembleDebug
```

디버그 APK는 `app/build/outputs/apk/debug/app-debug.apk` 입니다.
