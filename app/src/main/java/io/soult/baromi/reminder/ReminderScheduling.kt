package io.soult.baromi.reminder

import java.time.Instant
import java.time.ZoneId

/**
 * Pure scheduling arithmetic — no Android types — so it is exhaustively unit-testable.
 * Everything else in this package is thin glue over [AlarmScheduler].
 */
object ReminderScheduling {

    /**
     * The next instant matching [hour]:[minute] wall-clock in [zone], strictly after [now].
     * If today's time has already passed (or is exactly now), rolls to tomorrow.
     */
    fun nextTrigger(now: Instant, zone: ZoneId, hour: Int, minute: Int): Instant {
        val zonedNow = now.atZone(zone)
        var candidate = zonedNow.toLocalDate().atTime(hour, minute).atZone(zone)
        if (!candidate.toInstant().isAfter(now)) {
            candidate = candidate.plusDays(1)
        }
        return candidate.toInstant()
    }
}
