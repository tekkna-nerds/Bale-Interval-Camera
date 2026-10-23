package com.example.baleintervalcamera

import android.app.Activity
import android.content.Context
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.view.View
import android.view.WindowManager

internal data class UiPalette(
    val background: Int,
    val surface: Int,
    val raisedSurface: Int,
    val text: Int,
    val muted: Int,
    val accent: Int,
    val accentText: Int,
    val border: Int,
    val good: Int,
    val warning: Int
)

internal object UiTheme {
    private const val KEY_DARK_MODE = "dark_mode"

    fun isDark(context: Context): Boolean =
        context.getSharedPreferences(AppSettings.PREFERENCES, Context.MODE_PRIVATE).getBoolean(KEY_DARK_MODE, true)

    fun setDark(context: Context, enabled: Boolean) {
        context.getSharedPreferences(AppSettings.PREFERENCES, Context.MODE_PRIVATE).edit()
            .putBoolean(KEY_DARK_MODE, enabled)
            .apply()
    }

    fun platformTheme(context: Context): Int = if (isDark(context)) {
        android.R.style.Theme_Material_NoActionBar
    } else {
        android.R.style.Theme_Material_Light_NoActionBar
    }

    fun palette(context: Context): UiPalette = if (isDark(context)) dark else light

    fun applySystemBars(activity: Activity) {
        val p = palette(activity)
        activity.window.statusBarColor = p.background
        activity.window.navigationBarColor = p.background
        activity.window.decorView.layoutDirection = AppSettings.direction(activity)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            activity.window.isStatusBarContrastEnforced = false
            activity.window.isNavigationBarContrastEnforced = false
        }
        @Suppress("DEPRECATION")
        activity.window.decorView.systemUiVisibility = if (isDark(activity)) 0 else
            (View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR or View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR)
        activity.window.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE)
    }

    fun rounded(color: Int, radiusDp: Float, stroke: Int? = null, strokeDp: Int = 1): GradientDrawable =
        GradientDrawable().apply {
            setColor(color)
            cornerRadius = radiusDp
            if (stroke != null) setStroke(strokeDp, stroke)
        }

    fun dp(context: Context, value: Int): Int =
        (value * context.resources.displayMetrics.density).toInt()

    private val dark = UiPalette(
        background = Color.rgb(7, 20, 40),
        surface = Color.rgb(15, 34, 59),
        raisedSurface = Color.rgb(22, 45, 73),
        text = Color.rgb(239, 247, 255),
        muted = Color.rgb(164, 184, 204),
        accent = Color.rgb(47, 197, 215),
        accentText = Color.rgb(4, 25, 43),
        border = Color.rgb(37, 65, 95),
        good = Color.rgb(144, 225, 100),
        warning = Color.rgb(255, 199, 112)
    )

    private val light = UiPalette(
        background = Color.rgb(242, 247, 250),
        surface = Color.rgb(255, 255, 255),
        raisedSurface = Color.rgb(232, 243, 247),
        text = Color.rgb(16, 39, 59),
        muted = Color.rgb(91, 112, 131),
        accent = Color.rgb(20, 135, 158),
        accentText = Color.WHITE,
        border = Color.rgb(213, 227, 234),
        good = Color.rgb(50, 132, 71),
        warning = Color.rgb(169, 107, 17)
    )
}
