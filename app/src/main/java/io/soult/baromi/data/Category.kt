package io.soult.baromi.data

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * A rating dimension (1..5). Built-in categories (Mood/Energy/Anxiety) can be disabled but not
 * deleted; custom categories (max 3) can be deleted. Built-ins carry a stable [key] that maps to
 * their custom face-icon sets in code; custom categories render 1..5 as number-keycap emoji.
 */
@Entity(
    tableName = "category",
    indices = [Index(value = ["name"], unique = true)],
)
data class Category(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val key: String?,
    val name: String,
    val builtin: Boolean,
    val enabled: Boolean = true,
    val position: Int,
) {
    init {
        require(name.isNotBlank()) { "category name must not be blank" }
    }

    companion object {
        const val MAX_CUSTOM = 3

        /** Longest category name, in characters. The Settings input uses the same limit (maxLength). */
        const val MAX_NAME_LENGTH = 40

        /** Trims and shortens [name] to [MAX_NAME_LENGTH]; used where names arrive from outside (backups). */
        fun normalizeName(name: String): String = name.trim().take(MAX_NAME_LENGTH).trim()

        val BUILTINS = listOf(
            Category(key = "mood", name = "Mood", builtin = true, position = 0),
            Category(key = "energy", name = "Energy", builtin = true, position = 1),
            Category(key = "anxiety", name = "Anxiety", builtin = true, position = 2),
        )
    }
}
