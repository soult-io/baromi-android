package io.soult.baromi.reminder

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.Instant
import java.time.ZoneId

/** Pure java.time arithmetic — expected instants derived independently from the inputs. */
class ReminderSchedulingTest {

    private val utc = ZoneId.of("UTC")

    @Test
    fun time_later_today() {
        val now = Instant.parse("2026-07-20T08:00:00Z")
        assertEquals(
            Instant.parse("2026-07-20T14:00:00Z"),
            ReminderScheduling.nextTrigger(now, utc, 14, 0),
        )
    }

    @Test
    fun time_already_passed_rolls_to_tomorrow() {
        val now = Instant.parse("2026-07-20T15:00:00Z")
        assertEquals(
            Instant.parse("2026-07-21T14:00:00Z"),
            ReminderScheduling.nextTrigger(now, utc, 14, 0),
        )
    }

    @Test
    fun exactly_now_is_not_next_rolls_to_tomorrow() {
        val now = Instant.parse("2026-07-20T14:00:00Z")
        assertEquals(
            Instant.parse("2026-07-21T14:00:00Z"),
            ReminderScheduling.nextTrigger(now, utc, 14, 0),
        )
    }

    @Test
    fun midnight_boundary() {
        val now = Instant.parse("2026-07-20T23:30:00Z")
        assertEquals(
            Instant.parse("2026-07-21T00:00:00Z"),
            ReminderScheduling.nextTrigger(now, utc, 0, 0),
        )
    }

    @Test
    fun respects_zone_offset() {
        // 09:00 wall-clock in +02:00 is 07:00Z.
        val zone = ZoneId.of("+02:00")
        val now = Instant.parse("2026-07-20T04:00:00Z")
        assertEquals(
            Instant.parse("2026-07-20T07:00:00Z"),
            ReminderScheduling.nextTrigger(now, zone, 9, 0),
        )
    }
}
