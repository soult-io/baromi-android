package io.soult.baromi.data

import org.junit.Assert.assertThrows
import org.junit.Test

/** Pure JVM tests of the [Checkin] and [CheckinValue] construction guards. No Android runtime needed. */
class CheckinValidationTest {

    private fun checkin(
        comment: String? = null,
        answeredTime: Long = 1L,
    ) = Checkin(
        promptTime = null,
        answeredTime = answeredTime,
        comment = comment,
    )

    @Test
    fun valid_checkin_constructs() {
        val c = checkin(comment = "fine")
        assert(c.comment == "fine")
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

    @Test
    fun value_in_range_constructs() {
        val v = CheckinValue(checkinId = 1L, categoryId = 2L, value = 3)
        assert(v.value == 3)
    }

    @Test
    fun value_below_range_rejected() {
        assertThrows(IllegalArgumentException::class.java) {
            CheckinValue(checkinId = 1L, categoryId = 2L, value = 0)
        }
    }

    @Test
    fun value_above_range_rejected() {
        assertThrows(IllegalArgumentException::class.java) {
            CheckinValue(checkinId = 1L, categoryId = 2L, value = 6)
        }
    }

    @Test
    fun blank_category_name_rejected() {
        assertThrows(IllegalArgumentException::class.java) {
            Category(key = null, name = "  ", builtin = false, position = 0)
        }
    }
}
