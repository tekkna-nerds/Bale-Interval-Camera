package com.example.baleintervalcamera

import android.content.Context
import android.view.View

internal object AppSettings {
    const val PREFERENCES = "ui_preferences"
    const val KEY_LANGUAGE = "language"
    const val KEY_INTERVAL_MILLIS = "interval_millis"
    const val PERSIAN = "fa"
    const val ENGLISH = "en"
    const val DEFAULT_INTERVAL_MILLIS = 3_000L
    val INTERVAL_OPTIONS = longArrayOf(3_000L, 5_000L, 10_000L, 15_000L, 30_000L, 60_000L)

    fun isPersian(context: Context): Boolean = context.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE)
        .getString(KEY_LANGUAGE, PERSIAN) != ENGLISH

    fun text(context: Context, persian: String, english: String): String =
        if (isPersian(context)) persian else english

    fun direction(context: Context): Int =
        if (isPersian(context)) View.LAYOUT_DIRECTION_RTL else View.LAYOUT_DIRECTION_LTR

    fun intervalMillis(context: Context): Long {
        val saved = context.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE)
            .getLong(KEY_INTERVAL_MILLIS, DEFAULT_INTERVAL_MILLIS)
        return saved.takeIf { it in INTERVAL_OPTIONS } ?: DEFAULT_INTERVAL_MILLIS
    }

    fun intervalLabel(context: Context, intervalMillis: Long): String {
        val seconds = (intervalMillis / 1_000L).toInt()
        return if (isPersian(context)) {
            if (seconds == 60) "هر ۱ دقیقه (یک تصویر در دقیقه)" else "هر ${persianDigits(seconds)} ثانیه"
        } else {
            if (seconds == 60) "Once per minute (60 seconds)" else "Every $seconds seconds"
        }
    }

    fun intervalPhrase(context: Context, intervalMillis: Long): String {
        val seconds = (intervalMillis / 1_000L).toInt()
        return if (isPersian(context)) {
            if (seconds == 60) "هر دقیقه" else "هر ${persianDigits(seconds)} ثانیه"
        } else {
            if (seconds == 60) "once per minute" else "every $seconds seconds"
        }
    }

    fun persianDigits(number: Int): String = number.toString().map { digit ->
        if (digit in '0'..'9') ('۰'.code + (digit - '0')).toChar() else digit
    }.joinToString("")
}
