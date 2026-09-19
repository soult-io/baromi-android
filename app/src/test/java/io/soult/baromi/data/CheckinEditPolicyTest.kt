package io.soult.baromi.data

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant
import java.time.ZoneId

/** "Edit until midnight, then locked" — editability is derived from the answered calendar day. */
class CheckinEditPolicyTest {

    private val zone = ZoneId.of("UTC")
    private val now = Instant.parse("2026-09-19T12:00:00Z").toEpochMilli()

    private fun checkin(answered: String, locked: Boolean = false) = Checkin(
        promptTime = null,
        answeredTime = Instant.parse(answered).toEpochMilli(),
        comment = null,
        locked = locked,
    )

    @Test
    fun editable_when_answered_earlier_today() {
        assertTrue(CheckinEditPolicy.isEditable(checkin("2026-09-19T00:30:00Z"), now, zone))
    }

    @Test
    fun not_editable_after_midnight_the_next_day() {
        assertFalse(CheckinEditPolicy.isEditable(checkin("2026-09-18T23:30:00Z"), now, zone))
    }

    @Test
    fun locked_checkin_is_never_editable_even_same_day() {
        assertFalse(CheckinEditPolicy.isEditable(checkin("2026-09-19T09:00:00Z", locked = true), now, zone))
    }
}
