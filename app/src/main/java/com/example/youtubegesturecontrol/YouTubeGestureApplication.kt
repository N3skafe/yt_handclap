package com.example.youtubegesturecontrol

import android.app.Application
import com.example.youtubegesturecontrol.service.ControlStatusRepository

/**
 * 프로세스 동안 살아 있는 상태 저장소입니다.
 * Activity를 여기에 넣지 않습니다. 화면이 파괴돼도 서비스 상태는 남아야 합니다.
 */
class YouTubeGestureApplication : Application() {
    val statusRepository: ControlStatusRepository = ControlStatusRepository()
}
