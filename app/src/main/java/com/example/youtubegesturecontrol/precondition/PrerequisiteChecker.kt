package com.example.youtubegesturecontrol.precondition

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.example.youtubegesturecontrol.AssetFiles
import com.example.youtubegesturecontrol.R
import com.example.youtubegesturecontrol.UserNotice
import com.example.youtubegesturecontrol.gesture.GestureAnalyzer
import com.example.youtubegesturecontrol.media.YoutubeSessionSelector
import com.example.youtubegesturecontrol.wakeword.WakeWordConfigLoader
import com.example.youtubegesturecontrol.wakeword.WakeWordModels
import com.example.youtubegesturecontrol.wakeword.WakeWordStatus
import com.example.youtubegesturecontrol.withBreakableTokens

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

    fun blockingProblem(): UserNotice? {
        if (!modelInstalled()) {
            return UserNotice.ModelMissing(GestureAnalyzer.MODEL_ASSET_PATH)
        }
        if (!isGranted(Manifest.permission.CAMERA)) {
            return UserNotice.CameraPermissionMissing
        }
        if (!isGranted(Manifest.permission.RECORD_AUDIO)) {
            return UserNotice.MicrophonePermissionMissing
        }
        if (!listenerEnabled()) {
            return UserNotice.NotificationListenerOff
        }
        val wakeWord = WakeWordConfigLoader.status(context)
        if (!wakeWord.configured) {
            return UserNotice.WakeWordModelMissing(wakeWord.missingAssets.joinToString(", "))
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
        val phrase = if (report.wakeWord.configured) {
            WakeWordModels.PHRASE
        } else {
            context.getString(R.string.wake_word_unconfigured)
        }
        val detail = if (report.wakeWord.configured) {
            context.getString(R.string.wake_word_detail, WakeWordModels.PHRASE)
        } else {
            context.getString(R.string.wake_word_missing, report.wakeWord.missingAssets.joinToString(", "))
        }
        return buildString {
            appendLine(permissionLine(context.getString(R.string.perm_camera), report.cameraGranted))
            appendLine(permissionLine(context.getString(R.string.perm_microphone), report.microphoneGranted))
            if (report.notificationPermissionRequired) {
                appendLine(permissionLine(context.getString(R.string.perm_notifications), report.notificationsGranted))
                appendLine(context.getString(R.string.notifications_difference))
            } else {
                appendLine(context.getString(R.string.notifications_not_required))
            }
            appendLine(
                if (report.listenerEnabled) {
                    context.getString(R.string.listener_on)
                } else {
                    context.getString(R.string.listener_off)
                },
            )
            appendLine(
                if (report.modelInstalled) {
                    context.getString(R.string.model_present)
                } else {
                    context.getString(R.string.model_absent)
                },
            )
            appendLine(
                if (report.youtubeInstalled) {
                    context.getString(R.string.youtube_installed)
                } else {
                    context.getString(R.string.youtube_missing)
                },
            )
            append(context.getString(R.string.wake_word_line, phrase, detail))
            if (report.permanentlyDenied.isNotEmpty()) {
                append("\n")
                append(
                    context.getString(
                        R.string.permanently_denied_line,
                        report.permanentlyDenied.joinToString(", ") { permissionLabel(it) },
                    ),
                )
            }
        }.withBreakableTokens()
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
        val resId = if (granted) R.string.perm_granted else R.string.perm_denied
        return context.getString(resId, name)
    }

    private fun permissionLabel(permission: String): String {
        val resId = when (permission) {
            Manifest.permission.CAMERA -> R.string.perm_camera
            Manifest.permission.RECORD_AUDIO -> R.string.perm_microphone
            Manifest.permission.POST_NOTIFICATIONS -> R.string.perm_notifications
            else -> return permission
        }
        return context.getString(resId)
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
