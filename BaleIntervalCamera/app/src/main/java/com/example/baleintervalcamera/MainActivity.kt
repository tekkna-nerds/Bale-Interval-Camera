package com.example.baleintervalcamera

import android.Manifest
import android.app.AlertDialog
import android.app.Activity
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.content.res.ColorStateList
import android.graphics.Paint
import android.graphics.Typeface
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.RippleDrawable
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.CheckBox
import android.widget.FrameLayout
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.PopupWindow
import android.widget.RadioButton
import android.widget.RadioGroup
import android.widget.ScrollView
import android.widget.Switch
import android.widget.TextView
import android.widget.Toast
import androidx.core.content.ContextCompat
import org.json.JSONObject
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.util.UUID

class MainActivity : Activity() {
    private lateinit var consentCheck: CheckBox
    private lateinit var startButton: Button
    private lateinit var stopButton: Button
    private lateinit var statusText: TextView
    private lateinit var statusHeadline: TextView
    private lateinit var lastSessionCountText: TextView
    private lateinit var botIdText: TextView
    private lateinit var botLinkText: TextView
    private lateinit var recipientStatusText: TextView
    private lateinit var checkConnectionButton: Button
    private lateinit var pageContainer: FrameLayout
    private lateinit var homePage: View
    private lateinit var settingsPage: View
    private lateinit var homeTabButton: TextView
    private lateinit var settingsTabButton: TextView
    private lateinit var palette: UiPalette
    private var botUri: Uri? = null
    private var waitingForPermissions = false
    private var checkingBaleConnection = false
    private var selectedTab = TAB_HOME
    private var sessionCountReceiverRegistered = false
    private val sessionCountReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            refreshLastSessionCount()
        }
    }

    private fun t(persian: String, english: String) = AppSettings.text(this, persian, english)

    override fun onCreate(savedInstanceState: Bundle?) {
        setTheme(UiTheme.platformTheme(this))
        super.onCreate(savedInstanceState)
        UiTheme.applySystemBars(this)
        palette = UiTheme.palette(this)
        buildScreen(savedInstanceState)
    }

    override fun onResume() {
        super.onResume()
        if (::statusText.isInitialized) {
            refreshSessionState()
            if (!checkingBaleConnection && pendingPairingNonce().isNotBlank()) {
                checkBaleConnection(showProgress = false)
            }
            refreshRecipientStatus()
        }
    }

    override fun onStart() {
        super.onStart()
        ContextCompat.registerReceiver(
            this,
            sessionCountReceiver,
            IntentFilter(CameraCaptureService.ACTION_SESSION_COUNT_UPDATED),
            ContextCompat.RECEIVER_NOT_EXPORTED
        )
        sessionCountReceiverRegistered = true
    }

    private fun buildScreen(savedInstanceState: Bundle?) {
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutDirection = AppSettings.direction(this@MainActivity)
            setBackgroundColor(palette.background)
        }

        pageContainer = FrameLayout(this).apply { setBackgroundColor(palette.background) }
        homePage = buildHomePage()
        settingsPage = buildSettingsPage().apply { visibility = View.GONE }
        pageContainer.addView(homePage, FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.MATCH_PARENT
        ))
        pageContainer.addView(settingsPage, FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.MATCH_PARENT
        ))
        root.addView(pageContainer, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f))
        root.addView(buildBottomNavigation(), fullWidth())
        setContentView(root)

        consentCheck.isChecked = savedInstanceState?.getBoolean(STATE_CONSENT) ?: false
        switchToTab(savedInstanceState?.getInt(STATE_SELECTED_TAB, TAB_HOME) ?: TAB_HOME)
        refreshSessionState()
        loadBotProfile()
    }

    private fun buildHomePage(): View {
        val content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutDirection = AppSettings.direction(this@MainActivity)
            setPadding(dp(22), dp(18), dp(22), dp(24))
            setBackgroundColor(palette.background)
        }
        content.addView(buildHeader(), marginParams(bottom = 20))
        content.addView(buildStatusCard(), marginParams(bottom = 16))
        content.addView(buildBotCard(), marginParams(bottom = 16))
        content.addView(buildRecipientCard(), marginParams(bottom = 16))
        content.addView(buildConsentCard(), marginParams(bottom = 18))

        startButton = makeButton(t("شروع تصویربرداری", "Start capture"), primary = true).apply {
            setOnClickListener { beginPermissionFlow() }
        }
        content.addView(startButton, marginParams(bottom = 9))

        stopButton = makeButton(t("توقف تصویربرداری", "Stop capture"), primary = false).apply {
            setOnClickListener {
                startService(Intent(this@MainActivity, CameraCaptureService::class.java).apply {
                    action = CameraCaptureService.ACTION_STOP
                })
                statusHeadline.text = t("در حال توقف", "Stopping")
                statusText.text = t("درخواست توقف ارسال شد.", "Stop request sent.")
                refreshButtons(running = true)
                statusText.postDelayed({ refreshSessionState() }, 300)
            }
        }
        content.addView(stopButton, marginParams(bottom = 22))

        content.addView(TextView(this).apply {
            text = t(
                "برای حفظ حریم خصوصی، فقط با رضایت افراد و مجوزهای لازم از برنامه استفاده کنید.",
                "For privacy, use the app only with the people’s consent and required permissions."
            )
            textSize = 12f
            setTextColor(palette.muted)
            gravity = Gravity.CENTER
            setLineSpacing(dp(2).toFloat(), 1f)
            textDirection = if (AppSettings.isPersian(this@MainActivity)) View.TEXT_DIRECTION_FIRST_STRONG_RTL else View.TEXT_DIRECTION_FIRST_STRONG_LTR
        }, fullWidth())

        val scroll = ScrollView(this).apply {
            isFillViewport = true
            clipToPadding = false
            addView(content)
        }
        return scroll
    }

    private fun buildHeader(): View {
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            layoutDirection = AppSettings.direction(this@MainActivity)
        }

        val menuButton = ImageButton(this).apply {
            setImageResource(R.drawable.ic_menu)
            imageTintList = ColorStateList.valueOf(palette.text)
            contentDescription = t("باز کردن تنظیمات", "Open settings")
            background = UiTheme.rounded(palette.surface, dp(14).toFloat(), palette.border, dp(1))
            setPadding(dp(12), dp(12), dp(12), dp(12))
            setOnClickListener { showAppMenu(this) }
        }
        row.addView(menuButton, LinearLayout.LayoutParams(dp(48), dp(48)))

        val brand = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutDirection = AppSettings.direction(this@MainActivity)
            gravity = if (AppSettings.isPersian(this@MainActivity)) Gravity.RIGHT else Gravity.LEFT
        }
        brand.addView(TextView(this).apply {
            text = "تکنا"
            textSize = 25f
            typeface = Typeface.create("sans-serif", Typeface.BOLD)
            setTextColor(palette.text)
        }, fullWidth())
        brand.addView(TextView(this).apply {
            text = t("مدیریت ارسال تصویر به بله", "Bale photo delivery")
            textSize = 13f
            setTextColor(palette.muted)
        }, fullWidth())
        row.addView(brand, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        return row
    }

    private fun showAppMenu(anchor: View) {
        if (activeMenuPopup?.isShowing == true) {
            activeMenuPopup?.dismiss()
            activeMenuPopup = null
            return
        }
        val menu = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutDirection = AppSettings.direction(this@MainActivity)
            background = UiTheme.rounded(palette.surface, dp(22).toFloat(), palette.border, dp(1))
            clipToOutline = true
            setPadding(dp(13), dp(13), dp(13), dp(12))
        }

        val header = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            layoutDirection = AppSettings.direction(this@MainActivity)
            setPadding(dp(4), dp(2), dp(4), dp(12))
        }
        val brandMark = TextView(this).apply {
            text = "ت"
            textSize = 19f
            typeface = Typeface.create("sans-serif", Typeface.BOLD)
            gravity = Gravity.CENTER
            setTextColor(palette.accentText)
            background = GradientDrawable(
                GradientDrawable.Orientation.TL_BR,
                intArrayOf(palette.accent, palette.good)
            ).apply { shape = GradientDrawable.OVAL }
        }
        header.addView(brandMark, LinearLayout.LayoutParams(dp(42), dp(42)).apply { marginEnd = dp(11) })
        val heading = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutDirection = AppSettings.direction(this@MainActivity)
        }
        heading.addView(TextView(this).apply {
            text = "تکنا"
            textSize = 17f
            typeface = Typeface.create("sans-serif", Typeface.BOLD)
            setTextColor(palette.text)
        }, fullWidth())
        heading.addView(TextView(this).apply {
            text = t("دسترسی سریع", "Quick access")
            textSize = 12f
            setTextColor(palette.muted)
            setPadding(0, dp(2), 0, 0)
        }, fullWidth())
        header.addView(heading, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        menu.addView(header, fullWidth())

        val accentRule = View(this).apply {
            background = GradientDrawable(
                GradientDrawable.Orientation.LEFT_RIGHT,
                intArrayOf(palette.accent, palette.good)
            ).apply { cornerRadius = dp(2).toFloat() }
        }
        menu.addView(accentRule, fullWidth().apply { height = dp(3); bottomMargin = dp(10) })

        val helpRow = menuActionRow(
            R.drawable.ic_help_outline,
            t("راهنمای استفاده", "How to use"),
            t("اتصال ربات و شروع تصویربرداری", "Connect the bot and start capture")
        ) {
            popupDismissAndRun(anchor) { showHowToUse() }
        }
        val settingsRow = menuActionRow(
            R.drawable.ic_tune,
            t("تنظیمات", "Settings"),
            t("زبان، پوسته و فاصلهٔ عکس‌ها", "Language, theme, and photo interval")
        ) {
            popupDismissAndRun(anchor) { switchToTab(TAB_SETTINGS) }
        }
        menu.addView(helpRow, marginParams(bottom = 5))
        val divider = View(this).apply { setBackgroundColor(palette.border) }
        menu.addView(divider, fullWidth().apply {
            height = dp(1)
            setMargins(dp(7), dp(2), dp(7), dp(2))
        })
        menu.addView(settingsRow, marginParams(top = 5))

        val popupWidth = minOf(dp(292), resources.displayMetrics.widthPixels - dp(36))
        val popup = PopupWindow(menu, popupWidth, ViewGroup.LayoutParams.WRAP_CONTENT, true).apply {
            setBackgroundDrawable(ColorDrawable(android.graphics.Color.TRANSPARENT))
            isOutsideTouchable = true
            elevation = dp(18).toFloat()
        }
        popup.setOnDismissListener {
            anchor.isSelected = false
            if (activeMenuPopup === popup) activeMenuPopup = null
        }
        anchor.isSelected = true
        val alignment = if (AppSettings.isPersian(this)) Gravity.RIGHT else Gravity.LEFT
        popup.showAsDropDown(anchor, 0, dp(9), alignment)
        activeMenuPopup = popup
    }

    private var activeMenuPopup: PopupWindow? = null

    private fun popupDismissAndRun(anchor: View, action: () -> Unit) {
        activeMenuPopup?.dismiss()
        activeMenuPopup = null
        anchor.post { action() }
    }

    private fun menuActionRow(iconRes: Int, title: String, subtitle: String, action: () -> Unit): View {
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            layoutDirection = AppSettings.direction(this@MainActivity)
            setPadding(dp(9), dp(8), dp(9), dp(8))
            isClickable = true
            isFocusable = true
            background = RippleDrawable(
                ColorStateList.valueOf(palette.border),
                UiTheme.rounded(palette.raisedSurface, dp(15).toFloat()),
                null
            )
            setOnClickListener { action() }
        }
        val iconTile = ImageView(this).apply {
            setImageResource(iconRes)
            imageTintList = ColorStateList.valueOf(palette.accent)
            scaleType = ImageView.ScaleType.FIT_CENTER
            setPadding(dp(10), dp(10), dp(10), dp(10))
            background = UiTheme.rounded(palette.surface, dp(12).toFloat(), palette.border, dp(1))
            contentDescription = title
        }
        row.addView(iconTile, LinearLayout.LayoutParams(dp(42), dp(42)).apply { marginEnd = dp(11) })
        val labels = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutDirection = AppSettings.direction(this@MainActivity)
        }
        labels.addView(TextView(this).apply {
            text = title
            textSize = 14f
            typeface = Typeface.create("sans-serif", Typeface.BOLD)
            setTextColor(palette.text)
        }, fullWidth())
        labels.addView(TextView(this).apply {
            text = subtitle
            textSize = 11f
            setTextColor(palette.muted)
            setPadding(0, dp(2), 0, 0)
        }, fullWidth())
        row.addView(labels, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        return row
    }

    private fun showHowToUse() {
        val instructions = t(
            "۱. «باز کردن ربات در بله» را بزنید. لینک اتصال، پیام شروع همراه یک کد مخصوص را برای ربات می‌فرستد؛ اگر بله دکمهٔ شروع نشان داد، آن را بزنید.\n\n" +
                "۲. به برنامه برگردید. برنامه پیام دارای کد اتصال خودش را پیدا می‌کند، شناسهٔ گفت‌وگوی خصوصی شما را می‌خواند و روی همین گوشی ذخیره می‌کند. اگر وصل نشد، «بررسی اتصال بله» را بزنید. شناسه‌ای وارد نمی‌کنید.\n\n" +
                "۳. از تب «تنظیمات»، زبان، حالت روشن یا تیره و فاصلهٔ عکس‌ها را انتخاب کنید.\n\n" +
                "۴. در خانه، رضایت و مجوز استفاده از دوربین و ارسال تصاویر را تأیید کنید؛ سپس «شروع تصویربرداری» را بزنید و مجوزهای دوربین و اعلان را بدهید. باز کردن ربات به‌تنهایی دوربین را روشن نمی‌کند.\n\n" +
                "۵. تا وقتی جلسه فعال است، برنامه با فاصلهٔ انتخاب‌شده عکس می‌گیرد و به گفت‌وگوی خصوصی بله می‌فرستد. اینترنت لازم است و اعلان دائمی وضعیت و دکمهٔ توقف را نشان می‌دهد.\n\n" +
                "۶. برای پایان، «توقف تصویربرداری» یا دکمهٔ توقف اعلان را بزنید. تعداد عکس‌های آخرین جلسه در کارت وضعیت خانه دیده می‌شود. این روش اتصال برای یک نصب کنترل‌شده از برنامه مناسب است.",
            "1. Tap “Open bot in Bale.” Its pairing link sends the bot a start message with a one-time code; tap Start in Bale if it asks you to.\n\n" +
                "2. Return to the app. It finds the start message containing its code, reads your private chat ID, and saves it on this phone. If pairing fails, tap “Check Bale connection.” You never enter an ID.\n\n" +
                "3. In Settings, choose the language, light or dark theme, and photo interval.\n\n" +
                "4. On Home, confirm consent to use the camera and send photos. Tap “Start capture,” then grant camera and notification permissions. Opening the bot alone does not turn on the camera.\n\n" +
                "5. While the session is active, the app takes photos at the selected interval and sends them to your private Bale chat. Internet is required; the ongoing notification shows the status and Stop action.\n\n" +
                "6. Tap “Stop capture” in the app or notification to finish. The Home status card shows the last session’s photo count. This direct pairing is intended for one controlled app installation."
        )
        val dialog = AlertDialog.Builder(this)
            .setTitle(t("راهنمای استفاده", "How to use"))
            .setMessage(instructions)
            .setPositiveButton(t("متوجه شدم", "Got it"), null)
            .create()
        dialog.setOnShowListener {
            dialog.window?.decorView?.layoutDirection = AppSettings.direction(this)
            dialog.findViewById<TextView>(android.R.id.message)?.apply {
                textDirection = if (AppSettings.isPersian(this@MainActivity)) View.TEXT_DIRECTION_FIRST_STRONG_RTL else View.TEXT_DIRECTION_FIRST_STRONG_LTR
                gravity = if (AppSettings.isPersian(this@MainActivity)) Gravity.RIGHT else Gravity.LEFT
                setLineSpacing(dp(3).toFloat(), 1f)
            }
        }
        dialog.show()
    }

    private fun buildSettingsPage(): View {
        val direction = AppSettings.direction(this)
        val page = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutDirection = direction
            setPadding(dp(22), dp(18), dp(22), dp(10))
            setBackgroundColor(palette.background)
        }

        page.addView(TextView(this).apply {
            text = t("تنظیمات", "Settings")
            textSize = 25f
            typeface = Typeface.create("sans-serif", Typeface.BOLD)
            setTextColor(palette.text)
        }, marginParams(bottom = 16))

        val controls = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutDirection = direction
        }
        val themeRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            layoutDirection = direction
            background = UiTheme.rounded(palette.surface, dp(16).toFloat(), palette.border, dp(1))
            setPadding(dp(14), dp(10), dp(14), dp(10))
        }
        themeRow.addView(TextView(this).apply {
            text = t("حالت تیره", "Dark mode")
            textSize = 15f
            setTextColor(palette.text)
        }, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        themeRow.addView(Switch(this).apply {
            isChecked = UiTheme.isDark(this@MainActivity)
            contentDescription = t("تغییر حالت روشن یا تیره", "Toggle dark mode")
            thumbTintList = ColorStateList.valueOf(palette.accent)
            setOnCheckedChangeListener { _, checked ->
                UiTheme.setDark(this@MainActivity, checked)
                recreate()
            }
        }, LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        controls.addView(themeRow, marginParams(bottom = 14))

        controls.addView(settingsSection(t("فاصلهٔ بین عکس‌ها", "Photo interval")), marginParams(bottom = 4))
        controls.addView(TextView(this).apply {
            text = t(
                "برای جلسهٔ فعال هم اعمال می‌شود؛ ارسال کند می‌تواند تصویر بعدی را عقب بیندازد.",
                "Applies to an active session too. Slow uploads may delay the next photo."
            )
            textSize = 12f
            setTextColor(palette.muted)
        }, marginParams(bottom = 4))
        val intervalGroup = RadioGroup(this).apply {
            orientation = RadioGroup.VERTICAL
            layoutDirection = direction
        }
        val selectedInterval = AppSettings.intervalMillis(this)
        AppSettings.INTERVAL_OPTIONS.forEachIndexed { index, interval ->
            val option = RadioButton(this).apply {
                id = INTERVAL_RADIO_ID_BASE + index
                text = AppSettings.intervalLabel(this@MainActivity, interval)
                textSize = 14f
                setTextColor(palette.text)
                buttonTintList = ColorStateList.valueOf(palette.accent)
                layoutDirection = direction
                textDirection = if (AppSettings.isPersian(this@MainActivity)) View.TEXT_DIRECTION_FIRST_STRONG_RTL else View.TEXT_DIRECTION_FIRST_STRONG_LTR
                isChecked = interval == selectedInterval
                setPadding(dp(2), dp(5), dp(2), dp(5))
            }
            intervalGroup.addView(option, fullWidth())
        }
        intervalGroup.setOnCheckedChangeListener { _, checkedId ->
            val index = checkedId - INTERVAL_RADIO_ID_BASE
            val interval = AppSettings.INTERVAL_OPTIONS.getOrNull(index) ?: return@setOnCheckedChangeListener
            getSharedPreferences(AppSettings.PREFERENCES, MODE_PRIVATE).edit()
                .putLong(AppSettings.KEY_INTERVAL_MILLIS, interval)
                .apply()
            if (isCaptureRunning()) {
                startService(Intent(this, CameraCaptureService::class.java).apply {
                    action = CameraCaptureService.ACTION_UPDATE_INTERVAL
                    putExtra(CameraCaptureService.EXTRA_INTERVAL_MILLIS, interval)
                })
                statusText.text = t(
                    "فاصلهٔ تصویربرداری به ${AppSettings.intervalLabel(this, interval)} تغییر کرد.",
                    "Photo interval changed to ${AppSettings.intervalLabel(this, interval)}."
                )
            }
        }
        controls.addView(intervalGroup, marginParams(bottom = 14))

        controls.addView(settingsSection(t("زبان برنامه", "App language")), marginParams(bottom = 4))
        val languageGroup = RadioGroup(this).apply {
            orientation = RadioGroup.HORIZONTAL
            layoutDirection = direction
            gravity = Gravity.CENTER_VERTICAL
        }
        val persianOption = RadioButton(this).apply {
            id = LANGUAGE_PERSIAN_ID
            text = "فارسی"
            textSize = 14f
            setTextColor(palette.text)
            buttonTintList = ColorStateList.valueOf(palette.accent)
            layoutDirection = View.LAYOUT_DIRECTION_RTL
            textDirection = View.TEXT_DIRECTION_FIRST_STRONG_RTL
            gravity = Gravity.CENTER
            minHeight = dp(48)
            setPadding(dp(4), 0, dp(4), 0)
            isChecked = AppSettings.isPersian(this@MainActivity)
        }
        val englishOption = RadioButton(this).apply {
            id = LANGUAGE_ENGLISH_ID
            text = "English"
            textSize = 14f
            setTextColor(palette.text)
            buttonTintList = ColorStateList.valueOf(palette.accent)
            layoutDirection = View.LAYOUT_DIRECTION_LTR
            textDirection = View.TEXT_DIRECTION_FIRST_STRONG_LTR
            gravity = Gravity.CENTER
            minHeight = dp(48)
            setPadding(dp(4), 0, dp(4), 0)
            isChecked = !AppSettings.isPersian(this@MainActivity)
        }
        languageGroup.addView(persianOption, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        languageGroup.addView(englishOption, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        languageGroup.setOnCheckedChangeListener { _, checkedId ->
            val language = when (checkedId) {
                LANGUAGE_PERSIAN_ID -> AppSettings.PERSIAN
                LANGUAGE_ENGLISH_ID -> AppSettings.ENGLISH
                else -> return@setOnCheckedChangeListener
            }
            getSharedPreferences(AppSettings.PREFERENCES, MODE_PRIVATE).edit()
                .putString(AppSettings.KEY_LANGUAGE, language)
                .apply()
            recreate()
        }
        controls.addView(languageGroup, marginParams(bottom = 22))

        val scroll = ScrollView(this).apply {
            isFillViewport = false
            clipToPadding = false
            addView(controls)
        }
        page.addView(scroll, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f))
        page.addView(buildContactFooter(), marginParams(top = 8))
        return page
    }

    private fun buildBottomNavigation(): View {
        val bar = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            layoutDirection = AppSettings.direction(this@MainActivity)
            gravity = Gravity.CENTER
            setPadding(dp(12), dp(8), dp(12), dp(8))
            setBackgroundColor(palette.surface)
        }
        homeTabButton = createTabButton(TAB_HOME, t("خانه", "Home"))
        settingsTabButton = createTabButton(TAB_SETTINGS, t("تنظیمات", "Settings"))
        bar.addView(homeTabButton, LinearLayout.LayoutParams(0, dp(48), 1f).apply { marginEnd = dp(6) })
        bar.addView(settingsTabButton, LinearLayout.LayoutParams(0, dp(48), 1f).apply { marginStart = dp(6) })
        return bar
    }

    private fun createTabButton(tab: Int, label: String): TextView = TextView(this).apply {
        text = label
        textSize = 14f
        typeface = Typeface.create("sans-serif", Typeface.BOLD)
        gravity = Gravity.CENTER
        isClickable = true
        isFocusable = true
        setOnClickListener { switchToTab(tab) }
    }

    private fun switchToTab(tab: Int) {
        if (!::homePage.isInitialized || !::settingsPage.isInitialized) return
        selectedTab = if (tab == TAB_SETTINGS) TAB_SETTINGS else TAB_HOME
        homePage.visibility = if (selectedTab == TAB_HOME) View.VISIBLE else View.GONE
        settingsPage.visibility = if (selectedTab == TAB_SETTINGS) View.VISIBLE else View.GONE
        if (::homeTabButton.isInitialized && ::settingsTabButton.isInitialized) {
            styleTab(homeTabButton, selectedTab == TAB_HOME)
            styleTab(settingsTabButton, selectedTab == TAB_SETTINGS)
        }
    }

    private fun styleTab(button: TextView, selected: Boolean) {
        button.setTextColor(if (selected) palette.accentText else palette.muted)
        button.background = UiTheme.rounded(
            if (selected) palette.accent else palette.surface,
            dp(13).toFloat(),
            if (selected) null else palette.border,
            dp(1)
        )
    }

    private fun settingsSection(label: String): TextView = TextView(this).apply {
        text = label
        textSize = 16f
        typeface = Typeface.create("sans-serif", Typeface.BOLD)
        setTextColor(palette.text)
        setPadding(0, dp(8), 0, dp(5))
    }

    private fun buildContactFooter(): View {
        val footer = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            layoutDirection = AppSettings.direction(this@MainActivity)
            setPadding(0, dp(12), 0, 0)
        }
        val divider = View(this).apply { setBackgroundColor(palette.border) }
        val dividerHolder = LinearLayout(this).apply { addView(divider, fullWidth().apply { height = dp(1) }) }
        val block = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutDirection = AppSettings.direction(this@MainActivity)
        }
        block.addView(TextView(this).apply {
            text = t("تماس با تکنا", "Contact Tekkna")
            textSize = 15f
            typeface = Typeface.create("sans-serif", Typeface.BOLD)
            setTextColor(palette.text)
        }, fullWidth())
        block.addView(TextView(this).apply {
            text = "tekkna.ir"
            textSize = 13f
            setTextColor(palette.accent)
            paintFlags = paintFlags or Paint.UNDERLINE_TEXT_FLAG
            setOnClickListener { openTekknaWebsite() }
        }, fullWidth())
        footer.addView(ImageView(this).apply {
            setImageResource(R.drawable.tekkna_logo)
            contentDescription = t("نشان تکنا", "Tekkna logo")
            scaleType = ImageView.ScaleType.FIT_CENTER
        }, LinearLayout.LayoutParams(dp(46), dp(46)).apply { marginEnd = dp(12) })
        footer.addView(block, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        return LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutDirection = AppSettings.direction(this@MainActivity)
            addView(dividerHolder, fullWidth())
            addView(footer, fullWidth())
        }
    }

    private fun openTekknaWebsite() {
        try {
            startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://tekkna.ir")))
        } catch (_: android.content.ActivityNotFoundException) {
            Toast.makeText(this, t("امکان باز کردن وب‌سایت وجود ندارد.", "Could not open the website."), Toast.LENGTH_SHORT).show()
        }
    }

    private fun isCaptureRunning(): Boolean {
        val preferences = getSharedPreferences(CameraCaptureService.PREFS_NAME, MODE_PRIVATE)
        return preferences.getBoolean(CameraCaptureService.PREF_RUNNING, false) &&
            preferences.getInt(CameraCaptureService.PREF_PROCESS_ID, -1) == android.os.Process.myPid()
    }

    private fun buildStatusCard(): View {
        val card = card()
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            layoutDirection = AppSettings.direction(this@MainActivity)
        }
        val indicator = View(this).apply {
            background = UiTheme.rounded(palette.good, dp(9).toFloat())
            contentDescription = t("وضعیت تصویربرداری", "Capture status")
        }
        row.addView(indicator, LinearLayout.LayoutParams(dp(10), dp(10)).apply { marginStart = dp(9) })
        row.addView(TextView(this).apply {
            text = t("وضعیت برنامه", "App status")
            textSize = 13f
            setTextColor(palette.muted)
        }, LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        card.addView(row, fullWidth())

        statusHeadline = TextView(this).apply {
            text = t("آمادهٔ شروع", "Ready to start")
            textSize = 22f
            typeface = Typeface.create("sans-serif", Typeface.BOLD)
            setTextColor(palette.text)
            setPadding(0, dp(11), 0, dp(3))
        }
        card.addView(statusHeadline, fullWidth())

        statusText = TextView(this).apply {
            text = readyDescription()
            textSize = 13f
            setTextColor(palette.muted)
            setLineSpacing(dp(2).toFloat(), 1f)
            textDirection = if (AppSettings.isPersian(this@MainActivity)) View.TEXT_DIRECTION_FIRST_STRONG_RTL else View.TEXT_DIRECTION_FIRST_STRONG_LTR
        }
        card.addView(statusText, fullWidth())

        lastSessionCountText = TextView(this).apply {
            text = lastSessionCountLabel()
            textSize = 13f
            typeface = Typeface.create("sans-serif", Typeface.BOLD)
            setTextColor(palette.accent)
            setPadding(0, dp(12), 0, 0)
            textDirection = if (AppSettings.isPersian(this@MainActivity)) View.TEXT_DIRECTION_FIRST_STRONG_RTL else View.TEXT_DIRECTION_FIRST_STRONG_LTR
        }
        card.addView(lastSessionCountText, fullWidth())
        return card
    }

    private fun buildRecipientCard(): View {
        val card = card()
        card.addView(sectionTitle(
            t("مقصد ارسال", "Sending destination"),
            t("شناسهٔ گفت‌وگو پس از شروع ربات به‌صورت خودکار دریافت می‌شود.", "The conversation ID is discovered after you start the bot.")
        ), fullWidth())

        recipientStatusText = TextView(this).apply {
            textSize = 12f
            setTextColor(palette.muted)
            setLineSpacing(dp(2).toFloat(), 1f)
            textDirection = if (AppSettings.isPersian(this@MainActivity)) View.TEXT_DIRECTION_FIRST_STRONG_RTL else View.TEXT_DIRECTION_FIRST_STRONG_LTR
            setPadding(0, dp(12), 0, dp(8))
        }
        card.addView(recipientStatusText, fullWidth())

        checkConnectionButton = makeButton(t("بررسی اتصال بله", "Check Bale connection"), primary = false).apply {
            setOnClickListener { checkBaleConnection(showProgress = true) }
        }
        card.addView(checkConnectionButton, marginParams(top = 4))
        return card
    }

    private fun refreshRecipientStatus() {
        if (!::recipientStatusText.isInitialized) return
        val chatId = savedChatId()
        recipientStatusText.text = if (pendingPairingNonce().isNotBlank()) {
            t("در انتظار پیام شروع از ربات بله…", "Waiting for the Bale bot’s start message…")
        } else if (chatId.isNotBlank()) {
            t("ربات بله متصل است؛ مقصد ذخیره شده.", "Bale bot connected; destination saved.")
        } else {
            t("برای اتصال، ربات را باز کنید و دکمهٔ شروع را بزنید.", "To connect, open the bot and tap Start.")
        }
    }

    private fun savedChatId(): String = getSharedPreferences(PREFS, MODE_PRIVATE)
        .getString(KEY_CHAT_ID, "").orEmpty().trim()

    private fun pendingPairingNonce(): String = getSharedPreferences(PREFS, MODE_PRIVATE)
        .getString(KEY_PENDING_PAIRING_NONCE, "").orEmpty()

    private fun checkBaleConnection(showProgress: Boolean) {
        if (checkingBaleConnection) return
        val nonce = pendingPairingNonce()
        if (nonce.isBlank()) {
            refreshRecipientStatus()
            if (savedChatId().isBlank()) {
                recipientStatusText.text = t(
                    "ابتدا «باز کردن ربات در بله» را بزنید و در گفت‌وگو دکمهٔ شروع را انتخاب کنید.",
                    "First tap “Open bot in Bale,” then tap Start in the bot chat."
                )
            }
            return
        }
        val token = BuildConfig.BALE_BOT_TOKEN
        if (token.isBlank()) {
            recipientStatusText.text = t("ربات برای این نسخه پیکربندی نشده است.", "The bot is not configured for this build.")
            return
        }

        checkingBaleConnection = true
        checkConnectionButton.isEnabled = false
        if (showProgress) recipientStatusText.text = t("در حال بررسی پیام ربات…", "Checking for the bot’s message…")
        val preferences = getSharedPreferences(PREFS, MODE_PRIVATE)
        Thread {
            val result = runCatching {
                val deadline = System.currentTimeMillis() + PAIRING_TIMEOUT_MS
                var discoveredId = ""
                while (System.currentTimeMillis() < deadline && discoveredId.isBlank()) {
                    val offset = preferences.getLong(KEY_UPDATE_OFFSET, 0L)
                    val batch = fetchStartUpdate(token, offset, "pair_$nonce")
                    batch.nextOffset?.let { nextOffset ->
                        preferences.edit().putLong(KEY_UPDATE_OFFSET, nextOffset).commit()
                    }
                    discoveredId = batch.privateChatId.orEmpty()
                    if (discoveredId.isNotBlank()) {
                        preferences.edit()
                            .putString(KEY_CHAT_ID, discoveredId)
                            .remove(KEY_PENDING_PAIRING_NONCE)
                            .commit()
                    }
                    if (discoveredId.isBlank()) Thread.sleep(PAIRING_POLL_INTERVAL_MS)
                }
                discoveredId
            }
            runOnUiThread {
                checkingBaleConnection = false
                if (isFinishing || isDestroyed) return@runOnUiThread
                checkConnectionButton.isEnabled = true
                result.onSuccess { chatId ->
                    if (chatId.isNotBlank()) {
                        preferences.edit()
                            .putString(KEY_CHAT_ID, chatId)
                            .remove(KEY_PENDING_PAIRING_NONCE)
                            .apply()
                        recipientStatusText.text = t("اتصال برقرار شد؛ مقصد برای دفعات بعد ذخیره شد.", "Connected. The destination is saved for next time.")
                    } else {
                        recipientStatusText.text = t(
                            "پیام شروع دریافت نشد. گفت‌وگوی ربات را باز کنید، شروع را بزنید و دوباره بررسی کنید.",
                            "No start message received. Open the bot chat, tap Start, then check again."
                        )
                    }
                }.onFailure {
                    recipientStatusText.text = t(
                        "دریافت پیام ربات ناموفق بود. اتصال اینترنت یا تنظیم وب‌هوک ربات را بررسی کنید.",
                        "Could not receive the bot update. Check internet access or the bot’s webhook configuration."
                    )
                }
            }
        }.start()
    }

    private fun fetchStartUpdate(token: String, offset: Long, expectedPayload: String): UpdateBatch {
        val uri = Uri.parse("https://tapi.bale.ai/bot$token/getUpdates").buildUpon()
            .appendQueryParameter("offset", offset.toString())
            .appendQueryParameter("timeout", "0")
            .appendQueryParameter("limit", "100")
            .build()
        val connection = (URL(uri.toString()).openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            connectTimeout = 8_000
            readTimeout = 8_000
        }
        try {
            val responseCode = connection.responseCode
            val stream = if (responseCode in 200..299) connection.inputStream else connection.errorStream
            val body = stream?.bufferedReader(Charsets.UTF_8)?.use { it.readText() }.orEmpty()
            if (responseCode !in 200..299) throw IOException("Bale getUpdates request failed")
            val response = JSONObject(body)
            if (!response.optBoolean("ok")) throw IOException("Bale getUpdates response was not successful")
            val updates = response.optJSONArray("result") ?: return UpdateBatch(null, null)
            var highestUpdateId = -1L
            var matchedChatId: String? = null
            val startPattern = Regex("^/start(?:@[A-Za-z0-9_]+)?\\s+${Regex.escape(expectedPayload)}$")
            for (index in 0 until updates.length()) {
                val update = updates.optJSONObject(index) ?: continue
                val updateId = update.optLong("update_id", -1L)
                if (updateId > highestUpdateId) highestUpdateId = updateId
                val message = update.optJSONObject("message") ?: continue
                val chat = message.optJSONObject("chat") ?: continue
                if (chat.optString("type") != "private") continue
                if (startPattern.matches(message.optString("text"))) {
                    matchedChatId = chat.optString("id").takeIf { it.isNotBlank() }
                }
            }
            val nextOffset = if (highestUpdateId >= 0) highestUpdateId + 1 else null
            return UpdateBatch(nextOffset, matchedChatId)
        } finally {
            connection.disconnect()
        }
    }

    private data class UpdateBatch(val nextOffset: Long?, val privateChatId: String?)

    private fun buildBotCard(): View {
        val card = card()
        card.addView(sectionTitle(
            t("ربات بله", "Bale bot"),
            t("برای شروع گفت‌وگو یا فعال‌کردن ربات، آن را در بله باز کنید.", "Open the bot in Bale to start the conversation or activate it.")
        ), fullWidth())

        val idRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            layoutDirection = AppSettings.direction(this@MainActivity)
            setPadding(0, dp(12), 0, 0)
        }
        idRow.addView(TextView(this).apply {
            text = t("شناسهٔ ربات:", "Bot ID:")
            textSize = 13f
            setTextColor(palette.muted)
        }, LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        botIdText = TextView(this).apply {
            text = t("در حال دریافت…", "Loading…")
            textSize = 13f
            setTextColor(palette.text)
            textDirection = View.TEXT_DIRECTION_LTR
            layoutDirection = View.LAYOUT_DIRECTION_LTR
            gravity = Gravity.CENTER_VERTICAL or Gravity.START
            setPadding(dp(8), 0, 0, 0)
        }
        idRow.addView(botIdText, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        card.addView(idRow, fullWidth())

        botLinkText = TextView(this).apply {
            text = t("در حال دریافت پیوند ربات…", "Loading bot link…")
            textSize = 14f
            setTextColor(palette.accent)
            paintFlags = paintFlags or Paint.UNDERLINE_TEXT_FLAG
            textDirection = if (AppSettings.isPersian(this@MainActivity)) View.TEXT_DIRECTION_FIRST_STRONG_RTL else View.TEXT_DIRECTION_FIRST_STRONG_LTR
            gravity = Gravity.START
            isEnabled = false
            alpha = 0.55f
            setPadding(0, dp(12), 0, 0)
            setOnClickListener { openBotInBale() }
        }
        card.addView(botLinkText, fullWidth())
        return card
    }

    private fun loadBotProfile() {
        val token = BuildConfig.BALE_BOT_TOKEN
        if (token.isBlank()) {
            botIdText.text = "—"
            botLinkText.text = t("ربات پیکربندی نشده است", "Bot is not configured")
            botLinkText.isEnabled = false
            return
        }

        val preferences = getSharedPreferences(PREFS, MODE_PRIVATE)
        val cachedId = preferences.getString(KEY_BOT_ID, "").orEmpty()
        val cachedUsername = preferences.getString(KEY_BOT_USERNAME, "").orEmpty()
        val tokenId = token.substringBefore(':').takeIf { value -> value.isNotBlank() && value.all(Char::isDigit) }.orEmpty()
        renderBotProfile(cachedId.ifBlank { tokenId }, cachedUsername)

        Thread {
            val result = runCatching { fetchBotProfile(token) }
            runOnUiThread {
                if (isFinishing || isDestroyed) return@runOnUiThread
                result.onSuccess { profile ->
                    preferences.edit()
                        .putString(KEY_BOT_ID, profile.id)
                        .putString(KEY_BOT_USERNAME, profile.username)
                        .apply()
                    renderBotProfile(profile.id, profile.username)
                }.onFailure {
                    if (cachedUsername.isBlank()) {
                        botLinkText.text = t("پیوند ربات در دسترس نیست", "Bot link is unavailable")
                        botLinkText.isEnabled = false
                        botLinkText.alpha = 0.55f
                    }
                }
            }
        }.start()
    }

    private fun fetchBotProfile(token: String): BotProfile {
        val connection = (URL("https://tapi.bale.ai/bot$token/getMe").openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            connectTimeout = 8_000
            readTimeout = 8_000
        }
        try {
            val responseCode = connection.responseCode
            val stream = if (responseCode in 200..299) connection.inputStream else connection.errorStream
            val body = stream?.bufferedReader(Charsets.UTF_8)?.use { it.readText() }.orEmpty()
            if (responseCode !in 200..299) throw IOException("Bale getMe request failed")
            val response = JSONObject(body)
            if (!response.optBoolean("ok")) throw IOException("Bale getMe response was not successful")
            val bot = response.optJSONObject("result") ?: throw IOException("Bale getMe response had no bot profile")
            val id = bot.optString("id")
            if (id.isBlank()) throw IOException("Bale getMe response had no bot ID")
            return BotProfile(id, bot.optString("username"))
        } finally {
            connection.disconnect()
        }
    }

    private fun renderBotProfile(id: String, username: String) {
        botIdText.text = id.ifBlank { "—" }
        val safeUsername = username.trim().removePrefix("@")
        if (safeUsername.matches(Regex("^[A-Za-z0-9_]+$"))) {
            botUri = Uri.parse("https://ble.ir/$safeUsername")
            botLinkText.text = t("باز کردن ربات در بله ↗", "Open bot in Bale ↗")
            botLinkText.isEnabled = true
            botLinkText.alpha = 1f
        } else {
            botUri = null
            botLinkText.text = t("نام کاربری ربات برای ساخت پیوند در دسترس نیست", "Bot username is unavailable")
            botLinkText.isEnabled = false
            botLinkText.alpha = 0.55f
        }
    }

    private fun openBotInBale() {
        val baseUri = botUri ?: return
        val nonce = UUID.randomUUID().toString().replace("-", "")
        getSharedPreferences(PREFS, MODE_PRIVATE).edit()
            .putString(KEY_PENDING_PAIRING_NONCE, nonce)
            .apply()
        refreshRecipientStatus()
        val uri = baseUri.buildUpon().appendQueryParameter("start", "pair_$nonce").build()
        try {
            startActivity(Intent(Intent.ACTION_VIEW, uri))
        } catch (_: android.content.ActivityNotFoundException) {
            Toast.makeText(this, t("امکان باز کردن پیوند بله وجود ندارد.", "Could not open the Bale link."), Toast.LENGTH_SHORT).show()
        }
    }

    private fun buildConsentCard(): View {
        val card = card()
        card.addView(sectionTitle(
            t("پیش از شروع", "Before starting"),
            t("اجازه و آگاهی را تأیید کنید.", "Confirm consent and authorization.")
        ), fullWidth())
        consentCheck = CheckBox(this).apply {
            text = t(
                "برای استفاده از دوربین و ارسال تصاویر به این مقصد، مجوز دارم.",
                "I have permission to use the camera and send photos to this destination."
            )
            textSize = 14f
            setTextColor(palette.text)
            buttonTintList = ColorStateList.valueOf(palette.accent)
            layoutDirection = AppSettings.direction(this@MainActivity)
            textDirection = if (AppSettings.isPersian(this@MainActivity)) View.TEXT_DIRECTION_FIRST_STRONG_RTL else View.TEXT_DIRECTION_FIRST_STRONG_LTR
            gravity = Gravity.CENTER_VERTICAL
            setPadding(0, dp(12), 0, 0)
        }
        card.addView(consentCheck, fullWidth())
        return card
    }

    private fun sectionTitle(title: String, subtitle: String): View = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        layoutDirection = AppSettings.direction(this@MainActivity)
        addView(TextView(this@MainActivity).apply {
            text = title
            textSize = 17f
            typeface = Typeface.create("sans-serif", Typeface.BOLD)
            setTextColor(palette.text)
        }, fullWidth())
        addView(TextView(this@MainActivity).apply {
            text = subtitle
            textSize = 12f
            setTextColor(palette.muted)
            setPadding(0, dp(3), 0, 0)
        }, fullWidth())
    }

    private fun card(): LinearLayout = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        layoutDirection = AppSettings.direction(this@MainActivity)
        background = UiTheme.rounded(palette.surface, dp(20).toFloat(), palette.border, dp(1))
        setPadding(dp(17), dp(17), dp(17), dp(17))
    }

    private fun makeButton(label: String, primary: Boolean): Button = Button(this).apply {
        text = label
        isAllCaps = false
        textSize = 15f
        typeface = Typeface.create("sans-serif", Typeface.BOLD)
        gravity = Gravity.CENTER
        minHeight = dp(54)
        setTextColor(if (primary) palette.accentText else palette.text)
        background = UiTheme.rounded(
            if (primary) palette.accent else palette.surface,
            dp(16).toFloat(),
            if (primary) null else palette.border,
            dp(1)
        )
        stateListAnimator = null
    }

    private fun readyDescription(): String {
        val frequency = AppSettings.intervalPhrase(this, AppSettings.intervalMillis(this))
        return if (savedChatId().isBlank()) {
            t(
                "ابتدا ربات را در بله باز کنید و شروع را بزنید تا مقصد به‌صورت خودکار متصل شود.",
                "Open the bot in Bale and tap Start to connect a destination automatically."
            )
        } else {
            t(
                "با شروع، دوربین $frequency یک تصویر می‌گیرد و آن را به مقصد متصل‌شده می‌فرستد.",
                "When started, the camera takes one photo $frequency and sends it to the connected Bale chat."
            )
        }
    }

    private fun beginPermissionFlow() {
        if (BuildConfig.BALE_BOT_TOKEN.isBlank()) {
            statusHeadline.text = t("تنظیمات کامل نیست", "Setup is incomplete")
            statusText.text = t("برای این نسخه، ربات بله پیکربندی نشده است.", "A Bale bot is not configured for this build.")
            return
        }
        if (savedChatId().isBlank()) {
            statusHeadline.text = t("اتصال ربات لازم است", "Connect the bot first")
            statusText.text = t("ربات را در بله باز کنید و شروع را بزنید تا مقصد خودکار دریافت شود.", "Open the bot in Bale and tap Start to discover the destination automatically.")
            return
        }
        if (!consentCheck.isChecked) {
            statusHeadline.text = t("تأیید لازم است", "Consent required")
            statusText.text = t(
                "پیش از شروع، مجوز استفاده از دوربین و ارسال تصاویر را تأیید کنید.",
                "Confirm that you have permission to use the camera and send the photos before starting."
            )
            return
        }
        waitingForPermissions = true
        continuePermissionFlow()
    }

    private fun continuePermissionFlow() {
        if (!waitingForPermissions) return
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(arrayOf(Manifest.permission.CAMERA), PERMISSION_REQUEST)
            return
        }
        if (Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), PERMISSION_REQUEST)
            return
        }
        waitingForPermissions = false
        startCapture()
    }

    @Deprecated("Permission result callback required for API 26 compatibility")
    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode != PERMISSION_REQUEST || !waitingForPermissions) return
        if (grantResults.isNotEmpty() && grantResults.all { it == PackageManager.PERMISSION_GRANTED }) {
            continuePermissionFlow()
        } else {
            waitingForPermissions = false
            statusHeadline.text = t("مجوز لازم است", "Permission required")
            statusText.text = t(
                "برای اجرای تصویربرداری قابل‌مشاهده، دسترسی دوربین و اعلان لازم است.",
                "Camera and notification permissions are required for visible capture."
            )
        }
    }

    private fun startCapture() {
        val launchIntent = Intent(this, CameraCaptureService::class.java).apply {
            putExtra(CameraCaptureService.EXTRA_CHAT_ID, savedChatId())
            putExtra(CameraCaptureService.EXTRA_INTERVAL_MILLIS, AppSettings.intervalMillis(this@MainActivity))
        }
        ContextCompat.startForegroundService(this, launchIntent)
        consentCheck.isChecked = false
        statusHeadline.text = t("در حال آماده‌سازی دوربین", "Preparing camera")
        statusText.text = t(
            "اعلان دائمی برنامه، وضعیت و دکمهٔ توقف را نشان می‌دهد.",
            "The ongoing notification shows the session status and stop action."
        )
        refreshButtons(running = true)
    }

    private fun refreshSessionState() {
        val preferences = getSharedPreferences(CameraCaptureService.PREFS_NAME, MODE_PRIVATE)
        val running = isCaptureRunning()
        if (!running && preferences.getBoolean(CameraCaptureService.PREF_RUNNING, false)) {
            val recoveredCount = preferences.getInt(CameraCaptureService.PREF_CURRENT_PHOTO_COUNT, 0)
            preferences.edit()
                .putBoolean(CameraCaptureService.PREF_RUNNING, false)
                .putInt(CameraCaptureService.PREF_PROCESS_ID, -1)
                .putInt(CameraCaptureService.PREF_LAST_PHOTO_COUNT, recoveredCount)
                .apply()
        }
        refreshButtons(running)
        refreshLastSessionCount()
        if (running) {
            statusHeadline.text = t("تصویربرداری فعال است", "Capture is active")
            statusText.text = t(
                "تصاویر به گفت‌وگوی انتخابی بله فرستاده می‌شوند. برای توقف، دکمهٔ زیر یا اعلان را بزنید.",
                "Photos are being sent to the selected Bale conversation. Use the button or notification to stop."
            )
        } else {
            statusHeadline.text = t("آمادهٔ شروع", "Ready to start")
            statusText.text = readyDescription()
        }
        refreshRecipientStatus()
    }

    private fun lastSessionCountLabel(): String {
        val count = getSharedPreferences(CameraCaptureService.PREFS_NAME, MODE_PRIVATE)
            .getInt(CameraCaptureService.PREF_LAST_PHOTO_COUNT, 0)
        return t(
            "تصاویر ثبت‌شده در آخرین جلسه: ${AppSettings.persianDigits(count)}",
            "Photos captured in the last session: $count"
        )
    }

    private fun refreshLastSessionCount() {
        if (::lastSessionCountText.isInitialized) lastSessionCountText.text = lastSessionCountLabel()
    }

    private fun refreshButtons(running: Boolean) {
        startButton.isEnabled = !running
        startButton.alpha = if (running) 0.52f else 1f
        stopButton.isEnabled = running
        stopButton.alpha = if (running) 1f else 0.52f
        consentCheck.isEnabled = !running
    }

    override fun onStop() {
        if (sessionCountReceiverRegistered) {
            unregisterReceiver(sessionCountReceiver)
            sessionCountReceiverRegistered = false
        }
        super.onStop()
    }

    @Deprecated("Use the settings tab navigation")
    override fun onBackPressed() {
        if (::settingsPage.isInitialized && selectedTab == TAB_SETTINGS) {
            switchToTab(TAB_HOME)
        } else {
            super.onBackPressed()
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        if (::consentCheck.isInitialized) outState.putBoolean(STATE_CONSENT, consentCheck.isChecked)
        outState.putInt(STATE_SELECTED_TAB, selectedTab)
        super.onSaveInstanceState(outState)
    }

    private fun dp(value: Int) = UiTheme.dp(this, value)
    private fun fullWidth() = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
    private fun marginParams(top: Int = 0, bottom: Int = 0): LinearLayout.LayoutParams = fullWidth().apply {
        setMargins(0, dp(top), 0, dp(bottom))
    }

    companion object {
        private const val PERMISSION_REQUEST = 731
        private const val TAB_HOME = 0
        private const val TAB_SETTINGS = 1
        private const val PREFS = "ui_preferences"
        private const val KEY_CHAT_ID = "chat_id"
        private const val KEY_PENDING_PAIRING_NONCE = "pending_pairing_nonce"
        private const val KEY_UPDATE_OFFSET = "bale_update_offset"
        private const val KEY_BOT_ID = "bot_id"
        private const val KEY_BOT_USERNAME = "bot_username"
        private const val STATE_CONSENT = "consent_state"
        private const val STATE_SELECTED_TAB = "selected_tab_state"
        private const val PAIRING_TIMEOUT_MS = 20_000L
        private const val PAIRING_POLL_INTERVAL_MS = 1_200L
        private const val INTERVAL_RADIO_ID_BASE = 8_500
        private const val LANGUAGE_PERSIAN_ID = 8_600
        private const val LANGUAGE_ENGLISH_ID = 8_601
    }

    private data class BotProfile(val id: String, val username: String)
}
