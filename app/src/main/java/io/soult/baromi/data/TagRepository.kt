package io.soult.baromi.data

import android.database.sqlite.SQLiteConstraintException

/**
 * Reads and manages the tag list; seeds the defaults on first run. Built-in tags can be disabled
 * but not deleted; custom tags can be deleted. Only enabled tags are offered on the capture screen.
 */
class TagRepository(private val dao: TagDao) {

    enum class AddResult { ADDED, DUPLICATE, INVALID }

    suspend fun seedDefaultsIfEmpty() {
        if (dao.getAll().isEmpty()) {
            Tag.DEFAULTS.forEach { dao.insert(it) }
        }
    }

    suspend fun all(): List<Tag> = dao.getAll()

    suspend fun enabled(): List<Tag> = dao.getEnabled()

    /** Adds a custom trimmed label. Rejects blank and labels containing ';' (the CSV tag separator). */
    suspend fun add(label: String): AddResult {
        val trimmed = label.trim()
        if (trimmed.isEmpty() || trimmed.contains(';')) return AddResult.INVALID
        val id = dao.insert(Tag(label = trimmed, builtin = false, enabled = true))
        return if (id >= 0) AddResult.ADDED else AddResult.DUPLICATE
    }

    /** Enables or disables a tag (built-in or custom). */
    suspend fun setEnabled(tag: Tag, enabled: Boolean) {
        dao.update(tag.copy(enabled = enabled))
    }

    /**
     * Removes a custom tag. Built-in tags are never removable (disable-only). A tag referenced by
     * a check-in is kept (FK RESTRICT). Returns true if removed.
     */
    suspend fun remove(tag: Tag): Boolean {
        if (tag.builtin) return false
        return try {
            dao.delete(tag)
            true
        } catch (e: SQLiteConstraintException) {
            false
        }
    }
}
