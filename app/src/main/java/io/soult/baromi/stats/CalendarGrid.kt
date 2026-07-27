package io.soult.baromi.stats

import java.time.DayOfWeek
import java.time.LocalDate
import java.time.YearMonth

/**
 * Pure month-grid geometry for the calendar heatmap: where each day sits in a 7-column grid and
 * which day a tap lands on. Android-free, so the off-by-one-prone layout math (leading blanks,
 * row count, locale week start, tap → date) is unit-tested without a device. The view keeps only
 * the drawing; all positioning comes from here.
 */
class CalendarGrid(val month: YearMonth, val firstDayOfWeek: DayOfWeek) {

    /** Column 0..6 of [date] under [firstDayOfWeek]. */
    fun columnOf(date: LocalDate): Int =
        (date.dayOfWeek.value - firstDayOfWeek.value + 7) % 7

    /** Blank cells before day 1 (the first row's leading gap). */
    val leadingBlanks: Int = columnOf(month.atDay(1))

    /** Number of week rows the month occupies. */
    val rowCount: Int = (leadingBlanks + month.lengthOfMonth() + 6) / 7

    /** (row, col) for [day] (1-based), row-major from the top-left cell. */
    fun cellOf(day: Int): Pair<Int, Int> {
        val index = leadingBlanks + day - 1
        return index / 7 to index % 7
    }

    /** Day-of-month at grid ([row], [col]), or null if that cell is a leading/trailing blank. */
    fun dayAt(row: Int, col: Int): Int? {
        if (row < 0 || col !in 0..6) return null
        val day = row * 7 + col - leadingBlanks + 1
        return if (day in 1..month.lengthOfMonth()) day else null
    }

    /**
     * The date a tap at pixel ([x], [y]) lands on, or null if it is outside the day cells. [cellSize]
     * is the square cell edge; [headerHeight] is the weekday-label strip drawn above the grid.
     */
    fun dateAt(x: Float, y: Float, cellSize: Float, headerHeight: Float): LocalDate? {
        if (cellSize <= 0f || x < 0f || y < headerHeight) return null
        val col = (x / cellSize).toInt()
        val row = ((y - headerHeight) / cellSize).toInt()
        val day = dayAt(row, col) ?: return null
        return month.atDay(day)
    }
}
