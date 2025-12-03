package com.example.voidlite

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.view.View
import android.widget.ImageButton
import android.widget.TextView
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView

class HiddenAppsActivity : AppCompatActivity() {

    private lateinit var hiddenAppsView: RecyclerView
    private lateinit var backButton: ImageButton
    private lateinit var tvHiddenApps: TextView
    private lateinit var hiddenAppListAdapter: HiddenAppListAdapter

    private val hiddenAppsReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            val action = intent?.action
            if (action == Intent.ACTION_PACKAGE_REMOVED || action == Intent.ACTION_PACKAGE_FULLY_REMOVED) {
                val packageName = intent.data?.schemeSpecificPart

                // Check if the removed app is in our adapter
                val currentApps = hiddenAppListAdapter.getApps()
                val appToRemove = currentApps.find { it.packageName == packageName }

                if (appToRemove != null) {
                    // Remove from UI
                    hiddenAppListAdapter.removeApp(appToRemove)

                    // Update SharedPreferences (Clean up persistence)
                    // We do this here too to ensure HiddenAppsActivity handles it if MainActivity is paused
                    val hiddenPrefs = getSharedPreferences("hidden_list_prefs", MODE_PRIVATE)
                    val hiddenSet = hiddenPrefs.getStringSet("hidden_list_packages", mutableSetOf())?.toMutableSet()

                    if (hiddenSet != null && hiddenSet.contains(packageName)) {
                        hiddenSet.remove(packageName)
                        hiddenPrefs.edit().putStringSet("hidden_list_packages", hiddenSet).apply()
                    }

                    // Handle empty state visibility
                    if (hiddenAppListAdapter.itemCount == 0) {
                        tvHiddenApps.visibility = View.VISIBLE
                        hiddenAppsView.visibility = View.GONE
                    }
                }
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_hidden_apps)
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.hiddenAppsLayout)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        hiddenAppsView = findViewById(R.id.hiddenAppsView)
        backButton = findViewById(R.id.backButton)
        tvHiddenApps = findViewById(R.id.tvHiddenApps)

        backButton.setOnClickListener {
            finish()
        }

        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_PACKAGE_REMOVED)
            addAction(Intent.ACTION_PACKAGE_FULLY_REMOVED)
            addDataScheme("package")
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(hiddenAppsReceiver, filter, Context.RECEIVER_EXPORTED)
        } else {
            registerReceiver(hiddenAppsReceiver, filter)
        }

        if (MainActivity().loadHiddenListApps(this) == emptyList<ApplicationInfo>()) {
            tvHiddenApps.visibility = View.VISIBLE
            hiddenAppsView.visibility = View.GONE
        } else {
            tvHiddenApps.visibility = View.GONE
            hiddenAppsView.visibility = View.VISIBLE
            hiddenAppListAdapter = HiddenAppListAdapter(this, MainActivity().loadHiddenListApps(this).toMutableList(), packageManager,
                showApp = { app ->
                    hiddenAppListAdapter.removeApp(app)
                    MainActivity().saveListApps(this, MainActivity().loadListApps(this) + app)
                    MainActivity().saveHiddenListApps(this, hiddenAppListAdapter.getApps())
                    if (MainActivity().loadHiddenListApps(this) == emptyList<ApplicationInfo>()) {
                        tvHiddenApps.visibility = View.VISIBLE
                        hiddenAppsView.visibility = View.GONE
                    }
                })

            hiddenAppsView.layoutManager = LinearLayoutManager(this)
            hiddenAppsView.adapter = hiddenAppListAdapter
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        unregisterReceiver(hiddenAppsReceiver)
    }
}
