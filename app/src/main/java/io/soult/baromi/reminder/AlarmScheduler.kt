package io.soult.baromi.reminder

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.annotation.VisibleForTesting
import io.soult.baromi.data.ReminderTime
import java.time.Instant
import java.time.ZoneId

/**
 * Thin glue that arms one exact alarm per reminder time. Exact alarms are one-shot, so
 * [ReminderReceiver] re-arms the next occurrence each time one fires; [BootReceiver] re-arms
 * everything after a reboot (alarms don't survive it).
 */
object AlarmScheduler {

    const val EXTRA_ID = "io.soult.baromi.reminder.ID"
    const val EXTRA_HOUR = "io.soult.baromi.reminder.HOUR"
    const val EXTRA_MINUTE = "io.soult.baromi.reminder.MINUTE"

    // The alarm-hop prompt time (AlarmManager -> ReminderReceiver). Distinct key from the
    // tap-hop [io.soult.baromi.notify.Notifications.EXTRA_PROMPT_TIME] (notification -> Activity).
    const val EXTRA_PROMPT_TIME = "io.soult.baromi.reminder.PROMPT_TIME"

    /**
     * Test seam: when non-null, forces the exact-alarm capability answer. Production leaves it
     * null and uses the real OS check (Robolectric's shadow can't be driven directly here).
     */
    @VisibleForTesting(otherwise = VisibleForTesting.PRIVATE)
    var canScheduleExactOverride: Boolean? = null

    /** True when the OS will honor exact alarms (always pre-31; a special access grant on 31+). */
    fun canScheduleExact(context: Context): Boolean {
        canScheduleExactOverride?.let { return it }
        val am = context.getSystemService(AlarmManager::class.java)
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) am.canScheduleExactAlarms() else true
    }

    fun scheduleAll(
        context: Context,
        times: List<ReminderTime>,
        now: Instant = Instant.now(),
        zone: ZoneId = ZoneId.systemDefault(),
    ) {
        times.forEach { scheduleOne(context, it.id, it.hour, it.minute, now, zone) }
    }

    /** Arms the next occurrence of one reminder. No-ops (caller should prompt) if exact alarms aren't permitted. */
    fun scheduleOne(
        context: Context,
        id: Long,
        hour: Int,
        minute: Int,
        now: Instant = Instant.now(),
        zone: ZoneId = ZoneId.systemDefault(),
    ) {
        if (!canScheduleExact(context)) return
        val am = context.getSystemService(AlarmManager::class.java)
        val triggerAt = ReminderScheduling.nextTrigger(now, zone, hour, minute).toEpochMilli()
        // The exact-alarm grant can be revoked between the capability check above and this call
        // (a documented Android race). Swallow the resulting SecurityException so a background
        // re-arm can't crash the app; the reminder simply won't fire until the grant returns.
        try {
            am.setExactAndAllowWhileIdle(
                AlarmManager.RTC_WAKEUP,
                triggerAt,
                pendingIntent(context, id, hour, minute, triggerAt),
            )
        } catch (e: SecurityException) {
            return
        }
    }

    fun cancelOne(context: Context, id: Long, hour: Int, minute: Int) {
        val am = context.getSystemService(AlarmManager::class.java)
        am.cancel(pendingIntent(context, id, hour, minute, 0L))
    }

    private fun pendingIntent(
        context: Context,
        id: Long,
        hour: Int,
        minute: Int,
        triggerAt: Long,
    ): PendingIntent {
        val intent = Intent(context, ReminderReceiver::class.java).apply {
            putExtra(EXTRA_ID, id)
            putExtra(EXTRA_HOUR, hour)
            putExtra(EXTRA_MINUTE, minute)
            putExtra(EXTRA_PROMPT_TIME, triggerAt)
        }
        // Request code = reminder id keeps each alarm independently addressable for cancel/replace.
        return PendingIntent.getBroadcast(
            context,
            id.toInt(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }
}
