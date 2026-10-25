package com.example.youtubegesturecontrol

import android.content.Context
import java.io.File
import java.io.IOException

/**
 * assets 또는 절대 경로에 파일이 있는지만 확인합니다. 모델 내용을 읽어서 저장하지는 않습니다.
 */
object AssetFiles {
    fun exists(context: Context, path: String): Boolean {
        if (path.isBlank()) {
            return false
        }
        return try {
            if (path.startsWith("/")) {
                File(path).isFile
            } else {
                context.assets.open(path).close()
                true
            }
        } catch (error: IOException) {
            false
        }
    }
}
