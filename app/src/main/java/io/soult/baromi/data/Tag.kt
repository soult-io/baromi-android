package io.soult.baromi.data

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * A context tag. Built-in tags (seeded) can be disabled but not deleted; custom tags can be
 * deleted. Only enabled tags are offered on the capture screen. Labels are unique.
 */
@Entity(
    tableName = "tag",
    indices = [Index(value = ["label"], unique = true)],
)
data class Tag(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val label: String,
    val builtin: Boolean = false,
    val enabled: Boolean = true,
) {
    init {
        require(label.isNotBlank()) { "tag label must not be blank" }
    }

    companion object {
        /**
         * Seeded on first run; disable-only. Changing this set needs a new migration (see
         * `MIGRATION_3_4`) to reconcile existing databases — do not edit an old migration, which
         * keeps a frozen copy of the labels it retired/established and may diverge from this list.
         */
        val DEFAULTS = listOf("poor sleep", "exercise", "social")
            .map { Tag(label = it, builtin = true, enabled = true) }
    }
}
