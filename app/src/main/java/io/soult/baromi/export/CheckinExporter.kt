package io.soult.baromi.export

import io.soult.baromi.data.BaromiDatabase

/** Reads the whole record and renders it as a wide CSV — one column per category. */
class CheckinExporter(private val db: BaromiDatabase) {
    suspend fun exportCsv(): String {
        // All categories (including disabled ones that still hold historical values), in display order.
        val categories = db.categoryDao().getAll()
        return CsvExporter.toCsv(categories, db.checkinDao().getAllWithDetails())
    }
}
