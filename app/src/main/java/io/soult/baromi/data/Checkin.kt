package io.soult.baromi.data

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * One wellbeing check-in. The per-category 1..5 values live in [CheckinValue] (one row per
 * enabled category), so a check-in itself only holds timing, an optional comment, and the lock.
 */
@Entity(tableName = "checkin")
data class Checkin(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    @ColumnInfo(name = "prompt_time") val promptTime: Long?,
    @ColumnInfo(name = "answered_time") val answeredTime: Long,
    val comment: String?,
    val locked: Boolean = false,
) {
    init {
        require((comment?.length ?: 0) <= COMMENT_MAX_LEN) {
            "comment exceeds $COMMENT_MAX_LEN chars"
        }
        require(answeredTime > 0) { "answeredTime must be a positive epoch-millis: $answeredTime" }
    }

    companion object {
        const val COMMENT_MAX_LEN = 500
    }
}
