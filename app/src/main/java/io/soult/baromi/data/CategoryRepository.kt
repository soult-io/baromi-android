package io.soult.baromi.data

import android.database.sqlite.SQLiteConstraintException
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Reads and manages the category list; seeds the built-ins on first run. Built-in categories
 * (Mood/Energy/Anxiety) can be disabled but not deleted; custom categories (up to [Category.MAX_CUSTOM])
 * can be added and deleted. Only enabled categories are offered on the capture screen.
 */
class CategoryRepository(private val dao: CategoryDao) {

    enum class AddResult { ADDED, DUPLICATE, INVALID, MAX_REACHED }

    // Serializes the "last enabled" guard with its mutation so two rapid taps can't both pass the
    // check before either write commits and leave the enabled set empty (a TOCTOU race).
    private val guard = Mutex()

    suspend fun seedDefaultsIfEmpty() {
        if (dao.count() == 0) {
            Category.BUILTINS.forEach { dao.insert(it) }
        }
    }

    suspend fun all(): List<Category> = dao.getAll()

    suspend fun enabled(): List<Category> = dao.getEnabled()

    /**
     * True when removing [category] from the enabled set (by disabling or deleting it) would leave
     * no enabled categories at all. A check-in needs at least one category to rate, so the caller
     * must block the action and prompt the user to add or enable another first.
     */
    suspend fun isLastEnabled(category: Category): Boolean =
        category.enabled && dao.enabledCount() <= 1

    /**
     * Atomically disables [category] unless it is the last enabled one. Returns false (nothing
     * changed) when it was the last — the caller should surface the "keep at least one" alert.
     */
    suspend fun disableUnlessLast(category: Category): Boolean = guard.withLock {
        if (isLastEnabled(category)) return@withLock false
        dao.update(category.copy(enabled = false))
        true
    }

    /**
     * Atomically removes a custom [category] unless it is the last enabled one. Returns false
     * (nothing changed) when it was the last. Only wired to custom categories (built-ins have no
     * delete affordance), so a false return always means "blocked as the last enabled category".
     */
    suspend fun removeUnlessLast(category: Category): Boolean = guard.withLock {
        if (isLastEnabled(category)) return@withLock false
        dao.delete(category) // custom only; deleting cascades its historical values away
        true
    }

    /**
     * Adds a custom category. Rejects blank names, names containing ',' (the CSV column separator)
     * and names longer than [Category.MAX_NAME_LENGTH].
     */
    suspend fun add(name: String): AddResult {
        val trimmed = name.trim()
        if (trimmed.isEmpty() || trimmed.contains(',') || trimmed.length > Category.MAX_NAME_LENGTH) return AddResult.INVALID
        if (dao.customCount() >= Category.MAX_CUSTOM) return AddResult.MAX_REACHED
        val position = (dao.maxPosition() ?: -1) + 1
        val id = dao.insert(
            Category(key = null, name = trimmed, builtin = false, enabled = true, position = position),
        )
        return if (id >= 0) AddResult.ADDED else AddResult.DUPLICATE
    }

    /** Enables or disables a category (built-in or custom). */
    suspend fun setEnabled(category: Category, enabled: Boolean) {
        dao.update(category.copy(enabled = enabled))
    }

    /**
     * Removes a custom category. Built-ins are never removable (disable-only). Returns true if
     * removed. Deleting a custom category cascades its historical values away.
     */
    suspend fun remove(category: Category): Boolean {
        if (category.builtin) return false
        return try {
            dao.delete(category)
            true
        } catch (e: SQLiteConstraintException) {
            false
        }
    }
}
