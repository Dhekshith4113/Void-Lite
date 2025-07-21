package com.example.voidlite

import android.app.AlertDialog
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.provider.Settings
import android.view.GestureDetector
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ListView
import android.widget.RadioButton
import android.widget.TextView
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.appcompat.widget.SwitchCompat
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.abs

class SettingsActivity : AppCompatActivity() {

    private lateinit var gestureDetector: GestureDetector
    private lateinit var appUsageView: View
    private lateinit var listView: ListView
    private lateinit var usageStatsLauncher: ActivityResultLauncher<Intent>
    private lateinit var accessibilityLauncher: ActivityResultLauncher<Intent>

    private var visibilityToggle: ImageView? = null
    private var lockSwitch: SwitchCompat? = null
    private var doubleTapSwitch: SwitchCompat? = null
    private var showAppUsageView: Boolean = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_settings)
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.settingsLayout)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        val backButton = findViewById<ImageButton>(R.id.backButtonSettings)

        backButton.setOnClickListener {
            finish()
        }

        usageStatsLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) {
            if (UsageStatsManagerUtils.hasUsageStatsPermission(this)) {
                SharedPreferencesManager.setVisibilityToggleEnabled(this, true)
                visibilityToggle?.setImageResource(R.drawable.visibility_off_24px)
            } else {
                SharedPreferencesManager.setVisibilityToggleEnabled(this, false)
                visibilityToggle?.setImageResource(R.drawable.visibility_24px)
                Toast.makeText(
                    this,
                    "Please grant permission to show app usage",
                    Toast.LENGTH_SHORT
                ).show()
            }
            recreate()
        }

        accessibilityLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) {
            if (AppAccessibilityService.isAccessibilityServiceEnabled()) {
                SharedPreferencesManager.setSwipeToLockEnabled(this, true)
                SharedPreferencesManager.setDoubleTapToLockEnabled(this, true)
                lockSwitch?.isChecked = true
                doubleTapSwitch?.isChecked = true
            } else {
                SharedPreferencesManager.setSwipeToLockEnabled(this, false)
                SharedPreferencesManager.setDoubleTapToLockEnabled(this, false)
                lockSwitch?.isChecked = false
                doubleTapSwitch?.isChecked = false
                Toast.makeText(
                    this,
                    "Please grant permission to lock the phone",
                    Toast.LENGTH_SHORT
                ).show()
            }
        }

        gestureDetector = GestureDetector(this, SwipeBackGestureListener())

        listView = findViewById(R.id.settingsListView)
        val options = listOf(
            "Permission Stats",
            "Hidden apps",
            "Change color theme",
            "Gestures",
            "Customization",
            "Change launcher",
            "Device settings",
            "Digital Wellbeing"
        )
        val adapter = object : ArrayAdapter<String>(
            this,
            R.layout.item_settings_option,
            R.id.settingsOptionText,
            options
        ) {
            override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
                if (position == options.size - 1) {
                    appUsageView = LayoutInflater.from(context).inflate(R.layout.app_usage_layout, parent, false)
                    val visibleLayout: ConstraintLayout = appUsageView.findViewById(R.id.visibleLayout)
                    visibilityToggle = appUsageView.findViewById(R.id.visibilityToggle)
                    visibleLayout.visibility = View.GONE

                    if (!UsageStatsManagerUtils.hasUsageStatsPermission(context)) {
                        SharedPreferencesManager.setVisibilityToggleEnabled(context, false)
                    }

                    showAppUsageView = SharedPreferencesManager.isVisibilityToggleEnabled(context)

                    visibilityToggle?.setOnClickListener {
                        if (!UsageStatsManagerUtils.hasUsageStatsPermission(context)) {
                            promptUsageAccessSettings(context)
                        } else {
                            showAppUsageView = !showAppUsageView
                            SharedPreferencesManager.setVisibilityToggleEnabled(context, showAppUsageView)
                        }
                        notifyDataSetChanged()
                    }

                    if (showAppUsageView) {
                        populateAppUsageOption(appUsageView)

                        appUsageView.findViewById<TextView>(R.id.totalScreenTimeToday).text = "Total screen time today:"
                        visibilityToggle?.setImageResource(R.drawable.visibility_off_24px)
                        visibleLayout.visibility = View.VISIBLE
                    } else {
                        appUsageView.findViewById<TextView>(R.id.totalScreenTimeToday).text = options[position]
                        visibilityToggle?.setImageResource(R.drawable.visibility_24px)
                        visibleLayout.visibility = View.GONE
                    }
                    return appUsageView
                } else {
                    val view = LayoutInflater.from(context)
                        .inflate(R.layout.item_settings_option, parent, false)
                    view.findViewById<TextView>(R.id.settingsOptionText)?.text = options[position]
                    return view
                }
            }
        }
        listView.adapter = adapter

        listView.setOnItemClickListener { _, _, position, _ ->
            when (position) {
                0 -> startActivity(Intent(this, PermissionsActivity::class.java))
                1 -> startActivity(Intent(this, HiddenAppsActivity::class.java))
                2 -> showThemeDialog()
                3 -> showGesturesDialog()
                4 -> showCustomizationDialog()
                5 -> startActivity(Intent(Settings.ACTION_HOME_SETTINGS))
                6 -> startActivity(Intent(Settings.ACTION_SETTINGS))
                7 -> try {
                    val intent = Intent()
                    intent.setClassName(
                        "com.samsung.android.forest",
                        "com.samsung.android.forest.home.ui.DefaultActivity" // Common on some devices
                    )
                    startActivity(intent)
                } catch (e: Exception) {
                    Toast.makeText(this, "Digital Wellbeing not available", Toast.LENGTH_SHORT)
                        .show()
                }

                else -> {
                    Toast.makeText(this, "Something's wrong!", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    private fun populateAppUsageOption(optionView: View): View {
        val totalTimeTextView = optionView.findViewById<TextView>(R.id.textViewTotalTime)

        val appTextViews = listOf(
            optionView.findViewById<TextView>(R.id.appOne),
            optionView.findViewById(R.id.appTwo),
            optionView.findViewById(R.id.appThree),
            optionView.findViewById(R.id.appFour)
        )

        val appTimeViews = listOf(
            optionView.findViewById<TextView>(R.id.appOneTime),
            optionView.findViewById(R.id.appTwoTime),
            optionView.findViewById(R.id.appThreeTime),
            optionView.findViewById(R.id.appFourTime)
        )

        val appIndicatorView = listOf(
            optionView.findViewById<View>(R.id.appOneIndicator),
            optionView.findViewById(R.id.appTwoIndicator),
            optionView.findViewById(R.id.appThreeIndicator),
            optionView.findViewById(R.id.appFourIndicator)
        )

        val appViews = listOf(
            optionView.findViewById<View>(R.id.appOneView),
            optionView.findViewById(R.id.appTwoView),
            optionView.findViewById(R.id.appThreeView),
            optionView.findViewById(R.id.appFourView)
        )

        val usageBar = optionView.findViewById<LinearLayout>(R.id.usageBar)

        val colors = listOf(
            Color.parseColor("#2ED3B7"),
            Color.parseColor("#3A6FF8"),
            Color.parseColor("#7A5DFE"),
            Color.parseColor("#B1C0D7")
        )

        if (UsageStatsManagerUtils.hasUsageStatsPermission(this)) {
            lifecycleScope.launch {
                val (totalTime, topApps) = withContext(Dispatchers.IO) {
                    UsageStatsManagerUtils.getTodayTopUsedApps(this@SettingsActivity)
                }

                totalTimeTextView.text = formatTime(totalTime)

                for (i in appTextViews.indices) {
                    if (i < topApps.size) {
                        appTextViews[i].visibility = View.VISIBLE
                        appTimeViews[i].visibility = View.VISIBLE
                        appIndicatorView[i].visibility = View.VISIBLE
                        appViews[i].visibility = View.VISIBLE

                        val (packageName, usageTime) = topApps[i]
                        appTextViews[i].text = packageName
                        appTimeViews[i].text = formatTime(usageTime)
                    } else {
                        appTextViews[i].visibility = View.GONE
                        appTimeViews[i].visibility = View.GONE
                        appIndicatorView[i].visibility = View.GONE
                        appViews[i].visibility = View.GONE
                    }
                }

                for (i in appIndicatorView.indices) {
                    val drawable = ContextCompat.getDrawable(
                        this@SettingsActivity,
                        R.drawable.app_indicator_background
                    )?.mutate() as? GradientDrawable
                    drawable?.setColor(colors[i])
                    appIndicatorView[i].background = drawable
                }

                populateUsageBar(usageBar, topApps, totalTime)
            }
        } else {
            totalTimeTextView.text = "  --h --m"
        }

        return optionView
    }


    private fun populateUsageBar(
        container: LinearLayout,
        appUsageList: List<Pair<String, Long>>,
        totalTime: Long
    ) {
        container.removeAllViews()
        val colors = listOf(
            Color.parseColor("#2ED3B7"),
            Color.parseColor("#3A6FF8"),
            Color.parseColor("#7A5DFE"),
            Color.parseColor("#B1C0D7")
        )

        appUsageList.forEachIndexed { index, (_, usageTime) ->
            val weight = usageTime.toFloat() / totalTime
            val view = createRoundedSegment(
                container.context,
                colors.getOrElse(index) { Color.GRAY },
                isFirst = index == 0,
                isLast = index == appUsageList.lastIndex
            )

            view.layoutParams =
                LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, weight).apply {
                    marginEnd = if (index != appUsageList.lastIndex) 2 else 0
                }
            container.addView(view)
        }
    }

    private fun createRoundedSegment(
        context: Context,
        color: Int,
        isFirst: Boolean,
        isLast: Boolean
    ): View {
        val radius = 50f
        val drawable = GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            setColor(color)
            cornerRadii = when {
                isFirst && isLast -> FloatArray(8) { radius }
                isFirst -> floatArrayOf(radius, radius, 0f, 0f, 0f, 0f, radius, radius)
                isLast -> floatArrayOf(0f, 0f, radius, radius, radius, radius, 0f, 0f)
                else -> FloatArray(8)
            }
        }
        return View(context).apply {
            layoutParams = LinearLayout.LayoutParams(
                0,
                ViewGroup.LayoutParams.MATCH_PARENT, // ensures it fills height properly
                1f
            )
            background = drawable
        }
    }

    private fun formatTime(millis: Long): String {
        val seconds = millis / 1000
        val secs = seconds % 60
        val minutes = seconds / 60
        val mins = minutes % 60
        val hours = minutes / 60
        return when {
            hours >= 10 -> "${hours}h ${mins}m"
            hours >= 1 -> " ${hours}h ${mins}m"
            mins >= 10 -> "${mins}m"
            mins >= 1 -> " ${mins}m"
            secs >= 10 -> "${secs}s"
            else -> " ${secs}s"
        }
    }

    private fun showThemeDialog() {
        ThemeManager.applySavedTheme(this)
        val dialogView = layoutInflater.inflate(R.layout.activity_sub_options, null)
        val dialog = AlertDialog.Builder(this)
            .setView(dialogView)
            .setCancelable(true)
            .create()

        dialog.window?.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
        dialogView.findViewById<RadioButton>(R.id.radioDark).setOnClickListener {
            val mode = AppCompatDelegate.MODE_NIGHT_YES
            ThemeManager.saveThemeMode(this, mode)
            recreate()
            dialog.dismiss()
        }
        dialogView.findViewById<RadioButton>(R.id.radioLight).setOnClickListener {
            val mode = AppCompatDelegate.MODE_NIGHT_NO
            ThemeManager.saveThemeMode(this, mode)
            recreate()
            dialog.dismiss()
        }
        dialogView.findViewById<RadioButton>(R.id.radioSystem).setOnClickListener {
            val mode = AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM
            ThemeManager.saveThemeMode(this, mode)
            recreate()
            dialog.dismiss()
        }

        when (ThemeManager.getSavedThemeMode(this)) {
            AppCompatDelegate.MODE_NIGHT_YES -> dialogView.findViewById<RadioButton>(R.id.radioDark)?.isChecked =
                true

            AppCompatDelegate.MODE_NIGHT_NO -> dialogView.findViewById<RadioButton>(R.id.radioLight)?.isChecked =
                true

            else -> dialogView.findViewById<RadioButton>(R.id.radioSystem)?.isChecked = true
        }

        dialog.show()
    }

    private fun showGesturesDialog() {
        val dialogView = layoutInflater.inflate(R.layout.gesture_dialog, null)
        val dialog = AlertDialog.Builder(this)
            .setView(dialogView)
            .setCancelable(true)
            .create()

        lockSwitch = dialogView.findViewById(R.id.lockSwitch)
        doubleTapSwitch = dialogView.findViewById(R.id.doubleTapSwitch)

        if (!AppAccessibilityService.isAccessibilityServiceEnabled()) {
            SharedPreferencesManager.setSwipeToLockEnabled(this, false)
            SharedPreferencesManager.setDoubleTapToLockEnabled(this, false)
        }

        lockSwitch?.isChecked = SharedPreferencesManager.isSwipeToLockEnabled(this)
        doubleTapSwitch?.isChecked = SharedPreferencesManager.isDoubleTapToLockEnabled(this)

        dialog.window?.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))

        lockSwitch?.setOnCheckedChangeListener { _, isChecked ->
            SharedPreferencesManager.setSwipeToLockEnabled(this, isChecked)
            if (isChecked) {
                if (!AppAccessibilityService.isAccessibilityServiceEnabled()) {
                    promptAccessibilitySettings(this)
                } else {
                    SharedPreferencesManager.setSwipeToLockEnabled(this, true)
                    Toast.makeText(
                        this,
                        "'Swipe right to lock phone' is enabled",
                        Toast.LENGTH_SHORT
                    ).show()
                }
            } else {
                SharedPreferencesManager.setSwipeToLockEnabled(this, false)
                Toast.makeText(this, "'Swipe right to lock phone' is disabled", Toast.LENGTH_SHORT)
                    .show()
            }
        }

        doubleTapSwitch?.setOnCheckedChangeListener { _, isChecked ->
            SharedPreferencesManager.setDoubleTapToLockEnabled(this, isChecked)
            if (isChecked) {
                if (!AppAccessibilityService.isAccessibilityServiceEnabled()) {
                    promptAccessibilitySettings(this)
                } else {
                    SharedPreferencesManager.setDoubleTapToLockEnabled(this, true)
                    Toast.makeText(
                        this,
                        "'Double tap to lock phone' is enabled",
                        Toast.LENGTH_SHORT
                    ).show()
                }
            } else {
                SharedPreferencesManager.setDoubleTapToLockEnabled(this, false)
                Toast.makeText(this, "'Double tap to lock phone' is disabled", Toast.LENGTH_SHORT)
                    .show()
            }
        }

        dialog.setOnDismissListener {
            lockSwitch = null
            doubleTapSwitch = null
        }

        dialog.show()
    }

    private fun showCustomizationDialog() {
        val dialogView = layoutInflater.inflate(R.layout.dialog_customization, null)
        val dialog = AlertDialog.Builder(this)
            .setView(dialogView)
            .setCancelable(true)
            .create()

        val appListTv: TextView = dialogView.findViewById(R.id.appListTv)
        val appListSc: SwitchCompat = dialogView.findViewById(R.id.appListSc)
        val rowSizeFour: RadioButton = dialogView.findViewById(R.id.rowSizeFour)
        val rowSizeFive: RadioButton = dialogView.findViewById(R.id.rowSizeFive)
        dialog.window?.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))

        dialogView.findViewById<SwitchCompat>(R.id.miniDrawerSwitch).isChecked = SharedPreferencesManager.isMiniDrawerEnabled(this)
        dialogView.findViewById<SwitchCompat>(R.id.applyThemedIcon).isChecked = SharedPreferencesManager.isThemedIconsEnabled(this)

        if (SharedPreferencesManager.isAppDrawerEnabled(this)) {
            dialogView.findViewById<RadioButton>(R.id.appListBtn).isChecked = false
            dialogView.findViewById<RadioButton>(R.id.appDrawerBtn).isChecked = true
            dialogView.findViewById<LinearLayout>(R.id.appDrawerRowSize).visibility = View.VISIBLE
            appListTv.text = "Hide app name"
            appListSc.isChecked = SharedPreferencesManager.isHideAppNameEnabled(this)

            if (SharedPreferencesManager.isLandscapeMode(this)) {
                rowSizeFour.text = "6"
                rowSizeFive.text = "7"
                if (SharedPreferencesManager.getAppDrawerRowSizeLandscape(this) == 6) {
                    rowSizeFour.isChecked = true
                    rowSizeFive.isChecked = false
                } else if (SharedPreferencesManager.getAppDrawerRowSizeLandscape(this) == 7) {
                    rowSizeFour.isChecked = false
                    rowSizeFive.isChecked = true
                } else {
                    rowSizeFour.isChecked = false
                    rowSizeFive.isChecked = false
                }
            } else {
                rowSizeFour.text = "4"
                rowSizeFive.text = "5"
                if (SharedPreferencesManager.getAppDrawerRowSize(this) == 4) {
                    rowSizeFour.isChecked = true
                    rowSizeFive.isChecked = false
                } else if (SharedPreferencesManager.getAppDrawerRowSize(this) == 5) {
                    rowSizeFour.isChecked = false
                    rowSizeFive.isChecked = true
                } else {
                    rowSizeFour.isChecked = false
                    rowSizeFive.isChecked = false
                }
            }

        } else {
            dialogView.findViewById<RadioButton>(R.id.appListBtn).isChecked = true
            dialogView.findViewById<RadioButton>(R.id.appDrawerBtn).isChecked = false
            dialogView.findViewById<LinearLayout>(R.id.appDrawerRowSize).visibility = View.GONE
            appListTv.text = "Show app icon"
            appListSc.isChecked = SharedPreferencesManager.isShowAppIconEnabled(this)
        }

        if (SharedPreferencesManager.isMiniDrawerEnabled(this)) {
            dialogView.findViewById<LinearLayout>(R.id.miniDrawerAppName).visibility = View.VISIBLE
            dialogView.findViewById<LinearLayout>(R.id.miniDrawerAppCount).visibility = View.VISIBLE
            dialogView.findViewById<SwitchCompat>(R.id.miniDrawerAppNameSwitch).isChecked = SharedPreferencesManager.isShowMiniAppNameEnabled(this)
            dialogView.findViewById<TextView>(R.id.countTextView).text = SharedPreferencesManager.getMiniAppDrawerCount(this).toString()
        } else {
            dialogView.findViewById<LinearLayout>(R.id.miniDrawerAppName).visibility = View.GONE
            dialogView.findViewById<LinearLayout>(R.id.miniDrawerAppCount).visibility = View.GONE
        }

        if (SharedPreferencesManager.getAppIconShape(this) == "round") {
            dialogView.findViewById<RadioButton>(R.id.roundAppIcon).isChecked = true
            dialogView.findViewById<RadioButton>(R.id.squricleAppIcon).isChecked = false
        } else {
            dialogView.findViewById<RadioButton>(R.id.roundAppIcon).isChecked = false
            dialogView.findViewById<RadioButton>(R.id.squricleAppIcon).isChecked = true
        }

        dialogView.findViewById<RadioButton>(R.id.appListBtn).setOnClickListener {
            SharedPreferencesManager.setAppDrawerEnabled(this, false)
            appListTv.text = "Show app icon"
            appListSc.isChecked = SharedPreferencesManager.isShowAppIconEnabled(this)
            dialogView.findViewById<LinearLayout>(R.id.appDrawerRowSize).visibility = View.GONE
        }

        dialogView.findViewById<RadioButton>(R.id.appDrawerBtn).setOnClickListener {
            SharedPreferencesManager.setAppDrawerEnabled(this, true)
            appListTv.text = "Hide app name"
            appListSc.isChecked = SharedPreferencesManager.isHideAppNameEnabled(this)
            dialogView.findViewById<LinearLayout>(R.id.appDrawerRowSize).visibility = View.VISIBLE

            if (SharedPreferencesManager.isLandscapeMode(this)) {
                if (SharedPreferencesManager.getAppDrawerRowSizeLandscape(this) == 6) {
                    rowSizeFour.isChecked = true
                    rowSizeFive.isChecked = false
                } else if (SharedPreferencesManager.getAppDrawerRowSizeLandscape(this) == 7) {
                    rowSizeFour.isChecked = false
                    rowSizeFive.isChecked = true
                } else {
                    rowSizeFour.isChecked = false
                    rowSizeFive.isChecked = false
                }
            } else {
                if (SharedPreferencesManager.getAppDrawerRowSize(this) == 4) {
                    rowSizeFour.isChecked = true
                    rowSizeFive.isChecked = false
                } else if (SharedPreferencesManager.getAppDrawerRowSize(this) == 5) {
                    rowSizeFour.isChecked = false
                    rowSizeFive.isChecked = true
                } else {
                    rowSizeFour.isChecked = false
                    rowSizeFive.isChecked = false
                }
            }
        }

        appListSc.setOnCheckedChangeListener { _, isChecked ->
            if (SharedPreferencesManager.isAppDrawerEnabled(this)) {
                SharedPreferencesManager.setHideAppNameEnabled(this, isChecked)
            } else {
                SharedPreferencesManager.setShowAppIconEnabled(this, isChecked)
            }
        }

        dialogView.findViewById<RadioButton>(R.id.rowSizeFour).setOnClickListener {
            SharedPreferencesManager.setAppDrawerRowSize(this, 4)
            SharedPreferencesManager.setAppDrawerRowSizeLandscape(this, 6)
        }

        dialogView.findViewById<RadioButton>(R.id.rowSizeFive).setOnClickListener {
            SharedPreferencesManager.setAppDrawerRowSize(this, 5)
            SharedPreferencesManager.setAppDrawerRowSizeLandscape(this, 7)
        }

        dialogView.findViewById<SwitchCompat>(R.id.miniDrawerSwitch).setOnCheckedChangeListener { _, isChecked ->
            SharedPreferencesManager.setMiniDrawerEnabled(this, isChecked)
            if (isChecked) {
                dialogView.findViewById<LinearLayout>(R.id.miniDrawerAppName).visibility = View.VISIBLE
                dialogView.findViewById<LinearLayout>(R.id.miniDrawerAppCount).visibility = View.VISIBLE
                dialogView.findViewById<SwitchCompat>(R.id.miniDrawerAppNameSwitch).isChecked = SharedPreferencesManager.isShowMiniAppNameEnabled(this)
                dialogView.findViewById<TextView>(R.id.countTextView).text = SharedPreferencesManager.getMiniAppDrawerCount(this).toString()
            } else {
                dialogView.findViewById<LinearLayout>(R.id.miniDrawerAppName).visibility = View.GONE
                dialogView.findViewById<LinearLayout>(R.id.miniDrawerAppCount).visibility = View.GONE
            }
        }

        dialogView.findViewById<SwitchCompat>(R.id.miniDrawerAppNameSwitch).setOnCheckedChangeListener { _, isChecked ->
            SharedPreferencesManager.setShowMiniAppNameEnabled(this, isChecked)
        }

        dialogView.findViewById<ImageButton>(R.id.incrementButton).setOnClickListener {
            var count = SharedPreferencesManager.getMiniAppDrawerCount(this)
            if (count < 5) count++
            SharedPreferencesManager.setMiniAppDrawerCount(this, count)
            dialogView.findViewById<TextView>(R.id.countTextView).text = count.toString()
        }

        dialogView.findViewById<ImageButton>(R.id.decrementButton).setOnClickListener {
            var count = SharedPreferencesManager.getMiniAppDrawerCount(this)
            if (count > 0) count--
            SharedPreferencesManager.setMiniAppDrawerCount(this, count)
            dialogView.findViewById<TextView>(R.id.countTextView).text = count.toString()
        }

        dialogView.findViewById<SwitchCompat>(R.id.applyThemedIcon).setOnCheckedChangeListener { _, isChecked ->
            SharedPreferencesManager.setThemedIconsEnabled(this, isChecked)
        }

        dialogView.findViewById<RadioButton>(R.id.roundAppIcon).setOnClickListener {
            SharedPreferencesManager.setAppIconShape(this, "round")
        }

        dialogView.findViewById<RadioButton>(R.id.squricleAppIcon).setOnClickListener {
            SharedPreferencesManager.setAppIconShape(this, "squricle")
        }

        dialog.setOnDismissListener {
            SharedPreferencesManager.setRefreshViewEnabled(this, true)
        }

        dialog.show()
    }

    private fun promptUsageAccessSettings(context: Context) {
        val dialogView = LayoutInflater.from(context).inflate(R.layout.dialog_usage_prompt, null)
        val dialog = AlertDialog.Builder(context)
            .setView(dialogView)
            .setCancelable(true)
            .create()
        dialog.window?.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
        dialogView.findViewById<TextView>(R.id.btnOpen).setOnClickListener {
            val intent = Intent().apply {
                action = Settings.ACTION_USAGE_ACCESS_SETTINGS
                putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
            }
            usageStatsLauncher.launch(intent)
            dialog.dismiss()
        }

        dialogView.findViewById<TextView>(R.id.btnLater).setOnClickListener {
            dialog.dismiss()
        }

        dialog.setOnDismissListener {
            SharedPreferencesManager.setVisibilityToggleEnabled(this, false)
            visibilityToggle?.setImageResource(R.drawable.visibility_24px)
        }

        dialog.show()
    }

    private fun promptAccessibilitySettings(context: Context) {
        val dialogView = LayoutInflater.from(context).inflate(R.layout.dialog_accessibility_prompt, null)
        val dialog = AlertDialog.Builder(context)
            .setView(dialogView)
            .setCancelable(true)
            .create()
        dialog.window?.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
        dialogView.findViewById<TextView>(R.id.btnOpen).setOnClickListener {
            val intent = Intent().apply {
                action = Settings.ACTION_ACCESSIBILITY_SETTINGS
                putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
            }
            accessibilityLauncher.launch(intent)
            dialog.dismiss()
        }

        dialogView.findViewById<TextView>(R.id.btnLater).setOnClickListener {
            dialog.dismiss()
        }

        dialog.setOnDismissListener {
            SharedPreferencesManager.setSwipeToLockEnabled(this, false)
            SharedPreferencesManager.setDoubleTapToLockEnabled(this, false)
            lockSwitch?.isChecked = false
            doubleTapSwitch?.isChecked = false
        }

        dialog.show()
    }

    override fun dispatchTouchEvent(ev: MotionEvent): Boolean {
        gestureDetector.onTouchEvent(ev)
        return super.dispatchTouchEvent(ev)
    }

    inner class SwipeBackGestureListener : GestureDetector.SimpleOnGestureListener() {
        private val swipeThreshold = 100
        private val swipeVelocityThreshold = 100
        private val edgeSwipeThreshold = 50

        override fun onFling(
            e1: MotionEvent?,
            e2: MotionEvent,
            velocityX: Float,
            velocityY: Float
        ): Boolean {
            if (e1 == null) return false
            val startX = e1.x
            val diffX = e2.x - e1.x
            val diffY = e2.y - e1.y

            if (abs(diffX) > abs(diffY) &&
                abs(diffX) > swipeThreshold &&
                abs(velocityX) > swipeVelocityThreshold
            ) {
                if (diffX > 0 && startX < edgeSwipeThreshold) {
                    finish()
                    return true
                }
            }
            return false
        }
    }
}