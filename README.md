# Sena Cue

## Summary

### English

Sena Cue pauses and resumes a regular video in the installed YouTube app. Say **HI SENA** in English, then hold an open palm to pause or a thumbs-up to play. Speech and hand recognition stay on the phone. The screen language can be English, Korean, Simplified Chinese, or Japanese, and the appearance can follow the system or be light or dark. The longer guide below is written separately in English and Korean.

### 한국어

Sena Cue는 폰에 설치된 유튜브 앱의 일반 동영상을 일시정지하거나 다시 재생합니다. 영어로 **HI SENA**라고 말한 뒤, 손바닥을 펼치면 일시정지고 엄지를 올리면 재생입니다. 음성과 손 인식은 폰 안에서 이루어집니다. 화면 언어는 영어, 한국어, 중국어 간체, 일본어 중에서 고를 수 있고, 화면 모드는 시스템을 따르거나 라이트 또는 다크로 둘 수 있습니다. 아래의 긴 안내는 영어와 한국어로 나누어 적었습니다.

### 中文

Sena Cue 可以暂停或继续播放已安装的 YouTube 应用中的普通视频。请用英语说 **HI SENA**，然后张开手掌暂停，或竖起大拇指播放。语音和手势识别都在手机上完成。界面语言可以是英语、韩语、简体中文或日语，外观可以跟随系统，也可以设为浅色或深色。下面的详细说明分为英语和韩语。

### 日本語

Sena Cue は、インストール済みの YouTube アプリにある通常の動画を一時停止したり再生したりします。英語で **HI SENA** と言ってから、開いた手のひらで一時停止、親指を上げて再生します。音声と手の認識はこの端末の中で行います。画面の言語は英語、韓国語、簡体字中国語、日本語から選べます。画面モードはシステムに従うか、ライトまたはダークにできます。詳しい説明は下に英語と韓国語で分けてあります。

## English

### What this app is

Sena Cue listens for the English wake word **HI SENA**, then opens the front camera for a few seconds. An open palm sends pause to YouTube. A thumbs-up sends play. Speech and hand recognition stay on the phone. There is no account and no API key.

The screen language can be English, Korean, Simplified Chinese, or Japanese. Appearance can follow the system, or be light or dark. Button names in this English guide match the English screen.

The app sends those two commands to the YouTube app package `com.google.android.youtube`. It is aimed at a regular video while YouTube exposes a media session and notification access is on. Each wake accepts one command. An open palm always pauses, and a thumbs-up always plays.

Palm pause and thumb play were checked on a phone. Hearing a spoken **HI SENA** and then opening the camera has been checked with an English recording, and has not yet been checked with a person speaking into the phone.

Audio is used only to decide whether **HI SENA** was said. Camera frames are used only to classify the hand. Neither is saved to a file or uploaded. The notification listener is used to find YouTube's media session.

### How to use it

