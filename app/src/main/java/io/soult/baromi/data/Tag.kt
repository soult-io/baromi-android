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
        /** Seeded on first run; disable-only. */
        val DEFAULTS = listOf("poor sleep", "exercise", "social")
            .map { Tag(label = it, builtin = true, enabled = true) }
    }
}
