package io.soult.baromi.export

import io.soult.baromi.data.Checkin
import io.soult.baromi.data.CheckinWithTags
import io.soult.baromi.data.Tag
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Pure CSV tests — expected strings derived independently from the inputs. */
class CsvExporterTest {

    private fun row(
        mood: Int = 3,
        energy: Int = 3,
        anxiety: Int = 3,
        comment: String? = null,
        promptTime: Long? = null,
        answeredTime: Long = 100L,
        tags: List<String> = emptyList(),
    ) = CheckinWithTags(
        checkin = Checkin(
            promptTime = promptTime,
            answeredTime = answeredTime,
            mood = mood,
            energy = energy,
            anxiety = anxiety,
            comment = comment,
        ),
        tags = tags.mapIndexed { i, label -> Tag(id = i + 1L, label = label) },
    )

    private fun lines(csv: String) = csv.trimEnd('\n').split("\n")

    @Test
    fun empty_export_is_legend_then_header() {
        val out = lines(CsvExporter.toCsv(emptyList()))
        assertEquals(2, out.size)
        assertTrue(out[0].contains("higher is better"))
        assertTrue(out[0].contains("anxiety very anxious..very calm"))
        assertEquals("prompt_time,answered_time,mood,energy,anxiety,comment,tags", out[1])
    }

    @Test
    fun row_renders_all_fields() {
        val out = lines(
            CsvExporter.toCsv(
                listOf(row(mood = 4, energy = 2, anxiety = 5, comment = "hi", promptTime = 1000L, answeredTime = 2000L, tags = listOf("work", "social"))),
            ),
        )
        assertEquals("1000,2000,4,2,5,hi,work;social", out[2])
    }

    @Test
    fun null_prompt_time_and_comment_render_empty() {
        val out = lines(CsvExporter.toCsv(listOf(row(promptTime = null, comment = null, answeredTime = 2000L))))
        assertEquals(",2000,3,3,3,,", out[2])
    }

    @Test
    fun comment_with_comma_is_quoted() {
        val csv = CsvExporter.toCsv(listOf(row(comment = "a,b", answeredTime = 1L)))
        assertTrue(csv.contains("\"a,b\""))
    }

    @Test
    fun comment_with_quotes_doubles_them() {
        val csv = CsvExporter.toCsv(listOf(row(comment = "he said \"hi\"", answeredTime = 1L)))
        assertTrue(csv.contains("\"he said \"\"hi\"\"\""))
    }

    @Test
    fun comment_with_newline_is_quoted() {
        val csv = CsvExporter.toCsv(listOf(row(comment = "line1\nline2", answeredTime = 1L)))
        assertTrue(csv.contains("\"line1\nline2\""))
    }
}
