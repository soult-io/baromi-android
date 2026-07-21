package io.soult.baromi.data

import androidx.room.Embedded
import androidx.room.Junction
import androidx.room.Relation

/** A check-in with its per-category values and its tags, for history + export. */
data class CheckinWithDetails(
    @Embedded val checkin: Checkin,
    @Relation(parentColumn = "id", entityColumn = "checkin_id")
    val values: List<CheckinValue>,
    @Relation(
        parentColumn = "id",
        entityColumn = "id",
        associateBy = Junction(
            value = CheckinTag::class,
            parentColumn = "checkin_id",
            entityColumn = "tag_id",
        ),
    )
    val tags: List<Tag>,
)
