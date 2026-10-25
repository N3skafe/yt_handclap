# 유튜브 제스처 제어

안드로이드에 설치된 유튜브 앱의 재생을 손 동작으로 일시정지하거나 다시 재생하는 Kotlin 앱입니다. 이 앱은 영상을 재생하지 않고, 유튜브를 내장하지 않으며, 영상을 내려받지도 않습니다.

호출어를 말한 뒤 손바닥을 펼치면 일시정지, 엄지를 올리면 재생합니다.

## 현재 상태

손바닥 일시정지와 엄지 재생은 폰에서 확인했습니다. 호출어 `SENA`를 말한 뒤 카메라가 열리는 흐름은 아직 폰에서 확인하지 않았습니다. API 키는 필요 없습니다.

## 아키텍처

화면은 권한과 시작/중지만 담당합니다. 호출어, 카메라, 제한 시간, 명령 전송은 `ControlService`가 소유합니다. 사용자가 유튜브를 열면 Activity는 멈추므로, 카메라 수명 주기를 화면에 묶지 않습니다.

상태는 `ControlState` 하나뿐입니다. "카메라 여는 중인데 이미 중지됨" 같은 조합은 만들 수 없습니다.

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

## 프로젝트 구조

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

## 빌드와 실행

필요 환경:

- JDK 17 이상. 이 환경은 JDK 21이었습니다.
- Android SDK. `local.properties`의 `sdk.dir`이 필요합니다.
- 컴파일 SDK 37.2, 대상 SDK 37, 최소 SDK 26.
- Android Gradle Plugin 9.4.1, Gradle 9.6.0.

```
.\gradlew.bat :app:testDebugUnitTest :app:lintDebug :app:assembleDebug
```

디버그 APK는 `app/build/outputs/apk/debug/app-debug.apk` 입니다. 서명 설정은 바꾸지 않았고, 디버그 키로 조립합니다.

## 권한과 설정

| 항목 | 역할 |
| --- | --- |
| 카메라 | 전면 카메라로 손을 봅니다. 프레임은 저장하지 않습니다. |
| 마이크 | 호출어를 기기 안에서 듣습니다. |
| 알림 게시 | 안드로이드 13 이상에서 상태 알림을 보여 줍니다. |
| 알림 접근 | 다른 앱의 미디어 세션 목록을 조회하는 별도 설정입니다. 알림 내용 저장용이 아닙니다. |
| 포그라운드 서비스 `camera`, `microphone` | 화면 밖에서도 마이크와, 필요할 때만 카메라를 쓰기 위한 유형입니다. |

"제어 시작"은 Activity가 보이는 동안의 버튼으로만 서비스를 시작합니다. 부팅 시 자동 시작은 없습니다. 프로세스가 죽으면 `START_NOT_STICKY`라서 자동으로 다시 켜지지 않습니다. 포그라운드 서비스도 제조사 절전 정책에서 항상 살아남지는 않습니다.

권한이 거부되면 상태를 한국어로 보여 주고, 같은 요청을 루프로 반복하지 않습니다. "다시 묻지 않음"이면 앱 설정으로 안내합니다. 알림 접근 설정으로 이동하기 전에는 이유를 설명하는 창을 띄웁니다. 설정에서 돌아온 뒤 `onResume`에서 다시 확인합니다.

## 호출어

sherpa-onnx 1.13.5의 영어 키워드 모델을 사용합니다. 계정이나 API 키는 없습니다. 호출어는 하나이고, 말할 문구는 `SENA`입니다. `HELLO WORLD`는 길어서 조용한 방에서도 잘 안 맞았고, `ASUMI`는 이 모델의 발음 조각으로 인식되지 않았습니다.

모델은 공식 모바일 패키지에서 필요한 파일만 넣었습니다.

- 출처: `https://github.com/k2-fsa/sherpa-onnx/releases/download/kws-models/sherpa-onnx-kws-zipformer-gigaspeech-3.3M-2024-01-01-mobile.tar.bz2`
- 경로: `app/src/main/assets/kws/`
- 인식은 기기 안에서만 합니다. 인터넷 권한은 쓰지 않습니다.
- 배포된 인코더는 16프레임 청크 뒤에 마지막 프레임을 한 번 더 붙입니다. 그 다음 reshape는 16프레임만 받으므로, 제어를 시작하면 ONNX Runtime이 프로세스를 종료합니다. 에셋의 인코더는 그 여분 연결만 제거했습니다. 디코더, 조이너, 토큰, 키워드는 배포본 그대로입니다.

유튜브 스피커 소리로 호출어가 잘못 들릴 수 있습니다. 이 민감도는 폰에서 맞추지 않았습니다. 한국어 호출어는 이 모델이 지원하지 않습니다.

## MediaPipe 모델

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

## 지원하는 초기 시나리오

일반 유튜브 동영상에서, 알림 접근이 켜져 있고 유튜브가 미디어 세션을 내놓는 경우를 대상으로 합니다.

Shorts, 광고, 라이브, 모든 유튜브 버전을 지원한다고 말하지 않습니다. 일시정지 중에 유튜브가 세션을 거두면, 앱은 세션이 없다고 보여 줍니다.

## 알려진 제한

- 호출어 키가 없으면 전체 호출어 루프는 끝나지 않은 상태입니다.
- 유튜브가 앞에 있는 동안 카메라를 다시 여는 동작은 코드로만 준비되어 있고 기기 검증이 남아 있습니다.
- 전면 카메라 회전과 좌우 반전은 MediaPipe 예제와 같은 순서이며, 실기기 확인 전입니다.
- 접근성 서비스, 화면 좌표 탭, 전역 미디어 버튼으로 대체하지 않습니다.
- 탐색, 음량, 다음 영상, 유튜브 종료는 없습니다.
- 부팅 자동 시작과 상시 WakeLock은 없습니다.

## 개인정보

마이크 오디오는 호출어 판정에만 쓰고 파일로 저장하거나 업로드하지 않습니다. 카메라 프레임도 추론 뒤 닫고, 녹화나 업로드를 하지 않습니다. 알림 리스너는 세션 조회에 필요하고 알림 본문을 수집하지 않습니다. 클라우드 백업과 기기 간 전송에서도 앱 데이터를 제외하도록 규칙을 넣었습니다.