You need Android 8.0 or later, a front camera, a microphone, and the YouTube app. This project builds a debug APK at `app/build/outputs/apk/debug/app-debug.apk`. See [Build and run](#build-and-run) for the build command.

1. Install and open **Sena Cue**.
2. Tap **Request permissions**. Allow the camera, the microphone, and, on Android 13 or later, notifications.
3. Tap **Notification access**. Turn this app on, then come back. Android shows other apps' media sessions only to apps with this access. The app uses that list to find YouTube and request pause or play.
4. Optional: tap **Display over other apps** and allow this app. When **HI SENA** is heard, a blue banner appears briefly over YouTube and the phone vibrates. The banner does not take touches. If you skip this, the app still starts and reports the wake word through the status notification.
5. Open YouTube and start a regular video.
6. Return to this app while its screen is visible and tap **Start control**. The app can be started only from that visible screen. It does not start on boot.
7. Switch back to YouTube. A status notification stays up. While the app is waiting for the wake word, the camera stays closed.
8. Say **HI SENA** in English, then pause for a moment. Speak closer to the phone if the status says the microphone is quiet.
9. Within about 10 seconds, hold one hand toward the front camera:
   - Open palm, fingers spread: pause.
   - Thumbs-up: play.
   Hold the same shape for about a quarter of a second. The camera closes, the command goes to YouTube, and the app waits for **HI SENA** again.
10. Tap **Stop control** in the app or in the notification when you are done. If notifications are off, open the app and use the button there.

If 10 seconds pass with no confirmed gesture, no command is sent and the app listens for **HI SENA** again. If YouTube has no active session, the status says so. Play a regular video and try again.

### License

The source code of this repository is released under the [MIT License](LICENSE). Copyright (c) 2026 neskafe3112.

Bundled models and third-party libraries stay under their own upstream licenses. That includes the sherpa-onnx keyword model, the MediaPipe gesture model, and the Android, ONNX Runtime, sherpa-onnx, and MediaPipe dependencies. This MIT grant covers this app's own source code.

### Developer notes

Sena Cue is a Kotlin app that pauses or resumes playback in the installed YouTube app with a hand gesture. This app does not play video, embed YouTube, or download video.

After the wake word, an open palm pauses and a thumbs-up plays.

### Current status

Palm pause and thumb play were checked on a phone. Hearing `HI SENA` and then opening the camera has not yet been checked on a phone. No API key is required.

### Architecture

The screen handles permissions and the start and stop buttons. The wake word, camera, timeouts, and command dispatch belong to `ControlService`. When the user opens YouTube, the Activity stops, so the camera lifecycle is not tied to the screen.

There is one `ControlState`. The state cannot be both "opening the camera" and "already stopped".

```
MainActivity
  permissions, setup guidance, buttons
ControlService
  foreground service, state machine, timeouts, resource release
WakeWordDetector / SherpaWakeWordDetector
  one wake word. If the key files are missing, the detector is not created
GestureAnalyzer
  service-owned CameraX front camera + MediaPipe
YoutubeController
  finds the YouTube session through NotificationListenerService and calls pause() or play()
ControlStatusRepository
  in-process StateFlow. It does not store an Activity reference
```

The camera stays closed while waiting for the wake word. The service still starts with the `camera|microphone` type. On Android 14 and later, the camera type has to be declared while the app is visible, so the camera can be opened later while YouTube is in front. Declaring the type is separate from keeping the camera hardware open. This path has not yet been checked on a device. On failure, the app shows the exception text and does not switch to leaving the camera open.

### Project layout

```
app/src/main/java/com/example/youtubegesturecontrol/
  MainActivity.kt
  YouTubeGestureApplication.kt
  service/          state machine, service, camera lifecycle, notification
  wakeword/         one sherpa-onnx wake word
  gesture/          stability check and CameraX/MediaPipe
  media/            YouTube session selection and playback commands
  precondition/     permission and setup text
  timing/           timeout scheduling
app/src/main/assets/gesture_recognizer.task
app/src/test/       unit tests that run without a camera
```

### Build and run

Requirements:

- JDK 17 or later. This environment used JDK 21.
- Android SDK. `sdk.dir` in `local.properties` is required.
- Compile SDK 37.2, target SDK 37, minimum SDK 26.
- Android Gradle Plugin 9.4.1, Gradle 9.6.0.

```
.\gradlew.bat :app:testDebugUnitTest :app:lintDebug :app:assembleDebug
```

The debug APK is `app/build/outputs/apk/debug/app-debug.apk`. The signing setup is unchanged, and the APK is assembled with the debug key.

### Permissions and settings

| Item | Role |
| --- | --- |
| Camera | Watches the hand with the front camera. Frames are not saved. |
| Microphone | Listens for the wake word on the device. |
| Post notifications | Shows the status notification on Android 13 and later. |
| Notification access | A separate setting used to read other apps' media sessions. It is not used to store notification contents. |
| Foreground service `camera`, `microphone` | Types that allow the microphone, and the camera only when needed, while the screen is not visible. |

**Start control** starts the service only from a button while the Activity is visible. There is no start on boot. If the process dies, `START_NOT_STICKY` means it is not started again on its own. A foreground service also does not always survive a manufacturer's battery policy.

If a permission is denied, the status is shown in the selected language, and the same request is not repeated in a loop. If the system will not ask again, the app points to its settings. Before opening notification access settings, the app explains why. After returning from settings, `onResume` checks again.

### Wake word

The app uses the English keyword model from sherpa-onnx 1.13.5. There is no account and no API key. There is one wake word, and the spoken phrase is `HI SENA`. `HELLO WORLD` was too long and still failed in a quiet room. `ASUMI` was not recognized from this model's pronunciation pieces.

Only the needed files from the official mobile package are included.

- Source: `https://github.com/k2-fsa/sherpa-onnx/releases/download/kws-models/sherpa-onnx-kws-zipformer-gigaspeech-3.3M-2024-01-01-mobile.tar.bz2`
- Path: `app/src/main/assets/kws/`
- Recognition stays on the device. The app does not use the internet permission.
- The published encoder appends the last frame once more after a 16-frame chunk. The following reshape accepts only 16 frames, so ONNX Runtime kills the process when control starts. The encoder in assets has only that extra connection removed. The decoder, joiner, tokens, and keywords are the published files.

YouTube speaker audio can false-trigger the wake word. That sensitivity has not been tuned on a phone. This model has no Korean wake word.

### MediaPipe model

The official float16 gesture model is included.

- Source: `https://storage.googleapis.com/mediapipe-models/gesture_recognizer/gesture_recognizer/float16/1/gesture_recognizer.task`
- Path: `app/src/main/assets/gesture_recognizer.task`
- Library: `com.google.mediapipe:tasks-vision:1.1.1`
- Mode: `LIVE_STREAM`, one hand, CPU delegate

If the model is missing, the app does not create an empty `.task` file. It blocks start and shows the missing path. CPU was chosen because the GPU delegate is tied to the thread that created it. That does not mean CPU always uses less power.

Only `Open_Palm` and `Thumb_Up` are used as commands. The minimum palm score is 0.55, the thumb score is 0.7, and the same shape is confirmed after it holds for 250 ms. The model itself was not reduced to a two-class model.

A new session does not start while another gesture session is in progress. One session sends at most one command. An open palm always pauses, and a thumbs-up always plays. A toggle is not used instead.

Command results distinguish these cases:

- The request was sent and the state was confirmed.
- The playback was already in that state before the request.
- The request was sent, but confirmation timed out.
- No session.
- No notification access.
- The action was not advertised, so it was not sent.

A successful method call alone is not treated as a playback change. The camera is closed while confirmation is pending.

Only `com.google.android.youtube` is selected. YouTube Music and other audio apps are not controlled.

### Initial supported scenario

The target is a regular YouTube video when notification access is on and YouTube exposes a media session.

The app does not claim support for Shorts, ads, live streams, or every YouTube version. If YouTube drops the session while paused, the app reports that there is no session.

### Known limits

- Without the wake-word key files, the full wake-word loop is unfinished.
- Opening the camera again while YouTube is in front is prepared in code, and device verification remains.
- Front-camera rotation and mirroring follow the MediaPipe sample order, and have not yet been checked on a device.
- An accessibility service, screen-coordinate taps, and a global media button are not used instead.
- There is no seek, volume, next video, or YouTube exit.
- There is no start on boot and no always-on WakeLock.

### Privacy

Microphone audio is used only to judge the wake word. It is not saved to a file or uploaded. Camera frames are closed after inference, and are not recorded or uploaded. The notification listener is required to look up the session and does not collect notification bodies. Rules also exclude app data from cloud backup and device-to-device transfer.

## 한국어

### 이 앱은 무엇인가

Sena Cue는 영어 호출어 **HI SENA**를 들은 뒤 전면 카메라를 몇 초 열고, 손 모양으로 유튜브에 명령을 보냅니다. 손바닥을 펼치면 일시정지, 엄지를 올리면 재생입니다. 음성과 손 인식은 폰 안에서 이루어지고, 계정이나 API 키는 없습니다.

화면 언어는 영어, 한국어, 중국어 간체, 일본어 중에서 고릅니다. 화면 모드는 시스템을 따르거나 라이트 또는 다크입니다. 이 한국어 안내의 버튼 이름은 한국어 화면과 같습니다.

명령은 유튜브 앱 패키지 `com.google.android.youtube`로만 갑니다. 유튜브가 미디어 세션을 내놓고 알림 접근이 켜져 있는 일반 동영상을 대상으로 합니다. 호출어 한 번에는 명령을 한 번만 보냅니다. 손바닥은 항상 일시정지이고, 엄지는 항상 재생입니다.

손바닥 일시정지와 엄지 재생은 폰에서 확인했습니다. 영어 녹음으로 **HI SENA**가 잡히는 것까지는 확인했고, 폰에 직접 말한 뒤 카메라가 열리는 흐름은 아직 확인하지 않았습니다.

마이크 소리는 호출어를 판정하는 데만 쓰고, 카메라 화면은 손 모양을 판정하는 데만 씁니다. 둘 다 파일로 저장하거나 올리지 않습니다. 알림 접근은 유튜브 미디어 세션을 찾는 데 씁니다.

### 사용 방법

Android 8.0 이상, 전면 카메라, 마이크, 유튜브 앱이 필요합니다. 이 프로젝트의 디버그 APK는 `app/build/outputs/apk/debug/app-debug.apk` 입니다. 빌드 명령은 [빌드와 실행](#빌드와-실행)에 있습니다.

1. **Sena Cue**를 설치하고 엽니다.
2. **권한 요청**을 눌러 카메라, 마이크, Android 13 이상이면 알림을 허용합니다.
3. **알림 접근 설정**을 눌러 이 앱을 켠 뒤 돌아옵니다. 안드로이드는 이 접근을 허용한 앱에만 다른 앱의 미디어 세션 목록을 보여 줍니다. 이 앱은 그 목록에서 유튜브를 찾아 일시정지 또는 재생을 요청합니다.
4. 선택 사항: **화면 위 표시 설정**에서 이 앱을 허용합니다. **HI SENA**가 들리면 유튜브 위에 파란 표시가 잠깐 뜨고 짧게 진동합니다. 그 표시는 터치를 받지 않습니다. 허용하지 않아도 제어는 시작되고, 호출어는 상태 알림으로 알려 줍니다.
5. 유튜브를 열고 일반 동영상을 재생합니다.
6. 이 앱 화면이 보이는 상태에서 **제어 시작**을 누릅니다. 시작은 그 화면이 보일 때만 됩니다. 부팅할 때 저절로 켜지지 않습니다.
7. 유튜브로 돌아갑니다. 상태 알림은 남아 있습니다. 호출어를 기다리는 동안에는 카메라를 열지 않습니다.
8. 영어로 **HI SENA**라고 말한 뒤 잠깐 멈춥니다. 상태가 마이크가 조용하다고 하면 폰에 더 가까이에서 말합니다.
9. 약 10초 안에 전면 카메라에 손 하나를 보여 줍니다.
   - 손가락을 편 손바닥: 일시정지.
   - 엄지: 재생.
   같은 모양을 약 0.25초 유지합니다. 카메라가 닫히고 유튜브에 명령이 간 뒤, 다시 **HI SENA**를 기다립니다.
10. 끝나면 앱이나 알림의 **제어 중지**를 누릅니다. 알림 권한이 없으면 앱으로 돌아와 그 버튼을 누릅니다.

10초 안에 손 모양이 확인되지 않으면 명령을 보내지 않고 다시 **HI SENA**를 기다립니다. 유튜브 세션이 없으면 화면에 그 내용이 나옵니다. 일반 동영상을 재생한 뒤 다시 말하면 됩니다.

### 라이선스

이 저장소의 소스 코드는 [MIT 라이선스](LICENSE)로 공개합니다. Copyright (c) 2026 neskafe3112.

함께 넣은 모델과 서드파티 라이브러리는 각자의 업스트림 라이선스를 유지합니다. sherpa-onnx 키워드 모델, MediaPipe 제스처 모델, 그리고 Android, ONNX Runtime, sherpa-onnx, MediaPipe 의존성이 여기에 해당합니다. 이 MIT 허가는 이 앱 자신의 소스 코드에 적용됩니다.

### 개발 참고

안드로이드에 설치된 유튜브 앱의 재생을 손 동작으로 일시정지하거나 다시 재생하는 Kotlin 앱입니다. 이 앱은 영상을 재생하지 않고, 유튜브를 내장하지 않으며, 영상을 내려받지도 않습니다.

호출어를 말한 뒤 손바닥을 펼치면 일시정지, 엄지를 올리면 재생합니다.

### 현재 상태

손바닥 일시정지와 엄지 재생은 폰에서 확인했습니다. 호출어 `HI SENA`를 말한 뒤 카메라가 열리는 흐름은 아직 폰에서 확인하지 않았습니다. API 키는 필요 없습니다.

### 아키텍처

화면은 권한과 시작/중지만 담당합니다. 호출어, 카메라, 제한 시간, 명령 전송은 `ControlService`가 소유합니다. 사용자가 유튜브를 열면 Activity는 멈추므로, 카메라 수명 주기를 화면에 묶지 않습니다.

상태는 `ControlState` 하나뿐입니다. 카메라 여는 중과 이미 중지됨이 동시에 성립하지 않습니다.

```
MainActivity
  권한, 설정 안내, 버튼
ControlService
  포그라운드 서비스, 상태 기계, 제한 시간, 자원 해제
WakeWordDetector / SherpaWakeWordDetector
  호출어 하나. 키가 없으면 감지기를 만들지 않음
GestureAnalyzer
  서비스 소유의 CameraX 전면 카메라 + MediaPipe
YoutubeController
  NotificationListenerService로 유튜브 세션을 찾아 pause() 또는 play()
ControlStatusRepository
  프로세스 안의 StateFlow. Activity 참조는 저장하지 않음
```

호출어 대기 중에는 카메라를 열지 않습니다. 다만 서비스는 시작할 때부터 `camera|microphone` 유형입니다. 안드로이드 14 이후에는 앱이 화면에 보일 때 카메라 유형으로 시작해 두어야, 나중에 유튜브가 앞에 있을 때 카메라를 열 수 있습니다. 유형을 선언하는 것과 카메라 하드웨어를 열어 두는 것은 다릅니다. 이 경로는 아직 기기에서 확인하지 않았습니다. 실패하면 예외 문구를 보여주고, 카메라를 계속 열어 두는 방식으로 바꾸지 않습니다.

### 프로젝트 구조

```
app/src/main/java/com/example/youtubegesturecontrol/
  MainActivity.kt
  YouTubeGestureApplication.kt
  service/          상태 기계, 서비스, 카메라 수명 주기, 알림
  wakeword/         sherpa-onnx 호출어 하나
  gesture/          안정 판정과 CameraX/MediaPipe
  media/            유튜브 세션 선택과 재생 명령
  precondition/     권한과 설정 문구
  timing/           제한 시간 예약
app/src/main/assets/gesture_recognizer.task
app/src/test/       카메라 없이 도는 단위 테스트
```

### 빌드와 실행

필요 환경:

- JDK 17 이상. 이 환경은 JDK 21이었습니다.
- Android SDK. `local.properties`의 `sdk.dir`이 필요합니다.
- 컴파일 SDK 37.2, 대상 SDK 37, 최소 SDK 26.
- Android Gradle Plugin 9.4.1, Gradle 9.6.0.

```
.\gradlew.bat :app:testDebugUnitTest :app:lintDebug :app:assembleDebug
```

디버그 APK는 `app/build/outputs/apk/debug/app-debug.apk` 입니다. 서명 설정은 바꾸지 않았고, 디버그 키로 조립합니다.

### 권한과 설정

| 항목 | 역할 |
| --- | --- |
| 카메라 | 전면 카메라로 손을 봅니다. 프레임은 저장하지 않습니다. |
| 마이크 | 호출어를 기기 안에서 듣습니다. |
| 알림 게시 | 안드로이드 13 이상에서 상태 알림을 보여 줍니다. |
| 알림 접근 | 다른 앱의 미디어 세션 목록을 조회하는 별도 설정입니다. 알림 내용 저장용이 아닙니다. |
| 포그라운드 서비스 `camera`, `microphone` | 화면 밖에서도 마이크와, 필요할 때만 카메라를 쓰기 위한 유형입니다. |

**제어 시작**은 Activity가 보이는 동안의 버튼으로만 서비스를 시작합니다. 부팅 시 자동 시작은 없습니다. 프로세스가 죽으면 `START_NOT_STICKY`라서 자동으로 다시 켜지지 않습니다. 포그라운드 서비스도 제조사 절전 정책에서 항상 살아남지는 않습니다.

권한이 거부되면 고른 언어로 상태를 보여 주고, 같은 요청을 루프로 반복하지 않습니다. 다시 묻지 않도록 거부되면 앱 설정으로 안내합니다. 알림 접근 설정으로 이동하기 전에는 이유를 설명하는 창을 띄웁니다. 설정에서 돌아온 뒤 `onResume`에서 다시 확인합니다.

### 호출어

sherpa-onnx 1.13.5의 영어 키워드 모델을 사용합니다. 계정이나 API 키는 없습니다. 호출어는 하나이고, 말할 문구는 `HI SENA`입니다. `HELLO WORLD`는 길어서 조용한 방에서도 잘 안 맞았고, `ASUMI`는 이 모델의 발음 조각으로 인식되지 않았습니다.

모델은 공식 모바일 패키지에서 필요한 파일만 넣었습니다.

- 출처: `https://github.com/k2-fsa/sherpa-onnx/releases/download/kws-models/sherpa-onnx-kws-zipformer-gigaspeech-3.3M-2024-01-01-mobile.tar.bz2`
- 경로: `app/src/main/assets/kws/`
- 인식은 기기 안에서만 합니다. 인터넷 권한은 쓰지 않습니다.
- 배포된 인코더는 16프레임 청크 뒤에 마지막 프레임을 한 번 더 붙입니다. 그 다음 reshape는 16프레임만 받으므로, 제어를 시작하면 ONNX Runtime이 프로세스를 종료합니다. 에셋의 인코더는 그 여분 연결만 제거했습니다. 디코더, 조이너, 토큰, 키워드는 배포본 그대로입니다.

유튜브 스피커 소리로 호출어가 잘못 들릴 수 있습니다. 이 민감도는 폰에서 맞추지 않았습니다. 한국어 호출어는 이 모델이 지원하지 않습니다.

### MediaPipe 모델

공식 float16 제스처 모델을 받아 두었습니다.

- 출처: `https://storage.googleapis.com/mediapipe-models/gesture_recognizer/gesture_recognizer/float16/1/gesture_recognizer.task`
- 경로: `app/src/main/assets/gesture_recognizer.task`
- 라이브러리: `com.google.mediapipe:tasks-vision:1.1.1`
- 모드: `LIVE_STREAM`, 손 1개, CPU delegate

모델이 없으면 빈 `.task` 파일을 만들지 않고, 시작을 막은 뒤 없는 파일 경로를 보여 줍니다. CPU를 고른 이유는 GPU delegate가 생성 스레드에 묶이기 때문입니다. CPU가 항상 더 절전이라는 뜻은 아닙니다.

`Open_Palm`과 `Thumb_Up`만 명령으로 씁니다. 손바닥 최소 점수는 0.55, 엄지는 0.7이고, 같은 손 모양이 250ms 유지되면 확정합니다. 모델 자체를 두 클래스 모델로 줄인 것은 아닙니다.

다른 제스처 세션이 진행 중이면 새 세션을 시작하지 않습니다. 한 세션의 명령은 최대 한 번입니다. 손바닥은 항상 일시정지, 엄지는 항상 재생입니다. 토글로 대체하지 않습니다.

명령 결과는 다음을 구분합니다.

- 요청을 보냈고 상태가 확인됨
- 요청 전부터 이미 그 상태였음
- 요청은 보냈지만 확인 시간이 지남
- 세션 없음
- 알림 접근 없음
- 동작이 광고되지 않아 보내지 않음

메서드 호출 성공만으로 재생이 바뀌었다고 보지 않습니다. 확인을 기다리는 동안 카메라는 닫혀 있습니다.

세션은 `com.google.android.youtube`만 고릅니다. 유튜브 뮤직과 다른 오디오 앱은 제어하지 않습니다.

### 지원하는 초기 시나리오

일반 유튜브 동영상에서, 알림 접근이 켜져 있고 유튜브가 미디어 세션을 내놓는 경우를 대상으로 합니다.

Shorts, 광고, 라이브, 모든 유튜브 버전을 지원 범위에 넣지 않습니다. 일시정지 중에 유튜브가 세션을 거두면, 앱은 세션이 없다고 보여 줍니다.

### 알려진 제한

- 호출어 키가 없으면 전체 호출어 루프는 끝나지 않은 상태입니다.
- 유튜브가 앞에 있는 동안 카메라를 다시 여는 동작은 코드로만 준비되어 있고 기기 검증이 남아 있습니다.
- 전면 카메라 회전과 좌우 반전은 MediaPipe 예제와 같은 순서이며, 실기기 확인 전입니다.
- 접근성 서비스, 화면 좌표 탭, 전역 미디어 버튼으로 대체하지 않습니다.
- 탐색, 음량, 다음 영상, 유튜브 종료는 없습니다.
- 부팅 자동 시작과 상시 WakeLock은 없습니다.

### 개인정보

마이크 오디오는 호출어 판정에만 쓰고 파일로 저장하거나 업로드하지 않습니다. 카메라 프레임도 추론 뒤 닫고, 녹화나 업로드를 하지 않습니다. 알림 리스너는 세션 조회에 필요하고 알림 본문을 수집하지 않습니다. 클라우드 백업과 기기 간 전송에서도 앱 데이터를 제외하도록 규칙을 넣었습니다.
