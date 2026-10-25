package com.example.youtubegesturecontrol.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import com.example.youtubegesturecontrol.MainActivity
import com.example.youtubegesturecontrol.R
import com.example.youtubegesturecontrol.UserMessages
import com.example.youtubegesturecontrol.wakeword.WakeWordModels

/**
 * 제어가 돌아가는 동안 지워지지 않는 알림입니다.
 * 여기서 서비스를 중지할 수 있고, 알림을 누르면 앱 화면으로 돌아옵니다.
 */
class ControlNotifier(
    private val service: Service,
) {
    private val wakeWordBanner = WakeWordBanner(service)
    fun startForeground(label: String, detail: String) {
        ensureChannel()
        ServiceCompat.startForeground(
            service,
            NOTIFICATION_ID,
            build(label, detail),
            foregroundTypes(),
        )
    }

    fun update(label: String, detail: String) {
        val manager = service.getSystemService(NotificationManager::class.java)
        manager.notify(NOTIFICATION_ID, build(label, detail))
    }

    /**
     * 유튜브 화면 위에 호출어를 들었다는 표시를 잠깐 냅니다.
     * 다른 앱 위 그리기가 없으면 알림 배너로 대신합니다.
     */
    fun showWakeWordHeard() {
        ensureAlertChannel()
        if (!wakeWordBanner.show()) {
            val manager = service.getSystemService(NotificationManager::class.java)
            manager.notify(ALERT_NOTIFICATION_ID, buildWakeAlert())
        }
    }

    fun stop() {
        wakeWordBanner.hide()
        val manager = service.getSystemService(NotificationManager::class.java)
        manager.cancel(ALERT_NOTIFICATION_ID)
        ServiceCompat.stopForeground(service, ServiceCompat.STOP_FOREGROUND_REMOVE)
    }

    private fun build(label: String, detail: String): Notification {
        return NotificationCompat.Builder(service, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat_gesture)
            .setContentTitle(service.getString(R.string.app_title))
            .setContentText(label)
            .setStyle(NotificationCompat.BigTextStyle().bigText(detail))
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            // 안드로이드 14는 이 값을 주지 않으면 포그라운드 알림 표시를 늦출 수 있습니다.
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
            .setContentIntent(openAppIntent())
            .addAction(R.drawable.ic_stat_gesture, "제어 중지", stopIntent())
            .build()
    }

    private fun buildWakeAlert(): Notification {
        return NotificationCompat.Builder(service, ALERT_CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat_gesture)
            .setContentTitle(WakeWordModels.PHRASE)
            .setContentText(UserMessages.WAKE_WORD_HEARD)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_STATUS)
            .setAutoCancel(true)
            .setTimeoutAfter(ALERT_TIMEOUT_MS)
            .setContentIntent(openAppIntent())
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
            .build()
    }

    private fun ensureChannel() {
        val manager = service.getSystemService(NotificationManager::class.java)
        val channel = NotificationChannel(
            CHANNEL_ID,
            "유튜브 제스처 제어",
            NotificationManager.IMPORTANCE_LOW,
        )
        channel.description = "호출어 대기와 제스처 인식 상태를 보여 줍니다."
        manager.createNotificationChannel(channel)
    }

    private fun ensureAlertChannel() {
        val manager = service.getSystemService(NotificationManager::class.java)
        val channel = NotificationChannel(
            ALERT_CHANNEL_ID,
            "호출어 확인",
            NotificationManager.IMPORTANCE_HIGH,
        )
        channel.description = "호출어가 들리면 화면 위에 잠깐 알려 줍니다."
        channel.enableVibration(false)
        manager.createNotificationChannel(channel)
    }

    /**
     * 카메라를 나중에 열려면, 앱이 화면에 보이는 지금 카메라 유형까지 포함해 시작해야 합니다.
     * 유튜브가 앞에 있는 뒤 카메라 유형을 추가하면 시스템은 거부할 수 있습니다.
     * 유형을 선언해도 호출어를 기다리는 동안 CameraX를 바인드하지는 않습니다.
     */
    private fun foregroundTypes(): Int {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) {
            return 0
        }
        return ServiceInfo.FOREGROUND_SERVICE_TYPE_CAMERA or
            ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE
    }

    private fun stopIntent(): PendingIntent {
        val intent = Intent(service, ControlService::class.java).setAction(ControlService.ACTION_STOP)
        return PendingIntent.getService(
            service,
            REQUEST_STOP,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    private fun openAppIntent(): PendingIntent {
        val intent = Intent(service, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        return PendingIntent.getActivity(
            service,
            REQUEST_OPEN,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    companion object {
        private const val CHANNEL_ID = "gesture_control"
        private const val ALERT_CHANNEL_ID = "wake_word_heard"
        private const val NOTIFICATION_ID = 1001
        private const val ALERT_NOTIFICATION_ID = 1002
        private const val ALERT_TIMEOUT_MS = 2200L
        private const val REQUEST_STOP = 1
        private const val REQUEST_OPEN = 2
    }
}
