package com.example.voidlite

import android.app.AppOpsManager
import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import android.content.Context
import android.content.pm.LauncherApps
import android.os.UserHandle
import android.os.UserManager
import android.util.Log
import java.util.Calendar

object UsageStatsManagerUtils {

    fun hasUsageStatsPermission(context: Context): Boolean {
        val appOps = context.getSystemService(Context.APP_OPS_SERVICE) as AppOpsManager
        val mode = appOps.checkOpNoThrow(
            AppOpsManager.OPSTR_GET_USAGE_STATS,
            android.os.Process.myUid(),
            context.packageName
        )
        return mode == AppOpsManager.MODE_ALLOWED
    }

    fun getTodayTopUsedApps(context: Context): Pair<Long, List<Pair<String, Long>>> {
        val usageStatsManager =
            context.getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager
        val packageManager = context.packageManager
        val launchablePackages = getAllLaunchableAppPackages(context)

        val calendar = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        val startTime = calendar.timeInMillis
        val endTime = System.currentTimeMillis()

        val usageMap = mutableMapOf<String, Long>()
        var lastUsedApp: String? = null
        var lastEventTime: Long = startTime

        val events = usageStatsManager.queryEvents(startTime, endTime)
        val event = UsageEvents.Event()

        while (events.hasNextEvent()) {
            events.getNextEvent(event)
            val packageName = event.packageName

            when (event.eventType) {
                UsageEvents.Event.MOVE_TO_FOREGROUND -> {
                    // Close previous session
                    lastUsedApp?.let {
                        val duration = event.timeStamp - lastEventTime
                        if (duration > 0 && duration < 1000 * 60 * 60 * 4) {
                            usageMap[it] = usageMap.getOrDefault(it, 0L) + duration
                        }
                    }
                    lastUsedApp = packageName
                    lastEventTime = event.timeStamp
                }

                UsageEvents.Event.MOVE_TO_BACKGROUND -> {
                    if (packageName == lastUsedApp) {
                        val duration = event.timeStamp - lastEventTime
                        if (duration > 0 && duration < 1000 * 60 * 60 * 4) {
                            usageMap[packageName] =
                                usageMap.getOrDefault(packageName, 0L) + duration
                        }
                        lastUsedApp = null
                        lastEventTime = event.timeStamp
                    }
                }
            }
        }

        // Include ongoing session if any
        lastUsedApp?.let {
            val duration = endTime - lastEventTime
            if (duration > 0 && duration < 1000 * 60 * 60 * 4) {
                usageMap[it] = usageMap.getOrDefault(it, 0L) + duration
            }
        }

        val filteredAppUsageMap = usageMap.filterKeys { it in launchablePackages }
        val totalScreenTime = usageMap.values.sum()
        val totalAppTime = filteredAppUsageMap.values.sum()
        val sortedAppUsage = filteredAppUsageMap.entries
            .sortedByDescending { it.value }

        val appString = usageMap.toList()
            .sortedByDescending { (_, value) -> value }
            .joinToString("\n") { (packageName, usageTime) ->
                var appName = try {
                    packageManager.getApplicationLabel(
                        packageManager.getApplicationInfo(packageName, 0)
                    ).toString()
                } catch (e: Exception) {
                    packageName
                }
                appName = normalizeAppName(appName)
                "$appName ${formatTime(usageTime)}"
            }

        Log.d(
            "UsageStatsManagerUtils", "Total screen time: ${formatTime(totalScreenTime)}\n" +
                    "Total app time: ${formatTime(totalAppTime)}\n" +
                    "Total Usage: $appString"
        )

        val top3 = sortedAppUsage
            .take(3)
            .map { (packageName, time) ->
                var appName = try {
                    packageManager.getApplicationLabel(
                        packageManager.getApplicationInfo(packageName, 0)
                    ).toString()
                } catch (e: Exception) {
                    packageName
                }
                appName = normalizeAppName(appName)
                appName to time
            }
            .toMutableList()

        // Sum remaining usage time as "Other"
        if (sortedAppUsage.size > 3) {
            val otherTotal = sortedAppUsage.drop(3).sumOf { it.value }
            top3.add("Other" to otherTotal)
        }

        return totalAppTime to top3
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

    private fun getAllLaunchableAppPackages(context: Context): Set<String> {
        val launcherApps = context.getSystemService(Context.LAUNCHER_APPS_SERVICE) as LauncherApps
        val userManager = context.getSystemService(Context.USER_SERVICE) as UserManager
        val users = userManager.userProfiles

        val packages = mutableSetOf<String>()
        for (user in users) {
            val activities = launcherApps.getActivityList(null, user as UserHandle)
            for (activityInfo in activities) {
                val pkg = activityInfo.applicationInfo.packageName
                if (pkg != context.packageName) {
                    packages.add(pkg)
                }
            }
        }
        return packages
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

}