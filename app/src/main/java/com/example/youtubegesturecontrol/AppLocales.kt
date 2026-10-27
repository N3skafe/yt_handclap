package com.example.youtubegesturecontrol

import android.content.Context
import android.content.res.Configuration
import android.os.LocaleList
import androidx.appcompat.app.AppCompatDelegate

/**
 * 서비스와 오버레이는 액티비티와 달리 앱 언어와 밤낮 설정을 자동으로 받지 못합니다.
 * 그 둘을 반영한 컨텍스트로 문장과 색을 읽습니다.
 */
object AppLocales {
    fun wrap(base: Context): Context {
        val locales = AppCompatDelegate.getApplicationLocales()
        val night = nightFlag()
        if (locales.isEmpty && night == null) {
            return base
        }
        val config = Configuration(base.resources.configuration)
        if (!locales.isEmpty) {
            config.setLocales(locales.unwrap() as LocaleList)
        }
        if (night != null) {
            config.uiMode = (config.uiMode and Configuration.UI_MODE_NIGHT_MASK.inv()) or night
        }
        return base.createConfigurationContext(config)
    }

    private fun nightFlag(): Int? {
        return when (AppCompatDelegate.getDefaultNightMode()) {
            AppCompatDelegate.MODE_NIGHT_YES -> Configuration.UI_MODE_NIGHT_YES
            AppCompatDelegate.MODE_NIGHT_NO -> Configuration.UI_MODE_NIGHT_NO
            else -> null
        }
    }
}
