package io.soult.baromi.data

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * One wellbeing check-in. The frozen capture contract for v1.
 *
 * Ranges and comment length are enforced in [init] so an invalid row can never be
 * constructed — the same guard runs whether the row comes from the UI or from Room reading
 * a cursor, keeping the on-device record trustworthy for later export.
 */
@Entity(tableName = "checkin")
data class Checkin(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    /** Epoch millis of the scheduled prompt this answers; null if opened without a prompt. */
    @ColumnInfo(name = "prompt_time") val promptTime: Long?,
    /** Epoch millis when the user actually answered. Always set. */
    @ColumnInfo(name = "answered_time") val answeredTime: Long,
    val mood: Int,
    val energy: Int,
    val anxiety: Int,
    val comment: String?,
    val locked: Boolean = false,
) {
    init {
        require(ScaleSchema.inRange(mood)) { "mood out of range 1..5: $mood" }
        require(ScaleSchema.inRange(energy)) { "energy out of range 1..5: $energy" }
        require(ScaleSchema.inRange(anxiety)) { "anxiety out of range 1..5: $anxiety" }
        require((comment?.length ?: 0) <= COMMENT_MAX_LEN) {
            "comment exceeds $COMMENT_MAX_LEN chars"
        }
        require(answeredTime > 0) { "answeredTime must be set" }
    }

    companion object {
        const val COMMENT_MAX_LEN = 500
    }
}
