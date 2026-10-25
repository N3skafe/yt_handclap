package com.example.youtubegesturecontrol.precondition

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.example.youtubegesturecontrol.AssetFiles
import com.example.youtubegesturecontrol.gesture.GestureAnalyzer
import com.example.youtubegesturecontrol.media.YoutubeSessionSelector
import com.example.youtubegesturecontrol.wakeword.WakeWordConfigLoader
import com.example.youtubegesturecontrol.wakeword.WakeWordStatus

/**
 * 제어를 시작하기 전에 권한과 설정을 다시 확인합니다.
 * 알림을 띄우는 권한과, 다른 앱의 미디어 세션을 보기 위한 알림 접근은 서로 다릅니다.
 */
class PrerequisiteChecker(
    private val context: Context,
) {
    fun report(activity: Activity, askedPermissions: Set<String>): PrerequisiteReport {
        val permanent = missingRuntimePermissions()
            .filter { permission -> isPermanentlyDenied(activity, permission, askedPermissions) }
        return PrerequisiteReport(
            cameraGranted = isGranted(Manifest.permission.CAMERA),
            microphoneGranted = isGranted(Manifest.permission.RECORD_AUDIO),
            notificationsGranted = !notificationPermissionRequired() ||
                isGranted(Manifest.permission.POST_NOTIFICATIONS),
            notificationPermissionRequired = notificationPermissionRequired(),
            listenerEnabled = listenerEnabled(),
            modelInstalled = modelInstalled(),
            youtubeInstalled = youtubeInstalled(),
            wakeWord = WakeWordConfigLoader.status(context),
            permanentlyDenied = permanent,
        )
    }

    fun blockingProblem(): String? {
        if (!modelInstalled()) {
            return "제스처 모델 파일이 없습니다. assets/${GestureAnalyzer.MODEL_ASSET_PATH} 를 확인하세요."
        }
        if (!isGranted(Manifest.permission.CAMERA)) {
            return "카메라 권한이 없습니다."
        }
        if (!isGranted(Manifest.permission.RECORD_AUDIO)) {
            return "마이크 권한이 없습니다."
        }
        if (!listenerEnabled()) {
            return "알림 접근이 꺼져 있어 유튜브 세션을 볼 수 없습니다."
        }
        val wakeWord = WakeWordConfigLoader.status(context)
        if (!wakeWord.configured) {
            return wakeWord.detail
        }
        return null
    }

    fun missingRuntimePermissions(): Array<String> {
        val required = mutableListOf<String>()
        if (!isGranted(Manifest.permission.CAMERA)) {
            required.add(Manifest.permission.CAMERA)
        }
        if (!isGranted(Manifest.permission.RECORD_AUDIO)) {
            required.add(Manifest.permission.RECORD_AUDIO)
        }
        if (notificationPermissionRequired() && !isGranted(Manifest.permission.POST_NOTIFICATIONS)) {
            required.add(Manifest.permission.POST_NOTIFICATIONS)
        }
        return required.toTypedArray()
    }

    fun describe(report: PrerequisiteReport): String {
        return buildString {
            appendLine(permissionLine("카메라", report.cameraGranted))
            appendLine(permissionLine("마이크", report.microphoneGranted))
            if (report.notificationPermissionRequired) {
                appendLine(permissionLine("알림 게시", report.notificationsGranted))
                appendLine("알림 게시는 우리 상태 알림용입니다. 알림 접근과는 다릅니다.")
            } else {
                appendLine("알림 게시: 이 버전에서는 별도의 런타임 요청이 없습니다.")
            }
            appendLine(if (report.listenerEnabled) "알림 접근: 허용됨" else "알림 접근: 꺼짐")
            appendLine(
                if (report.modelInstalled) {
                    "제스처 모델: 있음"
                } else {
                    "제스처 모델: 없음"
                },
            )
            appendLine(
                if (report.youtubeInstalled) {
                    "유튜브 앱: 설치됨"
                } else {
                    "유튜브 앱: 설치를 확인하지 못함"
                },
            )
            append("호출어: ${report.wakeWord.phraseLabel}. ${report.wakeWord.detail}")
            if (report.permanentlyDenied.isNotEmpty()) {
                append("\n다시 묻지 않도록 거부됨: ")
                append(report.permanentlyDenied.joinToString(", ") { permissionLabel(it) })
                append(". 앱 설정에서 허용해야 합니다.")
            }
        }
    }

    private fun isPermanentlyDenied(
        activity: Activity,
        permission: String,
        askedPermissions: Set<String>,
    ): Boolean {
        if (isGranted(permission)) {
            return false
        }
        val canAskAgain = activity.shouldShowRequestPermissionRationale(permission)
        return askedPermissions.contains(permission) && !canAskAgain
    }

    private fun listenerEnabled(): Boolean {
        return NotificationManagerCompat.getEnabledListenerPackages(context).contains(context.packageName)
    }

    private fun modelInstalled(): Boolean {
        return AssetFiles.exists(context, GestureAnalyzer.MODEL_ASSET_PATH)
    }

    private fun youtubeInstalled(): Boolean {
        return try {
            context.packageManager.getPackageInfo(YoutubeSessionSelector.YOUTUBE_PACKAGE_NAME, 0)
            true
        } catch (error: PackageManager.NameNotFoundException) {
            false
        }
    }

    private fun notificationPermissionRequired(): Boolean {
        return Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU
    }

    private fun isGranted(permission: String): Boolean {
        return ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED
    }

    private fun permissionLine(name: String, granted: Boolean): String {
        return if (granted) "$name: 허용됨" else "$name: 아직 허용되지 않음"
    }

    private fun permissionLabel(permission: String): String {
        return when (permission) {
            Manifest.permission.CAMERA -> "카메라"
            Manifest.permission.RECORD_AUDIO -> "마이크"
            Manifest.permission.POST_NOTIFICATIONS -> "알림 게시"
            else -> permission
        }
    }
}

data class PrerequisiteReport(
    val cameraGranted: Boolean,
    val microphoneGranted: Boolean,
    val notificationsGranted: Boolean,
    val notificationPermissionRequired: Boolean,
    val listenerEnabled: Boolean,
    val modelInstalled: Boolean,
    val youtubeInstalled: Boolean,
    val wakeWord: WakeWordStatus,
    val permanentlyDenied: List<String>,
) {
    fun canStartWakeWordControl(): Boolean {
        return cameraGranted &&
            microphoneGranted &&
            listenerEnabled &&
            modelInstalled &&
            wakeWord.configured
    }
}
