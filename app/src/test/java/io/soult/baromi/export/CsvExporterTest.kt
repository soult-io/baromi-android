package io.soult.baromi.export

import io.soult.baromi.data.Category
import io.soult.baromi.data.Checkin
import io.soult.baromi.data.CheckinValue
import io.soult.baromi.data.CheckinWithDetails
import io.soult.baromi.data.Tag
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Pure CSV tests — expected strings derived independently from the inputs. */
class CsvExporterTest {

    private val mood = Category(id = 1, key = "mood", name = "Mood", builtin = true, position = 0)
    private val energy = Category(id = 2, key = "energy", name = "Energy", builtin = true, position = 1)
    private val anxiety = Category(id = 3, key = "anxiety", name = "Anxiety", builtin = true, position = 2)
    private val threeBuiltins = listOf(mood, energy, anxiety)

    private fun row(
        values: Map<Long, Int> = emptyMap(),
        comment: String? = null,
        promptTime: Long? = null,
        answeredTime: Long = 100L,
        tags: List<String> = emptyList(),
    ) = CheckinWithDetails(
        checkin = Checkin(promptTime = promptTime, answeredTime = answeredTime, comment = comment),
        values = values.map { (catId, v) -> CheckinValue(checkinId = 1L, categoryId = catId, value = v) },
        tags = tags.mapIndexed { i, label -> Tag(id = i + 1L, label = label) },
    )

    private fun lines(csv: String) = csv.trimEnd('\n').split("\n")

    @Test
    fun empty_export_is_header_only() {
        val out = lines(CsvExporter.toCsv(threeBuiltins, emptyList()))
        assertEquals(1, out.size)
        assertTrue(out[0].startsWith("prompt_time,answered_time,"))
        assertTrue(out[0].endsWith(",comment,tags"))
        assertTrue(out[0].contains("Mood (1=very low..5=very good; higher is better)"))
        assertTrue(out[0].contains("Anxiety (1=very anxious..5=very calm; higher is better)"))
    }

    @Test
    fun custom_category_header_uses_least_to_most() {
        val focus = Category(id = 9, key = null, name = "Focus", builtin = false, position = 3)
        val out = lines(CsvExporter.toCsv(listOf(focus), emptyList()))
        assertTrue(out[0].contains("Focus (1=least..5=most; higher is better)"))
    }

    @Test
    fun row_renders_all_categories_with_sorted_tags() {
        val out = lines(
            CsvExporter.toCsv(
                threeBuiltins,
                listOf(
                    row(
                        values = mapOf(1L to 4, 2L to 2, 3L to 5),
                        comment = "hi", promptTime = 1000L, answeredTime = 2000L,
                        tags = listOf("work", "social"),
                    ),
                ),
            ),
        )
        assertEquals("1000,2000,4,2,5,hi,social;work", out[1])
    }

    @Test
    fun missing_category_value_renders_empty_cell() {
        // Only mood recorded; energy and anxiety columns are empty.
        val out = lines(CsvExporter.toCsv(threeBuiltins, listOf(row(values = mapOf(1L to 3), answeredTime = 2000L))))
        assertEquals(",2000,3,,,,", out[1])
    }

    @Test
    fun comment_with_comma_is_quoted() {
        assertTrue(CsvExporter.toCsv(listOf(mood), listOf(row(comment = "a,b", answeredTime = 1L))).contains("\"a,b\""))
    }

    @Test
    fun comment_with_quotes_doubles_them() {
        assertTrue(CsvExporter.toCsv(listOf(mood), listOf(row(comment = "he said \"hi\"", answeredTime = 1L))).contains("\"he said \"\"hi\"\"\""))
    }

    @Test
    fun comment_with_newline_is_quoted() {
        assertTrue(CsvExporter.toCsv(listOf(mood), listOf(row(comment = "line1\nline2", answeredTime = 1L))).contains("\"line1\nline2\""))
    }

    @Test
    fun comment_with_carriage_return_is_quoted() {
        assertTrue(CsvExporter.toCsv(listOf(mood), listOf(row(comment = "a\rb", answeredTime = 1L))).contains("\"a\rb\""))
    }

    @Test
    fun formula_trigger_comment_is_guarded() {
        assertTrue(CsvExporter.toCsv(listOf(mood), listOf(row(comment = "=SUM(A1)", answeredTime = 1L))).contains("'=SUM(A1)"))
    }

    @Test
    fun leading_dash_comment_is_guarded() {
        assertTrue(CsvExporter.toCsv(listOf(mood), listOf(row(comment = "-low day", answeredTime = 1L))).contains("'-low day"))
    }
}
