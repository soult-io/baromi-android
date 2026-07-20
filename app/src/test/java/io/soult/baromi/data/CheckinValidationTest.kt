package io.soult.baromi.data

import org.junit.Assert.assertThrows
import org.junit.Test

/** Pure JVM tests of the [Checkin] construction guards. No Android runtime needed. */
class CheckinValidationTest {

    private fun checkin(
        mood: Int = 3,
        energy: Int = 3,
        anxiety: Int = 3,
        comment: String? = null,
        answeredTime: Long = 1L,
    ) = Checkin(
        promptTime = null,
        answeredTime = answeredTime,
        mood = mood,
        energy = energy,
        anxiety = anxiety,
        comment = comment,
    )

    @Test
    fun valid_checkin_constructs() {
        val c = checkin(mood = 1, energy = 5, anxiety = 3, comment = "fine")
        // No exception; fields retained.
        assert(c.mood == 1 && c.energy == 5 && c.anxiety == 3)
    }

    @Test
    fun mood_above_range_rejected() {
        assertThrows(IllegalArgumentException::class.java) { checkin(mood = 6) }
    }

    @Test
    fun mood_below_range_rejected() {
        assertThrows(IllegalArgumentException::class.java) { checkin(mood = 0) }
    }

    @Test
    fun energy_out_of_range_rejected() {
        assertThrows(IllegalArgumentException::class.java) { checkin(energy = 6) }
    }

    @Test
    fun anxiety_out_of_range_rejected() {
        assertThrows(IllegalArgumentException::class.java) { checkin(anxiety = 0) }
    }

    @Test
    fun comment_at_limit_allowed() {
        val c = checkin(comment = "x".repeat(Checkin.COMMENT_MAX_LEN))
        assert(c.comment!!.length == Checkin.COMMENT_MAX_LEN)
    }

    @Test
    fun comment_over_limit_rejected() {
        assertThrows(IllegalArgumentException::class.java) {
            checkin(comment = "x".repeat(Checkin.COMMENT_MAX_LEN + 1))
        }
    }

    @Test
    fun unset_answered_time_rejected() {
        assertThrows(IllegalArgumentException::class.java) { checkin(answeredTime = 0L) }
    }
}
