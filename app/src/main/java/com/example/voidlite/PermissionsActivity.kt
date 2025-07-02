package com.example.voidlite

import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import android.widget.Button
import android.widget.ImageButton
import androidx.appcompat.app.AppCompatActivity

class PermissionsActivity : AppCompatActivity() {

    private lateinit var btnOpenUsage: Button
    private lateinit var btnOpenAccessibility: Button

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_permissions)

        val backButton = findViewById<ImageButton>(R.id.backButton)
        backButton.setOnClickListener {
            finish()
        }

        btnOpenUsage = findViewById(R.id.btnOpenUsage)
        btnOpenAccessibility = findViewById(R.id.btnOpenAccessibility)

        if (UsageStatsManagerUtils.hasUsageStatsPermission(this)) {
            btnOpenUsage.setBackgroundColor(getColor(R.color.disable))
            btnOpenUsage.text = "Remove permission"
        } else {
            btnOpenUsage.setBackgroundColor(getColor(R.color.enable))
            btnOpenUsage.text = "Grant permission"
        }

        if (AppAccessibilityService.isAccessibilityServiceEnabled()) {
            btnOpenAccessibility.setBackgroundColor(getColor(R.color.disable))
            btnOpenAccessibility.text = "Remove permission"
        } else {
            btnOpenAccessibility.setBackgroundColor(getColor(R.color.enable))
            btnOpenAccessibility.text = "Grant permission"
        }

        btnOpenUsage.setOnClickListener {
            val intent = Intent().apply {
                action = Settings.ACTION_USAGE_ACCESS_SETTINGS
                putExtra(Settings.EXTRA_APP_PACKAGE, packageName)
            }
            startActivity(intent)
        }

        btnOpenAccessibility.setOnClickListener {
            val intent = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)
            startActivity(intent)
        }
    }

    override fun onResume() {
        super.onResume()

        if (UsageStatsManagerUtils.hasUsageStatsPermission(this)) {
            btnOpenUsage.setBackgroundColor(getColor(R.color.disable))
            btnOpenUsage.text = "Remove permission"
        } else {
            btnOpenUsage.setBackgroundColor(getColor(R.color.enable))
            btnOpenUsage.text = "Grant permission"
        }

        if (AppAccessibilityService.isAccessibilityServiceEnabled()) {
            btnOpenAccessibility.setBackgroundColor(getColor(R.color.disable))
            btnOpenAccessibility.text = "Remove permission"
        } else {
            btnOpenAccessibility.setBackgroundColor(getColor(R.color.enable))
            btnOpenAccessibility.text = "Grant permission"
        }
    }
}