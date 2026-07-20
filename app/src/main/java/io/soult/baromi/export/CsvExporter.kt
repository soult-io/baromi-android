package io.soult.baromi.export

import io.soult.baromi.data.CheckinWithTags
import io.soult.baromi.data.ScaleSchema

/**
 * Pure CSV serialization of the record — no Android types, so it is exhaustively testable.
 *
 * Rectangular output (header row + one row per check-in, all with the same column count) so it
 * loads cleanly in spreadsheets *and* strict parsers like pandas. The scale is documented in the
 * axis column headers themselves. Times are epoch milliseconds; tags are sorted and joined with
 * ';'. RFC-4180 escaping (comma/quote/CR/LF), plus a leading-character guard so a comment/tag
 * can't act as a spreadsheet formula (CSV injection).
 */
object CsvExporter {

    fun header(): List<String> = listOf(
        "prompt_time",
        "answered_time",
        axisHeader("mood"),
        axisHeader("energy"),
        axisHeader("anxiety"),
        "comment",
        "tags",
    )

    fun toCsv(rows: List<CheckinWithTags>): String {
        val out = StringBuilder()
        out.append(header().joinToString(",") { escape(it) }).append('\n')
        for (row in rows) {
            val c = row.checkin
            val fields = listOf(
                c.promptTime?.toString().orEmpty(),
                c.answeredTime.toString(),
                c.mood.toString(),
                c.energy.toString(),
                c.anxiety.toString(),
                c.comment.orEmpty(),
                row.tags.map { it.label }.sorted().joinToString(";"),
            )
            out.append(fields.joinToString(",") { sanitize(it) }).append('\n')
        }
        return out.toString()
    }

    private fun axisHeader(axis: String): String {
        val (low, high) = ScaleSchema.axes.getValue(axis)
        return "$axis (${ScaleSchema.MIN}=$low..${ScaleSchema.MAX}=$high; higher is better)"
    }

    /** RFC-4180 escaping after neutralizing a leading formula trigger. */
    private fun sanitize(field: String): String = escape(guardFormula(field))

    private fun guardFormula(field: String): String =
        if (field.isNotEmpty() && field[0] in FORMULA_TRIGGERS) "'$field" else field

    private fun escape(field: String): String =
        if (field.any { it == ',' || it == '"' || it == '\n' || it == '\r' }) {
            "\"" + field.replace("\"", "\"\"") + "\""
        } else {
            field
        }

    private val FORMULA_TRIGGERS = setOf('=', '+', '-', '@', '\t', '\r')
}
