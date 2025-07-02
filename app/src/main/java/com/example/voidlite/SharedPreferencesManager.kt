package com.example.voidlite

import android.content.Context
import android.content.SharedPreferences

object SharedPreferencesManager {
    private const val PREFS_NAME = "MinimaPrefs"
    private const val KEY_SWIPE_TO_LOCK_ENABLED = "swipe_to_lock_enabled"
    private const val KEY_DOUBLE_TAP_TO_LOCK_ENABLED = "double_tap_to_lock_enabled"
    private const val KEY_IS_VISIBILITY_TOGGLE_ENABLED = "visibility_toggle_enabled"
    private const val KEY_IS_HOME_LAUNCHER = "is_home_launcher"
    private const val KEY_IS_APP_ICON_TOGGLE_ENABLED = "is_app_icon_toggle_enabled"
    private const val KEY_IS_APP_NAME_TOGGLE_ENABLED = "is_app_name_toggle_enabled"
    private const val KEY_IS_APP_DRAWER_ENABLED = "is_app_drawer_enabled"
    private const val KEY_IS_REFRESH_VIEW_ENABLED = "is_refresh_view_enabled"
    private const val KEY_IS_MINI_APP_NAME_TOGGLE_ENABLED = "is_mini_app_name_toggle_enabled"
    private const val KEY_IS_MINI_DRAWER_TOGGLE_ENABLED = "is_mini_drawer_toggle_enabled"

    private fun getPrefs(context: Context): SharedPreferences {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    }

    fun isSwipeToLockEnabled(context: Context): Boolean {
        return getPrefs(context).getBoolean(KEY_SWIPE_TO_LOCK_ENABLED, false)
    }

    fun isDoubleTapToLockEnabled(context: Context): Boolean {
        return getPrefs(context).getBoolean(KEY_DOUBLE_TAP_TO_LOCK_ENABLED, false)
    }

    fun setSwipeToLockEnabled(context: Context, enabled: Boolean) {
        getPrefs(context).edit().putBoolean(KEY_SWIPE_TO_LOCK_ENABLED, enabled).apply()
    }

    fun setDoubleTapToLockEnabled(context: Context, enabled: Boolean) {
        getPrefs(context).edit().putBoolean(KEY_DOUBLE_TAP_TO_LOCK_ENABLED, enabled).apply()
    }

    fun isVisibilityToggleEnabled(context: Context): Boolean {
        return getPrefs(context).getBoolean(KEY_IS_VISIBILITY_TOGGLE_ENABLED, false)
    }

    fun setVisibilityToggleEnabled(context: Context, enabled: Boolean) {
        getPrefs(context).edit().putBoolean(KEY_IS_VISIBILITY_TOGGLE_ENABLED, enabled).apply()
    }

    fun setHomeLauncher(context: Context, enabled: Boolean) {
        getPrefs(context).edit().putBoolean(KEY_IS_HOME_LAUNCHER, enabled).apply()
    }

    fun isShowAppIconEnabled(context: Context): Boolean {
        return getPrefs(context).getBoolean(KEY_IS_APP_ICON_TOGGLE_ENABLED, false)
    }

    fun setShowAppIconEnabled(context: Context, enabled: Boolean) {
        getPrefs(context).edit().putBoolean(KEY_IS_APP_ICON_TOGGLE_ENABLED, enabled).apply()
    }

    fun isHideAppNameEnabled(context: Context): Boolean {
        return getPrefs(context).getBoolean(KEY_IS_APP_NAME_TOGGLE_ENABLED, false)
    }

    fun setHideAppNameEnabled(context: Context, enabled: Boolean) {
        getPrefs(context).edit().putBoolean(KEY_IS_APP_NAME_TOGGLE_ENABLED, enabled).apply()
    }

    fun isAppDrawerEnabled(context: Context): Boolean {
        return getPrefs(context).getBoolean(KEY_IS_APP_DRAWER_ENABLED, false)
    }

    fun setAppDrawerEnabled(context: Context, enabled: Boolean) {
        getPrefs(context).edit().putBoolean(KEY_IS_APP_DRAWER_ENABLED, enabled).apply()
    }

    fun isRefreshViewEnabled(context: Context): Boolean {
        return getPrefs(context).getBoolean(KEY_IS_REFRESH_VIEW_ENABLED, false)
    }

    fun setRefreshViewEnabled(context: Context, enabled: Boolean) {
        getPrefs(context).edit().putBoolean(KEY_IS_REFRESH_VIEW_ENABLED, enabled).apply()
    }

    fun isShowMiniAppNameEnabled(context: Context): Boolean {
        return getPrefs(context).getBoolean(KEY_IS_MINI_APP_NAME_TOGGLE_ENABLED, false)
    }

    fun setShowMiniAppNameEnabled(context: Context, enabled: Boolean) {
        getPrefs(context).edit().putBoolean(KEY_IS_MINI_APP_NAME_TOGGLE_ENABLED, enabled).apply()
    }

    fun isMiniDrawerEnabled(context: Context): Boolean {
        return getPrefs(context).getBoolean(KEY_IS_MINI_DRAWER_TOGGLE_ENABLED, false)
    }

    fun setMiniDrawerEnabled(context: Context, enabled: Boolean) {
        getPrefs(context).edit().putBoolean(KEY_IS_MINI_DRAWER_TOGGLE_ENABLED, enabled).apply()
    }

    fun isThemedIconsEnabled(context: Context): Boolean {
        val sharedPrefs = context.getSharedPreferences("launcher_prefs", Context.MODE_PRIVATE)
        return sharedPrefs.getBoolean("themed_icons_enabled", false)
    }

    fun setThemedIconsEnabled(context: Context, enabled: Boolean) {
        val sharedPrefs = context.getSharedPreferences("launcher_prefs", Context.MODE_PRIVATE)
        sharedPrefs.edit().putBoolean("themed_icons_enabled", enabled).apply()
    }

    fun getMiniAppDrawerCount(context: Context): Int {
        val sharedPrefs = context.getSharedPreferences("launcher_prefs", Context.MODE_PRIVATE)
        return sharedPrefs.getInt("mini_app_drawer_count", 4)
    }

    fun setMiniAppDrawerCount(context: Context, count: Int) {
        val sharedPrefs = context.getSharedPreferences("launcher_prefs", Context.MODE_PRIVATE)
        sharedPrefs.edit().putInt("mini_app_drawer_count", count).apply()
    }

    fun getAppDrawerRowSize(context: Context): Int {
        val sharedPrefs = context.getSharedPreferences("launcher_prefs", Context.MODE_PRIVATE)
        return sharedPrefs.getInt("app_drawer_row_size", 4)
    }

    fun setAppDrawerRowSize(context: Context, count: Int) {
        val sharedPrefs = context.getSharedPreferences("launcher_prefs", Context.MODE_PRIVATE)
        sharedPrefs.edit().putInt("app_drawer_row_size", count).apply()
    }

    fun getAppIconShape(context: Context): String? {
        val sharedPrefs = context.getSharedPreferences("launcher_prefs", Context.MODE_PRIVATE)
        return sharedPrefs.getString("app_icon_shape", "round")
    }

    fun setAppIconShape(context: Context, shape: String) {
        val sharedPrefs = context.getSharedPreferences("launcher_prefs", Context.MODE_PRIVATE)
        sharedPrefs.edit().putString("app_icon_shape", shape).apply()
    }
}