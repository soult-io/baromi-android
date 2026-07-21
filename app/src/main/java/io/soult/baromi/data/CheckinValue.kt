package io.soult.baromi.data

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index

/**
 * One category's 1..5 value on a check-in. Replaces the old fixed mood/energy/anxiety columns so
 * a check-in can carry a value per enabled category (built-in or custom). Deleting a check-in or a
 * category cascades its values away.
 */
@Entity(
    tableName = "checkin_value",
    primaryKeys = ["checkin_id", "category_id"],
    foreignKeys = [
        ForeignKey(
            entity = Checkin::class,
            parentColumns = ["id"],
            childColumns = ["checkin_id"],
            onDelete = ForeignKey.CASCADE,
        ),
        ForeignKey(
            entity = Category::class,
            parentColumns = ["id"],
            childColumns = ["category_id"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("category_id")],
)
data class CheckinValue(
    @ColumnInfo(name = "checkin_id") val checkinId: Long,
    @ColumnInfo(name = "category_id") val categoryId: Long,
    val value: Int,
) {
    init {
        ScaleSchema.requireInRange("value", value)
    }
}
