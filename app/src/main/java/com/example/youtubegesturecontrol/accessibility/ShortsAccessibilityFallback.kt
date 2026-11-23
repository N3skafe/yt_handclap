package com.example.youtubegesturecontrol.accessibility

import android.util.Log
import com.example.youtubegesturecontrol.media.PlaybackCommand
import com.example.youtubegesturecontrol.media.PlaybackRequestResult

/**
 * 쇼츠 껍데기에서만 접근성 탭·스와이프를 보냅니다.
 * 껍데기는 [performForShell]로 미디어 시도 전에 보내고, 미디어를 시도한 뒤에는
 * 동작 미지원이거나 확인 시간이 지난 껍데기만 [afterMediaResult]가 다시 보냅니다.
 * 일반 영상은 동작이 없어도 제스처를 보내지 않습니다.
 */
object ShortsAccessibilityFallback {
    suspend fun performForShell(
        command: PlaybackCommand,
        mediaId: String?,
        title: String?,
        actions: Long,
    ): PlaybackRequestResult {
        return perform(command, mediaId, title, actions)
    }

    suspend fun afterMediaResult(result: PlaybackRequestResult): PlaybackRequestResult {
        val fingerprint = fingerprintOf(result) ?: return result
        val actions = fingerprint.actions ?: return result
        val command = fingerprint.command
        if (!ShortsAccessibilityPolicy.isFallbackCandidate(result, fingerprint.mediaId, fingerprint.title, actions)) {
            logFallback(
                shell = false,
                actions = actions,
                mediaId = fingerprint.mediaId,
                title = fingerprint.title,
                command = command,
                why = "not-shell",
            )
            return result
        }
        return perform(command, fingerprint.mediaId, fingerprint.title, actions)
    }

    internal fun logFallback(
        shell: Boolean,
        actions: Long,
        mediaId: String?,
        title: String?,
        command: PlaybackCommand,
        why: String,
        foregroundPackage: String? = null,
    ) {
        val packageSuffix = if (foregroundPackage != null) " package=$foregroundPackage" else ""
        Log.i(
            TAG,
            "fallback shell=$shell actions=$actions hasMediaId=${!mediaId.isNullOrEmpty()} " +
                "hasTitle=${!title.isNullOrEmpty()} command=$command why=$why$packageSuffix",
        )
    }

    private suspend fun perform(
        command: PlaybackCommand,
        mediaId: String?,
        title: String?,
        actions: Long,
    ): PlaybackRequestResult {
        val service = YoutubeGestureAccessibility.connectedService()
        if (service == null) {
            logFallback(
                shell = true,
                actions = actions,
                mediaId = mediaId,
                title = title,
                command = command,
                why = "service-off",
            )
            return PlaybackRequestResult.AccessibilityServiceOff(command)
        }
        return service.perform(command, mediaId, title, actions)
    }

    private fun fingerprintOf(result: PlaybackRequestResult): SessionFingerprint? {
        return when (result) {
            is PlaybackRequestResult.ActionUnsupported -> SessionFingerprint(
                command = result.command,
                mediaId = result.mediaId,
                title = result.title,
                actions = result.actions,
            )
            is PlaybackRequestResult.ConfirmationTimedOut -> SessionFingerprint(
                command = result.command,
                mediaId = result.mediaId,
                title = result.title,
                actions = result.actions,
            )
            else -> null
        }
    }

    private data class SessionFingerprint(
        val command: PlaybackCommand,
        val mediaId: String?,
        val title: String?,
        val actions: Long?,
    )

    private const val TAG = "YoutubeController"
}
