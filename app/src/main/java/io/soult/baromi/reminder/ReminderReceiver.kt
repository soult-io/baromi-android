package io.soult.baromi.reminder

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import io.soult.baromi.notify.Notifications

/**
 * Fires when a reminder alarm goes off: posts the check-in prompt, then re-arms the next
 * occurrence of this same time (exact alarms are one-shot). No DB read needed — the hour/minute
 * ride along in the intent, keeping this path fast.
 */
class ReminderReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val id = intent.getLongExtra(AlarmScheduler.EXTRA_ID, -1L)
        val hour = intent.getIntExtra(AlarmScheduler.EXTRA_HOUR, -1)
        val minute = intent.getIntExtra(AlarmScheduler.EXTRA_MINUTE, -1)
        val promptTime = intent.getLongExtra(AlarmScheduler.EXTRA_PROMPT_TIME, 0L)

        Notifications.postCheckinPrompt(context, promptTime)

        if (id >= 0 && hour in 0..23 && minute in 0..59) {
            AlarmScheduler.scheduleOne(context, id, hour, minute)
        }
    }
}
