# Kotlin 안내

Flutter/Dart에 익숙한 상태에서 이 저장소를 읽을 때 쓰는 안내입니다. 문법은 처음 중요한 코드에 한국어 설명도 붙어 있습니다.

## 읽는 순서

1. `service/ControlState.kt`
   상태가 왜 한 종류뿐인지 봅니다.
2. `service/ControlStateMachine.kt`
   호출어, 카메라, 명령이 어떤 순서로 바뀌는지 봅니다. 안드로이드 API가 없습니다.
3. `gesture/GestureStabilityTracker.kt`
   한 프레임을 바로 명령으로 바꾸지 않는 이유를 봅니다.
4. `MainActivity.kt`
   화면이 서비스 상태를 구독만 하는 방식을 봅니다.
5. `service/ControlService.kt`
   서비스가 효과를 실행하고 자원을 닫는 방식을 봅니다.
6. `gesture/GestureAnalyzer.kt`
   카메라 소유와 프레임 수명을 봅니다.
7. `media/YoutubeController.kt`
   일시정지와 재생을 따로 보내는 방식을 봅니다.
8. `wakeword/SherpaWakeWordDetector.kt`
   콜백이 어느 스레드에서 오는지 봅니다.

## 클래스 역할

| 클래스 | 하는 일 |
| --- | --- |
| `MainActivity` | 한국어 화면, 권한 요청, 버튼. 카메라 세션을 소유하지 않습니다. |
| `YouTubeGestureApplication` | 프로세스가 살아있는 동안 `StateFlow` 저장소를 둡니다. |
| `ControlService` | 포그라운드 서비스. 상태 기계가 시킨 효과만 실행합니다. |
| `ControlStateMachine` | 다음 상태와 효과를 결정합니다. 테스트가 이 클래스를 직접 호출합니다. |
| `ControlStatusRepository` | 현재 상태와 최근 결과를 보유합니다. |
| `ServiceCameraLifecycleOwner` | 인식 중에만 `RESUMED`가 되는 카메라 수명 주기입니다. |
| `GestureAnalyzer` | 전면 카메라와 MediaPipe. 서비스가 생성하고 닫습니다. |
| `GestureStabilityTracker` | 같은 손 모양이 충분히 유지됐는지 계산합니다. |
| `YoutubeController` | 유튜브 세션을 다시 조회하고 `pause()` 또는 `play()`를 호출합니다. |
| `MediaNotificationListener` | 세션 조회에 필요한 알림 접근 구성요소입니다. |
| `SherpaWakeWordDetector` | 영어 호출어 `HI SENA`를 듣습니다. |
| `PrerequisiteChecker` | 권한, 알림 접근, 모델, 호출어 설정을 문장으로 만듭니다. |

## Dart와 비교

### data class

`GestureSample`과 `ControlSnapshot`이 data class입니다. 컴파일러가 `equals`, `hashCode`, `toString`, `copy`를 만듭니다. Dart에서 필드를 비교하거나 `copyWith`를 직접 두던 클래스에 가깝습니다. `copy`는 기존 객체를 바꾸지 않고 일부 필드만 다른 새 객체를 만듭니다.

### sealed class

`ControlState`는 sealed class입니다. Dart 3의 sealed class처럼, 허용된 하위 타입만 존재합니다. `when`으로 다루면 분기를 빠뜨릴 때 컴파일 오류가 납니다. boolean 여러 개로 상태를 조합하지 않은 이유입니다.

### enum class

`HandGesture`와 `PlaybackCommand`는 enum입니다. Dart enum과 같고, 값마다 함수를 둘 수 있습니다. 손바닥은 `PAUSE`, 엄지는 `PLAY`이며 하나의 토글 enum으로 합치지 않았습니다.

### null

`?.`는 null이면 호출을 건너뜁니다. Dart의 `?.`와 같습니다. `?:`는 null일 때 오른쪽 값을 씁니다. Dart의 `??`와 같습니다. `as?`는 타입이 아니면 null이고, 맞으면 그 분기에서 더 구체적인 타입으로 쓸 수 있습니다. 실패하면 예외가 나는 `as`나 `!!`는 쓰지 않습니다.

### object와 companion object

`YoutubeSessionSelector`의 `object`는 인스턴스가 하나인 객체입니다. Dart에서 생성자를 숨기고 static 함수만 두는 형태와 비슷합니다.

`ControlService`의 `companion object`는 인스턴스 없이 부르는 상수와 시작 함수를 담습니다. Dart의 static과 비슷하지만, companion 자체도 객체입니다. Activity를 저장하지 않습니다.

### 콜백

sherpa-onnx와 MediaPipe, `MediaController`는 콜백으로 결과를 돌려줍니다. Dart의 `VoidCallback`처럼 함수를 넘깁니다. 차이가 있다면 안드로이드 콜백은 다른 스레드에서 올 수 있다는 점입니다. 오디오 콜백은 감지기 안에서 메인 스레드로 올리고, 그 다음에만 상태 기계를 호출합니다.

