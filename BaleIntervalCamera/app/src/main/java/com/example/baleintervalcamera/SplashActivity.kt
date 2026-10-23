package com.example.baleintervalcamera

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView

class SplashActivity : Activity() {
    private val handler = android.os.Handler(android.os.Looper.getMainLooper())
    private val continueToApp = Runnable {
        startActivity(Intent(this, MainActivity::class.java))
        finish()
    }

    private fun t(persian: String, english: String) = AppSettings.text(this, persian, english)

    override fun onCreate(savedInstanceState: Bundle?) {
        setTheme(UiTheme.platformTheme(this))
        super.onCreate(savedInstanceState)
        UiTheme.applySystemBars(this)
        showSplash()
        handler.postDelayed(continueToApp, 1_200L)
    }

    private fun showSplash() {
        val density = resources.displayMetrics.density
        fun dp(value: Int) = (value * density).toInt()
        val palette = UiTheme.palette(this)

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            layoutDirection = AppSettings.direction(this@SplashActivity)
            setPadding(dp(24), dp(28), dp(24), dp(14))
            setBackgroundColor(palette.background)
        }

        val top = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            layoutDirection = AppSettings.direction(this@SplashActivity)
        }
        val appMark = ImageView(this).apply {
            setImageResource(R.drawable.ic_launcher_foreground)
            contentDescription = t("نشان دوربین بله", "Bale camera icon")
            scaleType = ImageView.ScaleType.FIT_CENTER
        }
        top.addView(appMark, LinearLayout.LayoutParams(dp(94), dp(94)))
        top.addView(TextView(this).apply {
            text = t("دوربین بله", "Bale Camera")
            textSize = 25f
            setTextColor(palette.text)
            typeface = android.graphics.Typeface.create("sans-serif", android.graphics.Typeface.BOLD)
            gravity = Gravity.CENTER
            setPadding(0, dp(12), 0, dp(4))
        }, fullWidth())
        top.addView(TextView(this).apply {
            text = t("ارسال تصویر با تأیید شما", "Photo delivery with your consent")
            textSize = 14f
            setTextColor(palette.muted)
            gravity = Gravity.CENTER
        }, fullWidth())
        root.addView(top, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f))

        val loading = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            layoutDirection = AppSettings.direction(this@SplashActivity)
        }
        val progress = ProgressBar(this).apply {
            isIndeterminate = true
            indeterminateTintList = android.content.res.ColorStateList.valueOf(palette.accent)
        }
        loading.addView(progress, LinearLayout.LayoutParams(dp(30), dp(30)))
        loading.addView(TextView(this).apply {
            text = t("در حال آماده‌سازی…", "Getting ready…")
            textSize = 12f
            setTextColor(palette.muted)
            gravity = Gravity.CENTER
            setPadding(0, dp(8), 0, 0)
        }, fullWidth())
        root.addView(loading, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 0.55f))

        val footer = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
            layoutDirection = AppSettings.direction(this@SplashActivity)
        }
        val logo = ImageView(this).apply {
            setImageResource(R.drawable.tekkna_logo)
            contentDescription = t("نشان شرکت تکنا", "Tekkna logo")
            scaleType = ImageView.ScaleType.FIT_CENTER
        }
        footer.addView(logo, LinearLayout.LayoutParams(dp(52), dp(52)))
        footer.addView(TextView(this).apply {
            text = t("تمام حقوق برای شرکت تکنا محفوظ است", "All rights reserved to Tekkna")
            textSize = 11f
            setTextColor(palette.muted)
            gravity = Gravity.CENTER_VERTICAL or Gravity.START
            textAlignment = View.TEXT_ALIGNMENT_VIEW_START
        }, LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
            marginStart = dp(10)
        })
        root.addView(footer, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        setContentView(root)
    }

    override fun onDestroy() {
        handler.removeCallbacks(continueToApp)
        super.onDestroy()
    }

    private fun fullWidth() = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
}
