package io.soult.baromi.export

import io.soult.baromi.data.Category
import io.soult.baromi.data.CategoryDefs
import io.soult.baromi.data.CheckinWithDetails
import io.soult.baromi.data.ScaleSchema

/**
 * Pure CSV serialization of the record — no Android types, so it is exhaustively testable.
 *
 * Wide, rectangular output: a fixed prompt_time/answered_time prefix, then one column per category
 * (in the order given), then comment and tags. Every row has the same column count so it loads
 * cleanly in spreadsheets *and* strict parsers like pandas. A category with no value on a given
 * check-in (e.g. added later, or disabled at the time) renders as an empty cell. The scale is
 * documented in each category column header. Times are epoch milliseconds; tags are sorted and
 * joined with ';'. RFC-4180 escaping (comma/quote/CR/LF), plus a leading-character guard so a
 * comment/tag can't act as a spreadsheet formula (CSV injection).
 */
object CsvExporter {

    fun header(categories: List<Category>): List<String> =
        listOf("prompt_time", "answered_time") +
            categories.map { categoryHeader(it) } +
            listOf("comment", "tags")

    fun toCsv(categories: List<Category>, rows: List<CheckinWithDetails>): String {
        val out = StringBuilder()
        out.append(header(categories).joinToString(",") { sanitize(it) }).append('\n')
        for (row in rows) {
            val c = row.checkin
            val byCategory = row.values.associate { it.categoryId to it.value }
            val fields = buildList {
                add(c.promptTime?.toString().orEmpty())
                add(c.answeredTime.toString())
                categories.forEach { add(byCategory[it.id]?.toString().orEmpty()) }
                add(c.comment.orEmpty())
                add(row.tags.map { it.label }.sorted().joinToString(";"))
            }
            out.append(fields.joinToString(",") { sanitize(it) }).append('\n')
        }
        return out.toString()
    }

    private fun categoryHeader(category: Category): String {
        val labels = CategoryDefs.labelsFor(category)
        // Built-ins document their worded anchors; custom categories are a plain least..most scale.
        val (low, high) = if (category.key != null) labels.first() to labels.last() else "least" to "most"
        return "${category.name} (${ScaleSchema.MIN}=$low..${ScaleSchema.MAX}=$high; higher is better)"
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
