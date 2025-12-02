package com.example.voidlite

import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
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

        if (MainActivity().loadHiddenListApps(this) == emptyList<ApplicationInfo>()) {
            tvHiddenApps.visibility = View.VISIBLE
            hiddenAppsView.visibility = View.GONE
        } else {
            tvHiddenApps.visibility = View.GONE
            hiddenAppsView.visibility = View.VISIBLE
            hiddenAppListAdapter = HiddenAppListAdapter(this, MainActivity().loadHiddenListApps(this).toMutableList(), packageManager,
                refreshList = { app ->
                    val appExists = isAppInstalled(app.packageName, packageManager)
                    if (!appExists) {
                        hiddenAppListAdapter.removeApp(app)
                        MainActivity().saveHiddenListApps(this, hiddenAppListAdapter.getApps())
                        hiddenAppListAdapter.updateData(MainActivity().loadHiddenListApps(this))
                    }
                },
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

    private fun isAppInstalled(packageName: String, packageManager: PackageManager): Boolean {
        return try {
            packageManager.getPackageInfo(packageName, 0)
            true // No error? It's installed.
        } catch (e: PackageManager.NameNotFoundException) {
            false // Error? It's uninstalled.
        }
    }
}
