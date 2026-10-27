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
import com.example.youtubegesturecontrol.AppLocales
import com.example.youtubegesturecontrol.MainActivity
import com.example.youtubegesturecontrol.R
import com.example.youtubegesturecontrol.UserNotice
import com.example.youtubegesturecontrol.controlStateLabel
import com.example.youtubegesturecontrol.noticeText
import com.example.youtubegesturecontrol.wakeword.WakeWordModels

/**
 * 제어가 돌아가는 동안 지워지지 않는 알림입니다.
 * 여기서 서비스를 중지할 수 있고, 알림을 누르면 앱 화면으로 돌아옵니다.
 */
class ControlNotifier(
    private val service: Service,
) {
    private val wakeWordBanner = WakeWordBanner(service)

    fun startForeground(state: ControlState, notice: UserNotice) {
        ensureChannels()
        ServiceCompat.startForeground(
            service,
            NOTIFICATION_ID,
            build(state, notice),
            foregroundTypes(),
        )
    }

    fun update(state: ControlState, notice: UserNotice) {
        ensureChannels()
        val manager = service.getSystemService(NotificationManager::class.java)
        manager.notify(NOTIFICATION_ID, build(state, notice))
    }

    /**
     * 유튜브 화면 위에 호출어를 들었다는 표시를 잠깐 냅니다.
     * 다른 앱 위 그리기가 없으면 알림 배너로 대신합니다.
     */
    fun showWakeWordHeard() {
        ensureChannels()
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

    private fun build(state: ControlState, notice: UserNotice): Notification {
        val text = AppLocales.wrap(service)
        return NotificationCompat.Builder(service, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat_gesture)
            .setContentTitle(text.getString(R.string.app_title))
            .setContentText(text.controlStateLabel(state))
            .setStyle(NotificationCompat.BigTextStyle().bigText(text.noticeText(notice)))
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            // 안드로이드 14는 이 값을 주지 않으면 포그라운드 알림 표시를 늦출 수 있습니다.
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
            .setContentIntent(openAppIntent())
            .addAction(R.drawable.ic_stat_gesture, text.getString(R.string.stop_control), stopIntent())
            .build()
    }

    private fun buildWakeAlert(): Notification {
        val text = AppLocales.wrap(service)
        return NotificationCompat.Builder(service, ALERT_CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat_gesture)
            .setContentTitle(WakeWordModels.PHRASE)
            .setContentText(text.noticeText(UserNotice.WakeWordHeard))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_STATUS)
            .setAutoCancel(true)
            .setTimeoutAfter(ALERT_TIMEOUT_MS)
            .setContentIntent(openAppIntent())
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
            .build()
    }

    private fun ensureChannels() {
        val text = AppLocales.wrap(service)
        val manager = service.getSystemService(NotificationManager::class.java)
        val status = NotificationChannel(
            CHANNEL_ID,
            text.getString(R.string.app_title),
            NotificationManager.IMPORTANCE_LOW,
        )
        status.description = text.getString(R.string.channel_control_description)
        manager.createNotificationChannel(status)
        val alert = NotificationChannel(
            ALERT_CHANNEL_ID,
            text.getString(R.string.channel_alert_name),
            NotificationManager.IMPORTANCE_HIGH,
        )
        alert.description = text.getString(R.string.channel_alert_description)
        alert.enableVibration(false)
        manager.createNotificationChannel(alert)
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
