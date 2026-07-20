package io.soult.baromi.data

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index

/**
 * Join row linking a [Checkin] to a [Tag]. Deleting either parent cascades to remove the
 * link (Room enables foreign-key enforcement by default), so no orphan links survive.
 */
@Entity(
    tableName = "checkin_tag",
    primaryKeys = ["checkin_id", "tag_id"],
    foreignKeys = [
        ForeignKey(
            entity = Checkin::class,
            parentColumns = ["id"],
            childColumns = ["checkin_id"],
            onDelete = ForeignKey.CASCADE,
        ),
        ForeignKey(
            entity = Tag::class,
            parentColumns = ["id"],
            childColumns = ["tag_id"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("tag_id")],
)
data class CheckinTag(
    @ColumnInfo(name = "checkin_id") val checkinId: Long,
    @ColumnInfo(name = "tag_id") val tagId: Long,
)
