package io.soult.baromi.data

import android.database.sqlite.SQLiteConstraintException

/**
 * Reads and manages the category list; seeds the built-ins on first run. Built-in categories
 * (Mood/Energy/Anxiety) can be disabled but not deleted; custom categories (up to [Category.MAX_CUSTOM])
 * can be added and deleted. Only enabled categories are offered on the capture screen.
 */
class CategoryRepository(private val dao: CategoryDao) {

    enum class AddResult { ADDED, DUPLICATE, INVALID, MAX_REACHED }

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

    /** Adds a custom category. Rejects blank names and names containing ',' (the CSV column separator). */
    suspend fun add(name: String): AddResult {
        val trimmed = name.trim()
        if (trimmed.isEmpty() || trimmed.contains(',')) return AddResult.INVALID
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
