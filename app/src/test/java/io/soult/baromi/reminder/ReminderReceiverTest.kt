package io.soult.baromi.reminder

import android.Manifest
import android.app.AlarmManager
import android.app.Application
import android.app.NotificationManager
import android.content.Intent
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf

@RunWith(RobolectricTestRunner::class)
class ReminderReceiverTest {

    private lateinit var app: Application
    private lateinit var alarmManager: AlarmManager

    @Before
    fun setup() {
        app = ApplicationProvider.getApplicationContext()
        shadowOf(app).grantPermissions(
            Manifest.permission.POST_NOTIFICATIONS,
            Manifest.permission.SCHEDULE_EXACT_ALARM,
            Manifest.permission.USE_EXACT_ALARM,
        )
        alarmManager = app.getSystemService(AlarmManager::class.java)
    }

    @Test
    fun firing_posts_a_prompt_and_rearms_the_next_occurrence() {
        val intent = Intent().apply {
            putExtra(AlarmScheduler.EXTRA_ID, 1L)
            putExtra(AlarmScheduler.EXTRA_HOUR, 14)
            putExtra(AlarmScheduler.EXTRA_MINUTE, 0)
            putExtra(AlarmScheduler.EXTRA_PROMPT_TIME, 1_000L)
        }

        ReminderReceiver().onReceive(app, intent)

        val nm = app.getSystemService(NotificationManager::class.java)
        assertEquals("prompt notification posted", 1, shadowOf(nm).size())
        assertEquals("next occurrence re-armed", 1, shadowOf(alarmManager).scheduledAlarms.size)
    }
}
