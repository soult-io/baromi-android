package io.soult.baromi.reminder

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings

/**
 * The runtime permissions the reminder engine needs, requested from an Activity. Shared by the
 * capture screen (first-run) and the settings screen (where the user configures reminders).
 */
object ReminderPermissions {

    const val REQ_POST_NOTIFICATIONS = 1

    fun requestNotificationPermissionIfNeeded(activity: Activity) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            activity.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            activity.requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), REQ_POST_NOTIFICATIONS)
        }
    }

    /** On 31+ exact alarms need a one-time grant in system settings; route the user there once. */
    fun promptForExactAlarmIfNeeded(activity: Activity) {
        if (!AlarmScheduler.canScheduleExact(activity) && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            runCatching {
                activity.startActivity(
                    Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM, Uri.parse("package:${activity.packageName}")),
                )
            }
        }
    }
}
