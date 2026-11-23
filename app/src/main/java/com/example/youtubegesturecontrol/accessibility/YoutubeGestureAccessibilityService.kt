package com.example.youtubegesturecontrol.accessibility

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.app.KeyguardManager
import android.content.Intent
import android.graphics.Path
import android.graphics.Rect
import android.os.Build
import android.os.PowerManager
import android.view.WindowManager
import android.view.accessibility.AccessibilityEvent
import com.example.youtubegesturecontrol.media.PlaybackCommand
import com.example.youtubegesturecontrol.media.PlaybackRequestResult
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume

/**
 * 유튜브가 앞에 있을 때만 Shorts용 탭과 스와이프를 보냅니다.
 */
class YoutubeGestureAccessibilityService : AccessibilityService() {
    override fun onServiceConnected() {
        super.onServiceConnected()
        YoutubeGestureAccessibility.attach(this)
    }

    override fun onUnbind(intent: Intent?): Boolean {
        YoutubeGestureAccessibility.detach(this)
        return super.onUnbind(intent)
    }

    override fun onDestroy() {
        YoutubeGestureAccessibility.detach(this)
        super.onDestroy()
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) = Unit

    override fun onInterrupt() = Unit

    fun foregroundPackageName(): String? {
        val activeRoot = windows.firstOrNull { window -> window.isActive }?.root
        val root = activeRoot ?: rootInActiveWindow ?: return null
        return try {
            root.packageName?.toString()
        } finally {
            @Suppress("DEPRECATION")
            root.recycle()
        }
    }

    suspend fun perform(
        command: PlaybackCommand,
        mediaId: String?,
        title: String?,
        actions: Long,
    ): PlaybackRequestResult {
        val foreground = foregroundPackageName()
        if (!ShortsAccessibilityPolicy.isYouTubeForeground(foreground)) {
            ShortsAccessibilityFallback.logFallback(
                shell = true,
                actions = actions,
                mediaId = mediaId,
                title = title,
                command = command,
                why = "not-youtube",
                foregroundPackage = foreground,
            )
            return PlaybackRequestResult.AccessibilityNotYouTube(command, foreground)
        }
        if (!screenUnlocked()) {
            ShortsAccessibilityFallback.logFallback(
                shell = true,
                actions = actions,
                mediaId = mediaId,
                title = title,
                command = command,
                why = "screen-locked",
                foregroundPackage = foreground,
            )
            return PlaybackRequestResult.AccessibilityNotYouTube(command, foreground)
        }
        ShortsAccessibilityFallback.logFallback(
            shell = true,
            actions = actions,
            mediaId = mediaId,
            title = title,
            command = command,
            why = "shell",
        )
        val completed = dispatch(ShortsAccessibilityPolicy.motionFor(command))
        return if (completed) {
            PlaybackRequestResult.AccessibilityGestureCompleted(command)
        } else {
            ShortsAccessibilityFallback.logFallback(
                shell = true,
                actions = actions,
                mediaId = mediaId,
                title = title,
                command = command,
                why = "gesture-failed",
            )
            PlaybackRequestResult.AccessibilityGestureFailed(command)
        }
    }

    private suspend fun dispatch(motion: ShortsGestureMotion): Boolean {
        val (width, height) = displaySize()
        if (width <= 0f || height <= 0f) {
            return false
        }
        val gesture = gestureFor(motion, width, height)
        return suspendCancellableCoroutine { continuation ->
            val accepted = dispatchGesture(
                gesture,
                object : GestureResultCallback() {
                    override fun onCompleted(gestureDescription: GestureDescription?) {
                        if (continuation.isActive) {
                            continuation.resume(true)
                        }
                    }

                    override fun onCancelled(gestureDescription: GestureDescription?) {
                        if (continuation.isActive) {
                            continuation.resume(false)
                        }
                    }
                },
                null,
            )
            if (!accepted && continuation.isActive) {
                continuation.resume(false)
            }
        }
    }

    private fun screenUnlocked(): Boolean {
        val power = getSystemService(PowerManager::class.java) ?: return false
        val keyguard = getSystemService(KeyguardManager::class.java) ?: return false
        return ShortsAccessibilityPolicy.isScreenUnlocked(
            interactive = power.isInteractive,
            keyguardLocked = keyguard.isKeyguardLocked,
        )
    }

    private fun displaySize(): Pair<Float, Float> {
        val windowManager = getSystemService(WindowManager::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            val bounds: Rect = windowManager.currentWindowMetrics.bounds
            return bounds.width().toFloat() to bounds.height().toFloat()
        }
        @Suppress("DEPRECATION")
        val display = windowManager.defaultDisplay
        val metrics = android.util.DisplayMetrics()
        @Suppress("DEPRECATION")
        display.getRealMetrics(metrics)
        return metrics.widthPixels.toFloat() to metrics.heightPixels.toFloat()
    }

    companion object {
        fun gestureFor(motion: ShortsGestureMotion, width: Float, height: Float): GestureDescription {
            val path = Path()
            val duration = when (motion) {
                ShortsGestureMotion.TAP -> {
                    val x = width * ShortsGestureGeometry.CENTER_X
                    val y = height * ShortsGestureGeometry.TAP_Y
                    path.moveTo(x, y)
                    path.lineTo(x + 1f, y)
                    ShortsGestureGeometry.TAP_DURATION_MS
                }
                ShortsGestureMotion.SWIPE_UP -> {
                    path.moveTo(width * ShortsGestureGeometry.CENTER_X, height * ShortsGestureGeometry.SWIPE_BOTTOM_Y)
                    path.lineTo(width * ShortsGestureGeometry.CENTER_X, height * ShortsGestureGeometry.SWIPE_TOP_Y)
                    ShortsGestureGeometry.SWIPE_DURATION_MS
                }
                ShortsGestureMotion.SWIPE_DOWN -> {
                    path.moveTo(width * ShortsGestureGeometry.CENTER_X, height * ShortsGestureGeometry.SWIPE_TOP_Y)
                    path.lineTo(width * ShortsGestureGeometry.CENTER_X, height * ShortsGestureGeometry.SWIPE_BOTTOM_Y)
                    ShortsGestureGeometry.SWIPE_DURATION_MS
                }
            }
            val stroke = GestureDescription.StrokeDescription(path, 0L, duration)
            return GestureDescription.Builder().addStroke(stroke).build()
        }
    }
}
