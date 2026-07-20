package io.soult.baromi.data

import android.database.sqlite.SQLiteConstraintException

/** Reads and manages the tag list; seeds the defaults on first run. */
class TagRepository(private val dao: TagDao) {

    enum class AddResult { ADDED, DUPLICATE, INVALID }

    suspend fun seedDefaultsIfEmpty() {
        if (dao.getAll().isEmpty()) {
            Tag.DEFAULTS.forEach { dao.insert(it) }
        }
    }

    suspend fun all(): List<Tag> = dao.getAll()

    /** Adds a trimmed label. Rejects blank and labels containing ';' (the CSV tag separator). */
    suspend fun add(label: String): AddResult {
        val trimmed = label.trim()
        if (trimmed.isEmpty() || trimmed.contains(';')) return AddResult.INVALID
        return if (dao.insert(Tag(label = trimmed)) >= 0) AddResult.ADDED else AddResult.DUPLICATE
    }

    /** Removes a tag unless it is referenced by a check-in (FK RESTRICT). Returns true if removed. */
    suspend fun remove(tag: Tag): Boolean =
        try {
            dao.delete(tag)
            true
        } catch (e: SQLiteConstraintException) {
            false
        }
}