### 코루틴

`ControlService`의 `CoroutineScope`는 멈췄다가 이어지는 작업을 담습니다. Dart의 `async`/`await`와 비슷합니다. `delay`는 스레드를 붙잡지 않고 기다립니다. scope를 취소하면 그 대기 작업도 취소됩니다. `Main.immediate`는 이미 메인 스레드이면 다음 이벤트까지 미루지 않아서, 상태 전환 순서가 뒤바뀌지 않습니다.

### StateFlow

`ControlStatusRepository.snapshot`은 항상 현재 값을 가지고, 새 구독자에게 그 값을 바로 줍니다. Dart의 `BehaviorSubject`나 `ValueNotifier`에 가깝습니다. 일반 `Stream`은 마지막 값을 다시 주지 않을 수 있습니다.

`MainActivity`는 `repeatOnLifecycle(STARTED)` 안에서 `collect`합니다. `collect`는 Dart의 `listen`처럼 끝까지 구독합니다. 화면이 멈추면 구독을 취소해, 파괴된 뷰를 갱신하지 않습니다. 다시 보이면 StateFlow가 마지막 상태를 바로 줍니다.

### apply

알림의 Intent를 만들 때 `apply { ... }`를 사용합니다. 객체를 만든 뒤 그 객체 안에서 필드를 채우고 같은 객체를 돌려줍니다. Dart에서 객체를 생성한 다음 필드를 넣는 코드와 같습니다.

## 서비스가 소유하는 수명 주기

Flutter에서는 위젯이 dispose되면 그 위젯이 연 카메라 구독도 같이 끊기는 경우가 많습니다. 이 앱은 사용자가 유튜브를 여는 순간 Activity가 멈추므로, 그때 카메라가 같이 닫히면 안 됩니다.

그래서:

1. 사용자가 보이는 Activity에서 "제어 시작"을 누릅니다.
2. 서비스가 즉시 포그라운드 알림을 띄웁니다. 안드로이드 14는 카메라/마이크 유형 서비스를 화면 밖에서 새로 시작하지 못하게 합니다.
3. 호출어를 기다리는 동안 카메라 하드웨어는 열지 않습니다.
4. 호출어가 들리면 서비스 안의 `LifecycleRegistry`만 `RESUMED`로 올리고 CameraX를 바인드합니다.
5. 성공, 시간 초과, 중지, 오류에서 바인드를 풀고 수명 주기를 `CREATED`로 내려 카메라를 닫습니다.
6. 서비스가 파괴될 때만 그 수명 주기를 `DESTROYED`로 보냅니다. 한 번 파괴된 수명 주기는 다시 쓸 수 없습니다.

`LifecycleService`를 그대로 쓰지 않은 이유는, 그 수명 주기가 서비스가 살아있는 동안 계속 시작된 상태로 남아 카메라가 대기 중에도 열려 있을 수 있기 때문입니다.

## 비동기 콜백과 세션 번호

카메라 준비, 제스처 결과, 재생 상태 콜백은 요청한 뒤 나중에 도착합니다. 사용자가 그 사이에 중지하면 늦은 콜백이 새 세션을 건드리면 안 됩니다.

상태 기계는 세션마다 번호를 올립니다. 중지하면 번호가 바뀌고 상태는 `Stopped`가 됩니다. 옛 번호의 준비 완료, 제스처, 명령 완료는 효과가 없습니다. 인식 제한 시간도 세션 번호로 취소합니다. 시간 초과 콜백이 제스처 확정보다 늦게 도착해도, 이미 정리 중이면 무시합니다.

프레임의 `ImageProxy`는 분석 함수의 `finally`에서 정확히 한 번 닫습니다. MediaPipe에 넘기는 이미지는 비트맵 복사본이라, 카메라 버퍼를 닫은 뒤에도 추론이 복사본을 사용합니다. 복사본은 결과 콜백 또는 오류 콜백에서 한 번만 닫습니다.

## 테스트가 따라가는 코드

`app/src/test`는 카메라와 유튜브 프로세스 없이 다음을 확인합니다.

- 손 모양이 바뀌거나 신뢰도가 떨어지거나 공백이 길면 후보를 버립니다.
- 한 세션은 명령을 한 번만 고릅니다.
- 손바닥은 일시정지, 엄지는 재생입니다.
- 호출어 중복과 늦은 콜백은 무시합니다.
- 카메라 실패 뒤 바로 다시 열지 않습니다.
- 유튜브 뮤직 패키지는 선택하지 않습니다.
- 제한 시간은 테스트용 시계에서 일찍 실행되지 않고, 취소하면 실행되지 않습니다.
