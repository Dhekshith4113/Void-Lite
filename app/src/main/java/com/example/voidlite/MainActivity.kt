package com.example.voidlite

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.LauncherApps
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.os.Build
import android.os.Bundle
import android.os.UserHandle
import android.os.UserManager
import android.view.DragEvent
import android.view.GestureDetector
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.ViewTreeObserver
import android.widget.FrameLayout
import android.widget.ImageButton
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.annotation.RequiresApi
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import kotlin.math.abs

class MainActivity : AppCompatActivity(), GradientUpdateListener {
    private lateinit var recyclerView: RecyclerView
    private lateinit var listAdapter: AppListAdapter
    private lateinit var gestureDetector: GestureDetector
    private lateinit var drawerGestureDetector: GestureDetector
    private lateinit var drawerAdapter: AppDrawerAdapter
    private lateinit var drawerRecyclerView: RecyclerView

    private var needRefresh = false
    private var toastShownThisDrag = false
    private var shouldMoveIndicator = true
    private var gradientOverlay: GradientOverlayView? = null

    @SuppressLint("ClickableViewAccessibility")
    @RequiresApi(Build.VERSION_CODES.UPSIDE_DOWN_CAKE)
    override fun onCreate(savedInstanceState: Bundle?) {
        ThemeManager.applySavedTheme(this)
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_main)
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.layoutMainActivity)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        val prefsInstalledApps = getSharedPreferences("installed_apps", MODE_PRIVATE)
        val firstTime = prefsInstalledApps.getBoolean("first_time", false)

        if (!firstTime) {
            saveListApps(this, loadListApps(this))
            prefsInstalledApps.edit().putBoolean("first_time", true).apply()
        }

