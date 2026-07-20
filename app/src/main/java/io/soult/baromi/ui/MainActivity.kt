package io.soult.baromi.ui

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.view.Gravity
import android.widget.TextView
import io.soult.baromi.data.BaromiDatabase
import io.soult.baromi.data.ReminderRepository
import io.soult.baromi.notify.Notifications
import io.soult.baromi.reminder.AlarmScheduler
import kotlinx.coroutines.runBlocking

/**
 * Minimal launcher + notification target. Module B replaces the body with the real check-in
 * screen; for now it wires up the notification channel, the runtime permissions the reminder
 * engine needs, and arms the alarms on launch.
 */
class MainActivity : Activity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(
            TextView(this).apply {
                text = getString(io.soult.baromi.R.string.capture_placeholder)
                gravity = Gravity.CENTER
                textSize = 18f
            },
        )

        Notifications.ensureChannel(this)
        requestNotificationPermissionIfNeeded()
        promptForExactAlarmIfNeeded()
        armReminders()
    }

    private fun requestNotificationPermissionIfNeeded() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), REQ_POST_NOTIFICATIONS)
        }
    }

    /** On 33+ exact alarms need a one-time grant in system settings; route the user there once. */
    private fun promptForExactAlarmIfNeeded() {
        if (!AlarmScheduler.canScheduleExact(this) && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            runCatching {
                startActivity(
                    Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM, Uri.parse("package:$packageName")),
                )
            }
        }
    }

    private fun armReminders() {
        val appContext = applicationContext
        Thread {
            runBlocking {
                val repo = ReminderRepository(BaromiDatabase.getInstance(appContext).reminderTimeDao())
                repo.seedDefaultsIfEmpty()
                AlarmScheduler.scheduleAll(appContext, repo.all())
            }
        }.start()
    }

    private companion object {
        const val REQ_POST_NOTIFICATIONS = 1
    }
}
