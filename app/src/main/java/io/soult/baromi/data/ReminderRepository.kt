package io.soult.baromi.data

/**
 * Reads/writes the configured reminder times, enforcing the 1..8-per-day bounds.
 * Seeding is explicit (not a side effect of reading) so callers control when defaults land.
 */
class ReminderRepository(private val dao: ReminderTimeDao) {

    enum class AddResult { ADDED, DUPLICATE, MAX_REACHED }

    suspend fun seedDefaultsIfEmpty() {
        if (dao.count() == 0) {
            ReminderTime.DEFAULTS.forEach { dao.insert(it) }
        }
    }

    suspend fun all(): List<ReminderTime> = dao.getAll()

    /** Adds a time, distinguishing a full schedule from a duplicate time (insert returns -1). */
    suspend fun add(hour: Int, minute: Int): AddResult {
        if (dao.count() >= ReminderTime.MAX_PER_DAY) return AddResult.MAX_REACHED
        return if (dao.insert(ReminderTime(hour = hour, minute = minute)) >= 0) {
            AddResult.ADDED
        } else {
            AddResult.DUPLICATE
        }
    }

    /** Removes a time unless it would drop below [ReminderTime.MIN_PER_DAY]. Returns true if removed. */
    suspend fun remove(time: ReminderTime): Boolean {
        if (dao.count() <= ReminderTime.MIN_PER_DAY) return false
        dao.delete(time)
        return true
    }
}
