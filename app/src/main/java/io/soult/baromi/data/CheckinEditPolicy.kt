package io.soult.baromi.data

import java.time.Instant
import java.time.ZoneId

/**
 * When a check-in may still be edited. Per the spec it is editable only on the local calendar day
 * it was answered ("edit until midnight, then locked"), and never once explicitly [Checkin.locked].
 * Derived from the answered date rather than a background job, so no timer has to flip a flag.
 */
object CheckinEditPolicy {
    fun isEditable(checkin: Checkin, nowMillis: Long, zone: ZoneId): Boolean {
        if (checkin.locked) return false
        val answeredDate = Instant.ofEpochMilli(checkin.answeredTime).atZone(zone).toLocalDate()
        val nowDate = Instant.ofEpochMilli(nowMillis).atZone(zone).toLocalDate()
        return answeredDate == nowDate
    }
}
