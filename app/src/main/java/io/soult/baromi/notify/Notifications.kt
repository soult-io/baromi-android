package io.soult.baromi.notify

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import io.soult.baromi.ui.MainActivity

/** Builds and posts the "how are you?" check-in prompt. */
object Notifications {

    const val CHANNEL_ID = "checkin_reminders"

    // A single prompt id: a new prompt replaces any earlier unanswered one, matching the
    // "answerable until the next scheduled prompt" rule.
    const val NOTIFICATION_ID = 1001

    /** Extra on the tap intent carrying the scheduled prompt instant (epoch millis) into the capture screen. */
    const val EXTRA_PROMPT_TIME = "io.soult.baromi.notify.PROMPT_TIME"

    fun ensureChannel(context: Context) {
        val channel = NotificationChannel(
            CHANNEL_ID,
            "Check-in reminders",
            NotificationManager.IMPORTANCE_HIGH,
        ).apply {
            description = "Prompts to record how you're feeling"
        }
        context.getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
    }

    fun postCheckinPrompt(context: Context, promptTime: Long) {
        ensureChannel(context)
        if (!canPost(context)) return

        val tapIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra(EXTRA_PROMPT_TIME, promptTime)
        }
        val contentIntent = PendingIntent.getActivity(
            context,
            0,
            tapIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_popup_reminder)
            .setContentTitle("How are you right now?")
            .setContentText("Tap to record your check-in.")
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .setContentIntent(contentIntent)
            .build()

        context.getSystemService(NotificationManager::class.java)
            .notify(NOTIFICATION_ID, notification)
    }

    private fun canPost(context: Context): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) ==
            PackageManager.PERMISSION_GRANTED
}
