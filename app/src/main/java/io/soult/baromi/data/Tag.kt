package io.soult.baromi.data

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/** A user-defined context tag (e.g. "work", "sleep-poor"). Labels are unique. */
@Entity(
    tableName = "tag",
    indices = [Index(value = ["label"], unique = true)],
)
data class Tag(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val label: String,
) {
    init {
        require(label.isNotBlank()) { "tag label must not be blank" }
    }

    companion object {
        /** Seeded on first run so the capture screen has tags to offer; editable later (Module E). */
        val DEFAULTS = listOf("work", "sleep-poor", "social", "exercise", "sick", "alone")
            .map { Tag(label = it) }
    }
}
