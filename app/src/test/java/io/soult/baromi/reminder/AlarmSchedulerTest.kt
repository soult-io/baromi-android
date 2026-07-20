package io.soult.baromi.reminder

import android.app.AlarmManager
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import io.soult.baromi.data.ReminderTime
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import java.time.Instant
import java.time.ZoneId

@RunWith(RobolectricTestRunner::class)
class AlarmSchedulerTest {

    private lateinit var context: Context
    private lateinit var alarmManager: AlarmManager
    private val utc = ZoneId.of("UTC")
    private val now = Instant.parse("2026-07-20T08:00:00Z")

    @Before
    fun setup() {
        context = ApplicationProvider.getApplicationContext()
        alarmManager = context.getSystemService(AlarmManager::class.java)
        shadowOf(alarmManager).setCanScheduleExactAlarms(true)
    }

    @Test
    fun schedule_one_arms_a_single_alarm() {
        AlarmScheduler.scheduleOne(context, id = 1L, hour = 14, minute = 0, now = now, zone = utc)
        assertEquals(1, shadowOf(alarmManager).scheduledAlarms.size)
    }

    @Test
    fun schedule_all_arms_one_alarm_per_time() {
        val times = listOf(
            ReminderTime(id = 1, hour = 9, minute = 0),
            ReminderTime(id = 2, hour = 14, minute = 0),
            ReminderTime(id = 3, hour = 20, minute = 0),
        )
        AlarmScheduler.scheduleAll(context, times, now = now, zone = utc)
        assertEquals(3, shadowOf(alarmManager).scheduledAlarms.size)
    }

    @Test
    fun not_scheduled_when_exact_alarms_denied() {
        shadowOf(alarmManager).setCanScheduleExactAlarms(false)
        AlarmScheduler.scheduleOne(context, id = 1L, hour = 14, minute = 0, now = now, zone = utc)
        assertEquals(0, shadowOf(alarmManager).scheduledAlarms.size)
    }

    @Test
    fun cancel_removes_a_scheduled_alarm() {
        AlarmScheduler.scheduleOne(context, id = 1L, hour = 14, minute = 0, now = now, zone = utc)
        AlarmScheduler.cancelOne(context, id = 1L, hour = 14, minute = 0)
        assertEquals(0, shadowOf(alarmManager).scheduledAlarms.size)
    }
}
