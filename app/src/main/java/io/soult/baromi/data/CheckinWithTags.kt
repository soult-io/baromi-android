package io.soult.baromi.data

import androidx.room.Embedded
import androidx.room.Junction
import androidx.room.Relation

/** A check-in together with its resolved tags, for read queries. */
data class CheckinWithTags(
    @Embedded val checkin: Checkin,
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
