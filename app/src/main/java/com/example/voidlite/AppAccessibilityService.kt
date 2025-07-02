package com.example.voidlite

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.AccessibilityServiceInfo
import android.os.Build
import android.provider.Settings
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import androidx.annotation.RequiresApi

class AppAccessibilityService : AccessibilityService() {

    companion object {
        var instance: AppAccessibilityService? = null
            private set

        @RequiresApi(Build.VERSION_CODES.P)
        fun lockNowWithAccessibility() {
            instance?.lockScreenWithAccessibility()
        }

        fun isAccessibilityServiceEnabled(): Boolean {
            return instance?.isAccessibilityServiceEnabled() ?: false
        }

    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        instance = this

        val info = AccessibilityServiceInfo().apply {
            feedbackType = AccessibilityServiceInfo.FEEDBACK_GENERIC
            notificationTimeout = 100
        }

        serviceInfo = info
        Log.d("AppAccessibilityService", "Service connected")
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        // Required override, no action needed for now
    }

    override fun onInterrupt() {
        // Required override, no action needed for now
    }

    @RequiresApi(Build.VERSION_CODES.P)
    fun lockScreenWithAccessibility() {
        Log.d("AppAccessibilityService", "Attempting to lock screen using Accessibility")
        performGlobalAction(GLOBAL_ACTION_LOCK_SCREEN)
    }

    fun isAccessibilityServiceEnabled(): Boolean {
        val expectedComponent = "${packageName}/${AppAccessibilityService::class.java.name}"
        val enabledServices = Settings.Secure.getString(
            contentResolver,
            Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
        ) ?: return false

        return enabledServices.split(":").any { it.equals(expectedComponent, ignoreCase = true) }
    }

    override fun onDestroy() {
        super.onDestroy()
        if (instance == this) {
            instance = null
        }
        Log.d("AppAccessibilityService", "Service destroyed")
    }
}