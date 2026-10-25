package com.example.youtubegesturecontrol.wakeword

import android.content.Context
import com.example.youtubegesturecontrol.AssetFiles

/**
 * 호출어 모델 파일이 assets에 있는지 확인합니다.
 */
object WakeWordConfigLoader {
    fun status(context: Context): WakeWordStatus {
        val missing = WakeWordModels.requiredAssets.filter { path ->
            !AssetFiles.exists(context, path)
        }
        return WakeWordModels.status(missing)
    }
}