//        if (!isDefaultLauncher(this)) {
//            finish()
//            startActivity(Intent(this, DefaultLauncherActivity::class.java))
//        }

        gestureDetector = GestureDetector(this, SwipeGestureListener())

        recyclerView = findViewById(R.id.recyclerView)

        listAdapter = AppListAdapter(this, loadListApps(this).toMutableList(), packageManager,
            refreshList = { app ->
                val appExists = isAppInstalled(app.packageName, packageManager)
                if (!appExists) {
                    needRefresh = true
                    listAdapter.removeApp(app)
                    saveListApps(this, listAdapter.getApps())
                }
            },
            hideApp = { app ->
                listAdapter.removeApp(app)
                saveHiddenListApps(this, loadHiddenListApps(this) + app)
                saveListApps(this, listAdapter.getApps())
                setupAlphabetScroller()
            })

        if (SharedPreferencesManager.isAppDrawerEnabled(this)) {
            if (SharedPreferencesManager.isLandscapeMode(this)) {
                val appDrawerRowSize = SharedPreferencesManager.getAppDrawerRowSizeLandscape(this)
                recyclerView.layoutManager = GridLayoutManager(this, appDrawerRowSize)
            } else {
                val appDrawerRowSize = SharedPreferencesManager.getAppDrawerRowSize(this)
                recyclerView.layoutManager = GridLayoutManager(this, appDrawerRowSize)
            }
        } else {
            recyclerView.layoutManager = LinearLayoutManager(this)
        }

        recyclerView.adapter = listAdapter
        setupGradientOverlay()

        listAdapter.onAppDragStarted = { app ->
            val currentApps = listAdapter.getApps()
            val index = currentApps.indexOfFirst { it.packageName == app.packageName }
            if (index != -1) {
                // Clone list to preserve order
                val updatedList = currentApps.toMutableList()
                updatedList.removeAt(index)
                listAdapter.updateData(updatedList)
            }
        }

        setupAlphabetScroller()

        drawerRecyclerView = findViewById(R.id.appDrawerRecyclerView)
        drawerRecyclerView.visibility = if (SharedPreferencesManager.isMiniDrawerEnabled(this)) {
            View.VISIBLE
        } else {
            View.GONE
        }

        drawerAdapter = AppDrawerAdapter(
            this,
            packageManager,
            loadDrawerApps().toMutableList(),
            ::saveDrawerApps,
            refreshList = { app ->
                val appExists = isAppInstalled(app.packageName, packageManager)
                if (!appExists) {
                    needRefresh = true
                    drawerAdapter.removeApp(app)
                    saveDrawerApps(drawerAdapter.getApps())
                }
            },
            hideApp = { app ->
                drawerAdapter.removeApp(app)
                saveHiddenListApps(this, loadHiddenListApps(this) + app)
                saveDrawerApps(drawerAdapter.getApps()
                    .filter { it.packageName != AppDrawerAdapter.DROP_INDICATOR_PACKAGE })
                setupAlphabetScroller()
            })

        drawerRecyclerView.apply {
            if (SharedPreferencesManager.isLandscapeMode(this@MainActivity)) {
                layoutManager = LinearLayoutManager(this@MainActivity, LinearLayoutManager.VERTICAL, false)
                addItemDecoration(CenterSpacingLandscape())
            } else {
                layoutManager = LinearLayoutManager(this@MainActivity, LinearLayoutManager.HORIZONTAL, false)
                addItemDecoration(CenterSpacingDecoration())
            }
            adapter = drawerAdapter
            itemAnimator = null // Remove animations

            // Ensure proper initial layout
            viewTreeObserver.addOnGlobalLayoutListener(object :
                ViewTreeObserver.OnGlobalLayoutListener {
                override fun onGlobalLayout() {
                    viewTreeObserver.removeOnGlobalLayoutListener(this)
                    post {
                        if (SharedPreferencesManager.isLandscapeMode(context)) {
                            CenterSpacingLandscape().invalidateSpacing()
                        } else {
                            CenterSpacingDecoration().invalidateSpacing()
                        }
                        invalidateItemDecorations()
                    }
                }
            })
        }

        drawerAdapter.onAppDragStarted = { app ->
            val currentApps = drawerAdapter.getApps()
            val index = currentApps.indexOfFirst { it.packageName == app.packageName }
            if (index != -1) {
                val updatedList = currentApps.toMutableList()
                updatedList.removeAt(index)
                drawerAdapter.updateData(updatedList)
            }
        }

        drawerRecyclerView.setOnDragListener { view, event ->
            when (event.action) {
                DragEvent.ACTION_DRAG_STARTED -> {
                    toastShownThisDrag = false
                    shouldMoveIndicator = true
                    true
                }

                DragEvent.ACTION_DRAG_ENTERED -> {
                    if (!drawerAdapter.hasDropIndicator()) {
                        drawerAdapter.insertDropIndicator(0)
                    }
                    true
                }

                DragEvent.ACTION_DRAG_LOCATION -> {
                    val x = event.x.toInt()
                    val y = event.y.toInt()
                    val recyclerView = view as RecyclerView
                    val draggedApp = event.localState as ApplicationInfo
                    val isAppFromDrawer =
                        drawerAdapter.getApps().any { it.packageName == draggedApp.packageName }
                    val drawerSize = SharedPreferencesManager.getMiniAppDrawerCount(this)
                    val currentRealApps = drawerAdapter.getApps()
                        .count { it.packageName != AppDrawerAdapter.DROP_INDICATOR_PACKAGE }

                    // Check if we can add more apps
                    if (!isAppFromDrawer && currentRealApps >= drawerSize) {
                        if (!toastShownThisDrag) {
                            Toast.makeText(
                                this,
                                "Cannot add more than $drawerSize apps",
                                Toast.LENGTH_SHORT
                            ).show()
                            drawerAdapter.removeDropIndicator()
                            listAdapter.addApp(draggedApp)
                            saveListApps(this, loadListApps(this))
                            shouldMoveIndicator = false
                            toastShownThisDrag = true
                        }
                        return@setOnDragListener true
                    }

                    if (!shouldMoveIndicator) return@setOnDragListener true

                    // Find which child view is being directly hovered over
                    var hoveredChild: View? = null
                    var hoveredPosition = -1

                    for (i in 0 until recyclerView.childCount) {
                        val child = recyclerView.getChildAt(i)

                        // Check if drag point is within the bounds of this child
                        if (x >= child.left && x <= child.right &&
                            y >= child.top && y <= child.bottom
                        ) {
                            hoveredChild = child
                            hoveredPosition = recyclerView.getChildAdapterPosition(child)
                            break
                        }
                    }

                    val currentDropIndex = drawerAdapter.getApps().indexOfFirst {
                        it.packageName == AppDrawerAdapter.DROP_INDICATOR_PACKAGE
                    }

                    if (hoveredChild != null && hoveredPosition != -1 && hoveredPosition < drawerAdapter.getApps().size) {
                        // We're directly hovering over a specific child
                        val hoveredApp = drawerAdapter.getApps()[hoveredPosition]

                        if (hoveredApp.packageName == AppDrawerAdapter.DROP_INDICATOR_PACKAGE) {
                            // Hovering over drop indicator - don't move it
                            return@setOnDragListener true
                        }

                        // We're hovering over a real app - determine where to place drop indicator
                        val targetPos = if (currentDropIndex == -1) {
                            // No drop indicator yet, place it based on drag direction
                            if (x < hoveredChild.left + hoveredChild.width / 2) {
                                hoveredPosition // Place before the hovered app
                            } else {
                                hoveredPosition + 1 // Place after the hovered app
                            }
                        } else {
                            // Drop indicator exists, move it to the opposite side of hovered app
                            if (currentDropIndex < hoveredPosition) {
                                // Drop indicator is to the left, move it to the right
                                hoveredPosition + 1
                            } else if (currentDropIndex > hoveredPosition) {
                                // Drop indicator is to the right, move it to the left
                                hoveredPosition
                            } else {
                                // Don't move if already adjacent
                                return@setOnDragListener true
                            }
                        }

                        val safeTargetPos = targetPos.coerceIn(0, drawerAdapter.itemCount)
                        if (currentDropIndex != safeTargetPos) {
                            if (currentDropIndex == -1) {
                                drawerAdapter.insertDropIndicator(safeTargetPos)
                            } else {
                                drawerAdapter.moveDropIndicator(safeTargetPos)
                            }
                        }
                    } else {
                        // Not hovering over any specific child
                        // Only insert drop indicator if it doesn't exist, at the edges
                        if (currentDropIndex == -1) {
                            val targetPos = if (x < recyclerView.width / 3) {
                                0 // Left edge
                            } else if (x > recyclerView.width * 2 / 3) {
                                drawerAdapter.itemCount // Right edge
                            } else {
                                // Middle area - don't insert drop indicator
                                return@setOnDragListener true
                            }
                            drawerAdapter.insertDropIndicator(targetPos)
                        }
                    }
                    true
                }

                DragEvent.ACTION_DROP -> {
                    val draggedApp = event.localState as ApplicationInfo
                    val dropIndex = drawerAdapter.getApps().indexOfFirst {
                        it.packageName == AppDrawerAdapter.DROP_INDICATOR_PACKAGE
                    }.takeIf { it != -1 } ?: drawerAdapter.itemCount

                    drawerAdapter.removeDropIndicator()

                    val isAppFromDrawer =
                        drawerAdapter.getApps().any { it.packageName == draggedApp.packageName }
                    val drawerSize = SharedPreferencesManager.getMiniAppDrawerCount(this)
                    val currentRealApps = drawerAdapter.getApps()
                        .count { it.packageName != AppDrawerAdapter.DROP_INDICATOR_PACKAGE }

                    if (view == drawerRecyclerView) {
                        if (!isAppFromDrawer && currentRealApps >= drawerSize) {
                            // Can't add more apps
                            return@setOnDragListener true
                        } else {
                            if (!isAppFromDrawer) {
                                // Moving from list to drawer
                                listAdapter.removeApp(draggedApp)
                            }
                            drawerAdapter.addAppAtPosition(draggedApp, dropIndex)
                        }
                    } else {
                        // Dropped into main app list
                        if (isAppFromDrawer) {
                            drawerAdapter.removeApp(draggedApp)
                            listAdapter.addApp(draggedApp)
                        }
                    }

                    saveDrawerApps(
                        drawerAdapter.getApps()
                            .filter { it.packageName != AppDrawerAdapter.DROP_INDICATOR_PACKAGE })
                    saveListApps(this, listAdapter.getApps())

                    // Force layout refresh
                    drawerRecyclerView.post {
                        if (SharedPreferencesManager.isLandscapeMode(this)) {
                            CenterSpacingLandscape().invalidateSpacing()
                        } else {
                            CenterSpacingDecoration().invalidateSpacing()
                        }
                        drawerRecyclerView.invalidateItemDecorations()
                    }

                    setupAlphabetScroller()

                    true
                }

                DragEvent.ACTION_DRAG_EXITED, DragEvent.ACTION_DRAG_ENDED -> {
                    drawerAdapter.removeDropIndicator()
                    toastShownThisDrag = false
                    shouldMoveIndicator = true
                    true
                }

                else -> true
            }
        }

        recyclerView.setOnDragListener { _, event ->
            when (event.action) {
                DragEvent.ACTION_DROP -> {
                    val app = event.localState as ApplicationInfo
                    drawerAdapter.removeApp(app)
                    listAdapter.addApp(app)
                    saveDrawerApps(
                        drawerAdapter.getApps()
                            .filter { it.packageName != AppDrawerAdapter.DROP_INDICATOR_PACKAGE })
                    saveListApps(this, listAdapter.getApps())
                    setupAlphabetScroller()
                    true
                }

                else -> true
            }
        }

        drawerGestureDetector =
            GestureDetector(this, object : GestureDetector.SimpleOnGestureListener() {
                override fun onDoubleTap(e: MotionEvent): Boolean {
                    val child = drawerRecyclerView.findChildViewUnder(e.x, e.y)
                    return if (child == null) {
                        if (SharedPreferencesManager.isDoubleTapToLockEnabled(this@MainActivity)) {
                            AppAccessibilityService.lockNowWithAccessibility()
                        } else {
                            Toast.makeText(
                                this@MainActivity,
                                "Please enable 'Double tap on the mini app drawer to Lock' in Gestures",
                                Toast.LENGTH_SHORT
                            ).show()
                        }
                        true
                    } else {
                        false
                    }
                }
            })

        drawerRecyclerView.setOnTouchListener { _, event ->
            drawerGestureDetector.onTouchEvent(event)
            false
        }

        val settingsButton = findViewById<ImageButton>(R.id.settingsButton)
        settingsButton.setOnClickListener {
            openSettings()
        }

    }

    @RequiresApi(Build.VERSION_CODES.TIRAMISU)
    override fun onResume() {
        super.onResume()
        clearExpiredNewAppTags()
        checkNewlyInstalledApps()
        if (needRefresh) {
            listAdapter.setApps(getNewlyInstalledApps())
            listAdapter.updateData(loadListApps(this).toMutableList())
            drawerAdapter.updateData(loadDrawerApps().toMutableList())
            setupAlphabetScroller()
            needRefresh = false
        }
//        if (!isDefaultLauncher(this)) {
//            finish()
//            startActivity(Intent(this, DefaultLauncherActivity::class.java))
//        }
        if (SharedPreferencesManager.isRefreshViewEnabled(this)) {
            if (!SharedPreferencesManager.isMiniDrawerEnabled(this)) {
                for (app in loadDrawerApps()) {
                    listAdapter.addApp(app)
                }
                saveListApps(this, listAdapter.getApps())
            } else {
                for (app in loadDrawerApps()) {
                    listAdapter.removeApp(app)
                }
                saveListApps(this, listAdapter.getApps())
            }
            val miniAppDrawerCount = SharedPreferencesManager.getMiniAppDrawerCount(this)
            if (miniAppDrawerCount < drawerAdapter.getApps().size) {
                for (i in 0 until drawerAdapter.getApps().size - miniAppDrawerCount) {
                    val extraApp = loadDrawerApps().last()
                    drawerAdapter.removeApp(extraApp)
                    listAdapter.addApp(extraApp)
                    saveDrawerApps(drawerAdapter.getApps())
                    saveListApps(this, listAdapter.getApps())
                }
            }
            finish()
            startActivity(Intent(this, MainActivity::class.java))
            SharedPreferencesManager.setRefreshViewEnabled(this, false)
        }
    }

    override fun updateGradients() {
        updateGradientAlphas()
    }

    private fun setupAlphabetScroller() {
        val scroller = findViewById<AlphabetScrollerView>(R.id.alphabetScroller)
        val apps = listAdapter.getApps()
        val usedAlphabets = apps.map {
            normalizeAppName(it.loadLabel(packageManager).toString()).first().uppercaseChar()
        }.distinct().sorted()
        val indexMap = getAlphabetIndexMap(apps)
        val layoutManager = recyclerView.layoutManager as LinearLayoutManager

        scroller.setup(usedAlphabets, indexMap, layoutManager)
        scroller.enableFloatingBubble(findViewById(R.id.layoutMainActivity))
        scroller.setGradientUpdateListener(this)   // IMPORTANT: Set the gradient update listener

        // NEW: Connect the adapter to the scroller for dimming functionality
        scroller.setAppListAdapter(listAdapter)

        // PERFORMANCE: Set RecyclerView reference for ultra-fast direct updates
        listAdapter.setRecyclerView(recyclerView)
    }

    private fun setupGradientOverlay() {
        // Create gradient overlay
        gradientOverlay = GradientOverlayView(this)

        // Get RecyclerView's current parent and position
        val currentParent = recyclerView.parent as ViewGroup
        val recyclerViewIndex = currentParent.indexOfChild(recyclerView)
        val recyclerViewParams = recyclerView.layoutParams

        // Remove RecyclerView from its current parent
        currentParent.removeView(recyclerView)

        // Create a FrameLayout container
        val frameContainer = FrameLayout(this)
        frameContainer.layoutParams = recyclerViewParams

        // Add RecyclerView to FrameLayout with MATCH_PARENT params
        val recyclerParams = FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.MATCH_PARENT,
            FrameLayout.LayoutParams.MATCH_PARENT
        )
        frameContainer.addView(recyclerView, recyclerParams)

        // Add gradient overlay to FrameLayout (on top of RecyclerView)
        val overlayParams = FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.MATCH_PARENT,
            FrameLayout.LayoutParams.MATCH_PARENT
        )
        frameContainer.addView(gradientOverlay, overlayParams)

        // Add the FrameLayout container back to the original parent
        currentParent.addView(frameContainer, recyclerViewIndex)

        // Set up scroll listener for smooth gradient updates
        recyclerView.addOnScrollListener(object : RecyclerView.OnScrollListener() {
            override fun onScrolled(recyclerView: RecyclerView, dx: Int, dy: Int) {
                super.onScrolled(recyclerView, dx, dy)
                updateGradientAlphas()
            }
        })

        // Initial gradient state
        updateGradientAlphas()
    }

    private fun updateGradientAlphas() {
        val (topAlpha, bottomAlpha) = recyclerView.calculateGradientAlphas()
        gradientOverlay?.updateGradients(topAlpha, bottomAlpha)
    }

    private fun RecyclerView.calculateGradientAlphas(): Pair<Float, Float> {
        val layoutManager = this.layoutManager as? LinearLayoutManager ?: return 0f to 1f

        // Check if we can scroll up (not at top)
        val canScrollUp = canScrollVertically(-1)

        // Check if we can scroll down (not at bottom)
        val canScrollDown = canScrollVertically(1)

        // Calculate precise scroll position
        val firstVisiblePosition = layoutManager.findFirstVisibleItemPosition()
        val firstVisibleView = layoutManager.findViewByPosition(firstVisiblePosition)

        val topAlpha = when {
            !canScrollUp -> 0f // At the very top
            firstVisiblePosition == 0 && firstVisibleView != null -> {
                // Near top, calculate based on first item offset
                val offset = firstVisibleView.top
                val viewHeight = firstVisibleView.height
                if (offset >= 0) 0f else (-offset.toFloat() / (viewHeight * 0.5f)).coerceIn(0f, 1f)
            }

            else -> 1f // Scrolled down significantly
        }

        val bottomAlpha = when {
            !canScrollDown -> 0f // At the very bottom
            else -> {
                // Calculate based on how close we are to bottom
                val lastVisiblePosition = layoutManager.findLastVisibleItemPosition()
                val itemCount = layoutManager.itemCount
                val lastVisibleView = layoutManager.findViewByPosition(lastVisiblePosition)

                if (lastVisiblePosition == itemCount - 1 && lastVisibleView != null) {
                    // Near bottom, calculate based on last item offset
                    val offset = lastVisibleView.bottom - height
                    val viewHeight = lastVisibleView.height
                    if (offset <= 0) 0f else (offset.toFloat() / (viewHeight * 0.5f)).coerceIn(
                        0f,
                        1f
                    )
                } else 1f
            }
        }

        return topAlpha to bottomAlpha
    }

    private fun getAlphabetIndexMap(apps: List<ApplicationInfo>): Map<Char, Int> {
        val map = mutableMapOf<Char, Int>()
        for ((index, app) in apps.withIndex()) {
            val label = normalizeAppName(app.loadLabel(packageManager).toString())
            val firstChar = label.firstOrNull()?.uppercaseChar() ?: continue
            if (!map.containsKey(firstChar)) {
                map[firstChar] = index
            }
        }
        return map
    }

    private fun checkNewlyInstalledApps() {
        val prefsNewApps = getSharedPreferences("new_apps", MODE_PRIVATE)
        val prefsList = getSharedPreferences("list_prefs", MODE_PRIVATE)
        val prefsDrawer = getSharedPreferences("drawer_prefs", MODE_PRIVATE)
        val prefsHiddenList = getSharedPreferences("hidden_list_prefs", MODE_PRIVATE)
        val currentAppList = prefsList.getStringSet("list_packages", emptySet()) ?: emptySet()
        val currentDrawerList = prefsDrawer.getString("drawer_ordered_packages", "")?.split(",")
            ?.filter { it.isNotBlank() } ?: emptyList()
        val currentHiddenList = prefsHiddenList.getStringSet("hidden_list_packages", emptySet()) ?: emptySet()

        val launcherApps = getSystemService(Context.LAUNCHER_APPS_SERVICE) as LauncherApps
        val userManager = getSystemService(Context.USER_SERVICE) as UserManager
        val users = userManager.userProfiles
        val currentPackage = applicationContext.packageName
        val newAppInfoList = mutableListOf<ApplicationInfo>()
        val newAppList = mutableListOf<String>()

        for (user in users) {
            val activities = launcherApps.getActivityList(null, user as UserHandle)
            for (activity in activities) {
                if (activity.applicationInfo.packageName != currentPackage) {   // exclude "Void" itself
                    newAppInfoList.add(activity.applicationInfo)
                    newAppList.add(activity.applicationInfo.packageName)
                }
            }
        }

        val filteredList = newAppInfoList.filter {
            packageManager.getLaunchIntentForPackage(it.packageName) != null &&
                    it.packageName !in currentDrawerList && it.packageName !in currentHiddenList
        }.sortedBy {
            normalizeAppName(it.loadLabel(packageManager).toString()).lowercase()
        }

        val newApps = newAppList.filterNot { it in currentAppList || it in currentDrawerList.toSet() || it in currentHiddenList }
        if (newApps.isNotEmpty()) {
            val editor = prefsNewApps.edit()
            val timestamp = System.currentTimeMillis()

            newApps.forEach { pkg ->
                editor.putLong("new_app_time_$pkg", timestamp)
            }

            editor.apply()
            prefsNewApps.edit().putStringSet("new_app_name", newApps.toSet()).apply()
            saveListApps(this, filteredList)
        }
        needRefresh = true
    }

    private fun getNewlyInstalledApps(): Set<String> {
        val prefs = getSharedPreferences("new_apps", MODE_PRIVATE)
        val allEntries = prefs.all
        println(allEntries)
        val now = System.currentTimeMillis()
        val oneDayMillis = 12 * 60 * 60 * 1000L

        return allEntries
            .filterKeys { it.startsWith("new_app_time_") }
            .mapNotNull { entry ->
                val pkg = entry.key.removePrefix("new_app_time_")
                val time = entry.value as? Long ?: return@mapNotNull null
                if (now - time < oneDayMillis) pkg else null
            }
            .toSet()
    }

    private fun clearExpiredNewAppTags() {
        val prefs = getSharedPreferences("new_apps", MODE_PRIVATE)
        val editor = prefs.edit()
        val now = System.currentTimeMillis()
        val halfDayMillis = 12 * 60 * 60 * 1000L

        prefs.all.forEach { (key, value) ->
            if (key.startsWith("new_app_time_")) {
                val time = value as? Long ?: return@forEach
                if (now - time >= halfDayMillis) {
                    editor.remove(key)
                }
            }
        }

        editor.apply()
    }

    private fun isAppInstalled(packageName: String, packageManager: PackageManager): Boolean {
        return try {
            packageManager.getPackageInfo(packageName, 0)
            true // No error? It's installed.
        } catch (e: PackageManager.NameNotFoundException) {
            false // Error? It's uninstalled.
        }
    }

    private fun saveDrawerApps(apps: List<ApplicationInfo>) {
        val orderedPackages = apps.joinToString(",") { it.packageName }
        getSharedPreferences("drawer_prefs", MODE_PRIVATE)
            .edit()
            .putString("drawer_ordered_packages", orderedPackages)
            .apply()
    }

    fun saveListApps(context: Context,apps: List<ApplicationInfo>) {
        val packageNames = apps.map { it.packageName }
        context.getSharedPreferences("list_prefs", MODE_PRIVATE)
            .edit()
            .putStringSet("list_packages", packageNames.toSet())
            .apply()
    }

    fun saveHiddenListApps(context: Context, apps: List<ApplicationInfo>) {
        val packageNames = apps.map { it.packageName }
        context.getSharedPreferences("hidden_list_prefs", MODE_PRIVATE)
            .edit()
            .putStringSet("hidden_list_packages", packageNames.toSet())
            .apply()
    }

    private fun loadDrawerApps(): List<ApplicationInfo> {
        val prefs = getSharedPreferences("drawer_prefs", MODE_PRIVATE)
        val packageList =
            prefs.getString("drawer_ordered_packages", "")?.split(",")?.filter { it.isNotBlank() }
                ?: emptyList()
        val launcherApps = getSystemService(Context.LAUNCHER_APPS_SERVICE) as LauncherApps
        val userManager = getSystemService(Context.USER_SERVICE) as UserManager
        val users = userManager.userProfiles

        val allAppsMap = mutableMapOf<String, ApplicationInfo>()
        for (user in users) {
            val activities = launcherApps.getActivityList(null, user as UserHandle)
            for (activity in activities) {
                allAppsMap[activity.applicationInfo.packageName] = activity.applicationInfo
            }
        }
        val drawerApps = mutableListOf<ApplicationInfo>()
        for (pkg in packageList) {
            allAppsMap[pkg]?.let { drawerApps.add(it) }
        }
        return drawerApps
    }

    fun loadListApps(context: Context): List<ApplicationInfo> {
        val prefs = context.getSharedPreferences("list_prefs", MODE_PRIVATE)
        val packageNames = prefs.getStringSet("list_packages", emptySet()) ?: emptySet()

        val launcherApps = context.getSystemService(Context.LAUNCHER_APPS_SERVICE) as LauncherApps
        val userManager = context.getSystemService(Context.USER_SERVICE) as UserManager
        val users = userManager.userProfiles
        val currentPackage = context.applicationContext.packageName
        val listApps = mutableListOf<ApplicationInfo>()

        if (packageNames.isEmpty()) {
            for (user in users) {
                val activities = launcherApps.getActivityList(null, user as UserHandle)
                for (activity in activities) {
                    try {
                        val appInfo = activity.applicationInfo
                        listApps.add(appInfo)
                    } catch (_: PackageManager.NameNotFoundException) {
                    }
                }
            }
            return listApps.filter {
                context.packageManager.getLaunchIntentForPackage(it.packageName) != null &&
                        it.packageName != currentPackage // exclude "Void" itself
            }
                .sortedBy { normalizeAppName(it.loadLabel(context.packageManager).toString()).lowercase() }
        } else {
            for (user in users) {
                val activities = launcherApps.getActivityList(null, user as UserHandle)
                for (activity in activities) {
                    val pkgName = activity.applicationInfo.packageName
                    if (pkgName in packageNames) {
                        listApps.add(activity.applicationInfo)
                    }
                }
            }
            return listApps.sortedBy {
                normalizeAppName(it.loadLabel(context.packageManager).toString()).lowercase()
            }
        }
    }

    fun loadHiddenListApps(context: Context): List<ApplicationInfo> {
        val prefs = context.getSharedPreferences("hidden_list_prefs", MODE_PRIVATE)
        val packageNames = prefs.getStringSet("hidden_list_packages", emptySet()) ?: emptySet()
        val launcherApps = context.getSystemService(Context.LAUNCHER_APPS_SERVICE) as LauncherApps
        val userManager = context.getSystemService(Context.USER_SERVICE) as UserManager
        val users = userManager.userProfiles

        val allAppsMap = mutableMapOf<String, ApplicationInfo>()
        for (user in users) {
            val activities = launcherApps.getActivityList(null, user as UserHandle)
            for (activity in activities) {
                allAppsMap[activity.applicationInfo.packageName] = activity.applicationInfo
            }
        }
        val drawerApps = mutableListOf<ApplicationInfo>()
        for (pkg in packageNames) {
            allAppsMap[pkg]?.let { drawerApps.add(it) }
        }
        return drawerApps.sortedBy {
            normalizeAppName(it.loadLabel(context.packageManager).toString()).lowercase()
        }
    }

    fun normalizeAppName(label: String): String {
        val prefixesToRemove = listOf("Samsung ", "Google ", "Galaxy ")
        var normalized = label

        for (prefix in prefixesToRemove) {
            if (label.startsWith(prefix, ignoreCase = true)) {
                normalized = normalized.removePrefix(prefix).trim()
                break
            }
        }

        return normalized
    }

    private fun isDefaultLauncher(context: Context): Boolean {
        val pm = context.packageManager
        val intent = Intent(Intent.ACTION_MAIN).apply {
            addCategory(Intent.CATEGORY_HOME)
        }
        val resolveInfo = pm.resolveActivity(intent, PackageManager.MATCH_DEFAULT_ONLY)
        return resolveInfo?.activityInfo?.packageName == context.packageName
    }

    override fun dispatchTouchEvent(ev: MotionEvent): Boolean {
        gestureDetector.onTouchEvent(ev)
        return super.dispatchTouchEvent(ev)
    }

    override fun onTouchEvent(event: MotionEvent?): Boolean {
        return event?.let { gestureDetector.onTouchEvent(it) } == true || super.onTouchEvent(event)
    }

    inner class SwipeGestureListener : GestureDetector.SimpleOnGestureListener() {
        private val swipeThreshold = 100
        private val swipeVelocityThreshold = 100
        private val edgeSwipeThreshold = 50  // px from edge to detect swipe

        @RequiresApi(Build.VERSION_CODES.P)
        override fun onFling(
            e1: MotionEvent?,
            e2: MotionEvent,
            velocityX: Float,
            velocityY: Float
        ): Boolean {
            if (e1 == null) return false

            val diffX = e2.x - e1.x
            val diffY = e2.y - e1.y

            // Swipe must start within left edgeSwipeThreshold
            val screenWidth = resources.displayMetrics.widthPixels

            if (abs(diffX) > abs(diffY) &&
                abs(diffX) > swipeThreshold &&
                abs(velocityX) > swipeVelocityThreshold
            ) {
                if (diffX < 0 && e1.x > screenWidth - edgeSwipeThreshold) {
                    // Swiped left (right → left)
                    openSettings()
                    return true
                }

                if (diffX > 0 && e1.x < edgeSwipeThreshold) {
                    // Swipe right → Lock screen
                    if (SharedPreferencesManager.isSwipeToLockEnabled(this@MainActivity)) {
                        AppAccessibilityService.lockNowWithAccessibility()
                    } else {
                        Toast.makeText(
                            this@MainActivity,
                            "Enable 'Swipe right to Lock' in Gestures",
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                    return true
                }
            }
            return false
        }

    }

    private fun openSettings() {
        startActivity(Intent(this, SettingsActivity::class.java))
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        if (newConfig.orientation == Configuration.ORIENTATION_LANDSCAPE) {
            SharedPreferencesManager.setLandscapeMode(this, true)
        } else if (newConfig.orientation == Configuration.ORIENTATION_PORTRAIT) {
            SharedPreferencesManager.setLandscapeMode(this, false)
        }
        recreate()
    }

}

interface GradientUpdateListener {
    fun updateGradients()
}