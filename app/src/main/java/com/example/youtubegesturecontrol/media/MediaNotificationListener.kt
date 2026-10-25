package com.example.youtubegesturecontrol.media

import android.service.notification.NotificationListenerService
import android.util.Log

/**
 * 다른 앱의 미디어 세션 목록을 조회하려면 시스템이 이 구성요소를 요구합니다.
 * 알림 내용을 저장하거나 업로드하지 않습니다. 알림을 띄우는 권한과는 별개입니다.
 */
class MediaNotificationListener : NotificationListenerService() {
    override fun onListenerConnected() {
        super.onListenerConnected()
        Log.i(TAG, "알림 접근이 연결되었습니다. 유튜브 미디어 세션을 찾는 데만 사용합니다.")
    }

    companion object {
        private const val TAG = "MediaNotification"
    }
}
