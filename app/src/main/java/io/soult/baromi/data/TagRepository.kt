package io.soult.baromi.data

/** Reads the tag list and seeds the defaults on first run. Full management lands in Module E. */
class TagRepository(private val dao: TagDao) {

    suspend fun seedDefaultsIfEmpty() {
        if (dao.getAll().isEmpty()) {
            Tag.DEFAULTS.forEach { dao.insert(it) }
        }
    }

    suspend fun all(): List<Tag> = dao.getAll()
}
