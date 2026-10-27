package com.example.youtubegesturecontrol.service

import android.content.Context
import android.graphics.PixelFormat
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.provider.Settings
import android.util.Log
import android.view.Gravity
import android.view.LayoutInflater
import android.view.WindowManager
import android.widget.TextView
import androidx.appcompat.view.ContextThemeWrapper
import com.example.youtubegesturecontrol.AppLocales
import com.example.youtubegesturecontrol.R
import com.example.youtubegesturecontrol.wakeword.WakeWordModels

/**
 * 유튜브가 앞에 있어도 호출어를 들었다는 것을 화면 위에 잠깐 보여 줍니다.
 * 다른 앱 위에 그리는 권한이 있을 때만 창을 붙입니다. 터치 입력은 받지 않습니다.
 */
class WakeWordBanner(
    context: Context,
) {
    private val appContext = context.applicationContext
    private val mainHandler = Handler(Looper.getMainLooper())
    private val hideRunnable = Runnable { removeView() }
    private var bannerView: android.view.View? = null

    fun canDrawOverApps(): Boolean = Settings.canDrawOverlays(appContext)

    fun show(): Boolean {
        vibrateBriefly()
        if (!canDrawOverApps()) {
            return false
        }
        return try {
            val windowManager = appContext.getSystemService(WindowManager::class.java)
            val view = inflate().also { bannerView = it }
            view.findViewById<TextView>(R.id.wakeBannerTitle).text = WakeWordModels.PHRASE
            view.findViewById<TextView>(R.id.wakeBannerDetail).text =
                view.context.getString(R.string.notice_wake_heard)
            if (view.parent == null) {
                windowManager.addView(view, layoutParams())
            }
            mainHandler.removeCallbacks(hideRunnable)
            mainHandler.postDelayed(hideRunnable, VISIBLE_MS)
            true
        } catch (error: Exception) {
            Log.w(TAG, "호출어 배너를 띄우지 못했습니다.", error)
            removeView()
            false
        }
    }

    fun hide() {
        mainHandler.removeCallbacks(hideRunnable)
        removeView()
    }

    private fun inflate(): android.view.View {
        removeView()
        val themed = ContextThemeWrapper(AppLocales.wrap(appContext), R.style.Theme_YouTubeGestureControl)
        return LayoutInflater.from(themed).inflate(R.layout.view_wake_banner, null)
    }

    private fun layoutParams(): WindowManager.LayoutParams {
        val type = WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        val flags = WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
            WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
            WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN
        return WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            type,
            flags,
            PixelFormat.TRANSLUCENT,
        ).apply {
            gravity = Gravity.TOP or Gravity.CENTER_HORIZONTAL
            y = (appContext.resources.displayMetrics.density * TOP_OFFSET_DP).toInt()
        }
    }

    private fun removeView() {
        val view = bannerView ?: return
        bannerView = null
        if (view.parent == null) {
            return
        }
        try {
            appContext.getSystemService(WindowManager::class.java).removeView(view)
        } catch (error: Exception) {
            Log.w(TAG, "호출어 배너를 내리지 못했습니다.", error)
        }
    }

    private fun vibrateBriefly() {
        val vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            appContext.getSystemService(VibratorManager::class.java)?.defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            appContext.getSystemService(Vibrator::class.java)
        } ?: return
        if (!vibrator.hasVibrator()) {
            return
        }
        vibrator.vibrate(VibrationEffect.createOneShot(VIBRATION_MS, VibrationEffect.DEFAULT_AMPLITUDE))
    }

    companion object {
        private const val TAG = "WakeWordBanner"
        private const val VISIBLE_MS = 2200L
        private const val VIBRATION_MS = 80L
        private const val TOP_OFFSET_DP = 72
    }
}
