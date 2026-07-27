package io.soult.baromi.stats

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.YearMonth
import org.junit.Test

/** Pure grid geometry — plain JUnit, expected cells hand-derived (July 1 2026 is a Wednesday). */
class CalendarGridTest {

    private val july = YearMonth.of(2026, 7)

    @Test
    fun sunday_first_leading_blanks_rows_and_cells() {
        val g = CalendarGrid(july, DayOfWeek.SUNDAY)
        assertEquals(3, g.leadingBlanks) // Sun, Mon, Tue before Wed 1
        assertEquals(5, g.rowCount) // (3 + 31 + 6) / 7
        assertEquals(0 to 3, g.cellOf(1))
        assertEquals(1 to 0, g.cellOf(5))
        assertEquals(4 to 5, g.cellOf(31))
    }

    @Test
    fun monday_first_shifts_the_first_column() {
        val g = CalendarGrid(july, DayOfWeek.MONDAY)
        assertEquals(2, g.leadingBlanks) // Mon, Tue before Wed 1
        assertEquals(0 to 2, g.cellOf(1))
    }

    @Test
    fun dayAt_maps_cells_and_rejects_blanks() {
        val g = CalendarGrid(july, DayOfWeek.SUNDAY)
        assertEquals(1, g.dayAt(0, 3))
        assertEquals(27, g.dayAt(4, 1))
        assertEquals(31, g.dayAt(4, 5))
        assertNull(g.dayAt(0, 2)) // leading blank
        assertNull(g.dayAt(4, 6)) // trailing blank (would be day 32)
        assertNull(g.dayAt(-1, 0))
        assertNull(g.dayAt(0, 7))
    }

    @Test
    fun dateAt_hit_tests_pixels() {
        val g = CalendarGrid(july, DayOfWeek.SUNDAY)
        val cell = 100f
        val header = 50f
        assertEquals(LocalDate.of(2026, 7, 27), g.dateAt(150f, 500f, cell, header)) // col 1, row 4
        assertEquals(LocalDate.of(2026, 7, 1), g.dateAt(350f, 80f, cell, header)) // col 3, row 0
        assertNull(g.dateAt(150f, 30f, cell, header)) // y inside the header strip
        assertNull(g.dateAt(250f, 80f, cell, header)) // col 2, row 0 → leading blank
        assertNull(g.dateAt(150f, 500f, 0f, header)) // not measured yet (cellSize 0)
    }

    @Test
    fun leap_february_row_count() {
        // Feb 1 2024 is a Thursday; leap Feb has 29 days.
        val g = CalendarGrid(YearMonth.of(2024, 2), DayOfWeek.SUNDAY)
        assertEquals(4, g.leadingBlanks) // Sun..Wed before Thu
        assertEquals(5, g.rowCount) // (4 + 29 + 6) / 7
    }
}
