package io.soult.baromi.export

import io.soult.baromi.data.CheckinWithTags
import io.soult.baromi.data.ScaleSchema

/**
 * Pure CSV serialization of the record — no Android types, so it is exhaustively testable.
 *
 * Layout: a single quoted legend line documenting the (frozen) scale, then a header row, then
 * one row per check-in. Times are epoch milliseconds; tags are joined with ';'. Fields that
 * contain a comma, quote, or newline are quoted with embedded quotes doubled (RFC 4180).
 */
object CsvExporter {

    val HEADER = listOf("prompt_time", "answered_time", "mood", "energy", "anxiety", "comment", "tags")

    fun toCsv(rows: List<CheckinWithTags>): String {
        val out = StringBuilder()
        out.append(escape(legend())).append('\n')
        out.append(HEADER.joinToString(",")).append('\n')
        for (row in rows) {
            val c = row.checkin
            val fields = listOf(
                c.promptTime?.toString().orEmpty(),
                c.answeredTime.toString(),
                c.mood.toString(),
                c.energy.toString(),
                c.anxiety.toString(),
                c.comment.orEmpty(),
                row.tags.joinToString(";") { it.label },
            )
            out.append(fields.joinToString(",") { escape(it) }).append('\n')
        }
        return out.toString()
    }

    private fun legend(): String {
        val axes = ScaleSchema.axes.entries.joinToString("; ") { (axis, anchors) ->
            "$axis ${anchors.first}..${anchors.second}"
        }
        return "Baromi export (scale v${ScaleSchema.VERSION}): all axes ${ScaleSchema.MIN}.." +
            "${ScaleSchema.MAX}, higher is better ($axes). Times are epoch milliseconds."
    }

    private fun escape(field: String): String =
        if (field.any { it == ',' || it == '"' || it == '\n' || it == '\r' }) {
            "\"" + field.replace("\"", "\"\"") + "\""
        } else {
            field
        }
}
