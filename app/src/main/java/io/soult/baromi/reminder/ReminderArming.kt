package io.soult.baromi.reminder

import android.content.Context
import io.soult.baromi.data.BaromiDatabase
import io.soult.baromi.data.ReminderRepository

/**
 * Single source of truth for "make the schedule real": seed the default times on first run,
 * then arm an exact alarm for each. Called from the launcher on start and from [BootReceiver]
 * after reboot / clock changes — both routes go through here so the seed+arm contract lives once.
 */
suspend fun rearmAllReminders(context: Context) {
    val appContext = context.applicationContext
    val repo = ReminderRepository(BaromiDatabase.getInstance(appContext).reminderTimeDao())
    repo.seedDefaultsIfEmpty()
    AlarmScheduler.scheduleAll(appContext, repo.all())
}
