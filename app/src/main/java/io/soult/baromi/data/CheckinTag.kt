package io.soult.baromi.data

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index

/**
 * Join row linking a [Checkin] to a [Tag]. (Room enables foreign-key enforcement by default.)
 *
 * The two FKs are deliberately asymmetric to protect the longitudinal record:
 * - deleting a **check-in** CASCADEs — its tag links go with it, tags themselves stay.
 * - deleting a **tag** is RESTRICTed — you cannot silently strip a tag from historic
 *   check-ins; Module E must detach (or reassign) it first.
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
            onDelete = ForeignKey.RESTRICT,
        ),
    ],
    indices = [Index("tag_id")],
)
data class CheckinTag(
    @ColumnInfo(name = "checkin_id") val checkinId: Long,
    @ColumnInfo(name = "tag_id") val tagId: Long,
)
