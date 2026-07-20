package io.soult.baromi.export

import io.soult.baromi.data.BaromiDatabase

/** Reads the whole record and renders it as CSV. */
class CheckinExporter(private val db: BaromiDatabase) {
    suspend fun exportCsv(): String = CsvExporter.toCsv(db.checkinDao().getAllWithTags())
}
