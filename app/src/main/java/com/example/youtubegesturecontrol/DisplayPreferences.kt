package com.example.youtubegesturecontrol

import android.content.Context
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.os.LocaleListCompat

/**
 * 앱 안에서 고른 언어와 화면 모드입니다. 상태 기계와는 따로 저장합니다.
 */
object DisplayPreferences {
    private const val PREFS = "display_preferences"
    private const val KEY_NIGHT = "night_mode"

    private val languageTags: List<String?> = listOf(null, "ko", "en", "zh-CN", "ja")

    fun applyStoredNightMode(context: Context) {
        AppCompatDelegate.setDefaultNightMode(storedNightMode(context))
    }

    fun storedNightMode(context: Context): Int {
        val stored = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getInt(KEY_NIGHT, AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM)
        return sanitizeNightMode(stored)
    }

    fun setNightMode(context: Context, mode: Int) {
        val safe = sanitizeNightMode(mode)
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .putInt(KEY_NIGHT, safe)
            .apply()
        if (AppCompatDelegate.getDefaultNightMode() != safe) {
            AppCompatDelegate.setDefaultNightMode(safe)
        }
    }

    fun currentLanguageIndex(): Int {
        val locales = AppCompatDelegate.getApplicationLocales()
        if (locales.isEmpty) {
            return 0
        }
        val tag = locales.get(0)?.toLanguageTag().orEmpty()
        return when {
            tag.equals("ko", ignoreCase = true) || tag.startsWith("ko-", ignoreCase = true) -> 1
            tag.equals("en", ignoreCase = true) || tag.startsWith("en-", ignoreCase = true) -> 2
            tag.equals("zh-CN", ignoreCase = true) ||
                tag.equals("zh-Hans-CN", ignoreCase = true) ||
                tag.equals("zh-Hans", ignoreCase = true) ||
                tag.equals("zh", ignoreCase = true) -> 3
            tag.equals("ja", ignoreCase = true) || tag.startsWith("ja-", ignoreCase = true) -> 4
            else -> 0
        }
    }

    fun setLanguage(index: Int) {
        val tag = languageTags.getOrNull(index)
        val locales = if (tag == null) {
            LocaleListCompat.getEmptyLocaleList()
        } else {
            LocaleListCompat.forLanguageTags(tag)
        }
        if (sameLanguage(locales)) {
            return
        }
        AppCompatDelegate.setApplicationLocales(locales)
    }

    fun currentAppearanceIndex(context: Context): Int {
        return when (storedNightMode(context)) {
            AppCompatDelegate.MODE_NIGHT_NO -> 1
            AppCompatDelegate.MODE_NIGHT_YES -> 2
            else -> 0
        }
    }

    fun setAppearanceIndex(context: Context, index: Int) {
        val mode = when (index) {
            1 -> AppCompatDelegate.MODE_NIGHT_NO
            2 -> AppCompatDelegate.MODE_NIGHT_YES
            else -> AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM
        }
        setNightMode(context, mode)
    }

    fun languageChoiceLabels(context: Context): List<String> {
        return listOf(
            context.getString(R.string.language_system),
            "한국어",
            "English",
            "中文",
            "日本語",
        )
    }

    fun appearanceChoiceLabels(context: Context): List<String> {
        return listOf(
            context.getString(R.string.appearance_system),
            context.getString(R.string.appearance_light),
            context.getString(R.string.appearance_dark),
        )
    }

    private fun sameLanguage(next: LocaleListCompat): Boolean {
        val current = AppCompatDelegate.getApplicationLocales()
        if (next.isEmpty && current.isEmpty) {
            return true
        }
        return next.toLanguageTags() == current.toLanguageTags()
    }

    private fun sanitizeNightMode(mode: Int): Int {
        return when (mode) {
            AppCompatDelegate.MODE_NIGHT_YES,
            AppCompatDelegate.MODE_NIGHT_NO,
            AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM,
            -> mode
            else -> AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM
        }
    }
}
