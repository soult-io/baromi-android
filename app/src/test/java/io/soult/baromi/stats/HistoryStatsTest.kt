package io.soult.baromi.stats

import io.soult.baromi.data.Checkin
import io.soult.baromi.data.CheckinValue
import io.soult.baromi.data.CheckinWithDetails
import io.soult.baromi.data.Tag
import org.junit.Assert.assertEquals
import java.time.LocalDate
import java.time.ZoneId
import org.junit.Test

/** Pure aggregation, so plain JUnit — no Robolectric. Expected values are hand-derived below. */
class HistoryStatsTest {

    private val zone: ZoneId = ZoneId.of("UTC")
    private val today: LocalDate = LocalDate.of(2026, 7, 27)

    private val moodId = 1L
    private val energyId = 2L

    private fun ms(date: LocalDate, hour: Int = 12): Long =
        date.atTime(hour, 0).atZone(zone).toInstant().toEpochMilli()

    private fun checkin(
        date: LocalDate,
        values: Map<Long, Int>,
        tags: List<String> = emptyList(),
        hour: Int = 12,
    ): CheckinWithDetails = CheckinWithDetails(
        checkin = Checkin(promptTime = null, answeredTime = ms(date, hour), comment = null),
        values = values.map { (categoryId, v) -> CheckinValue(0L, categoryId, v) },
        tags = tags.mapIndexed { i, label -> Tag(id = i.toLong() + 1, label = label) },
    )

    @Test
    fun dailyAverages_means_same_day_and_keeps_gaps_oldest_first() {
        val d1 = LocalDate.of(2026, 7, 24)
        val d2 = LocalDate.of(2026, 7, 26)
        val data = listOf(
            checkin(d1, mapOf(moodId to 2), hour = 9),
            checkin(d1, mapOf(moodId to 4), hour = 18), // same day -> mean (2+4)/2 = 3.0
            checkin(d2, mapOf(moodId to 5)),
        )
        val out = HistoryStats.dailyAverages(data, moodId, zone, HistoryStats.Window.ALL, today)
        assertEquals(2, out.size)
        assertEquals(HistoryStats.DayStat(d1, 3.0, 2), out[0])
        assertEquals(HistoryStats.DayStat(d2, 5.0, 1), out[1])
    }

    @Test
    fun dailyAverages_window_excludes_days_before_start() {
        // WEEK(7) ending 2026-07-27 => start = 2026-07-21 (today minus 6 days), inclusive.
        val data = listOf(
            checkin(LocalDate.of(2026, 7, 20), mapOf(moodId to 1)), // out of window
            checkin(LocalDate.of(2026, 7, 21), mapOf(moodId to 2)), // first in-window day
            checkin(today, mapOf(moodId to 4)),
        )
        val out = HistoryStats.dailyAverages(data, moodId, zone, HistoryStats.Window.WEEK, today)
        assertEquals(listOf(LocalDate.of(2026, 7, 21), today), out.map { it.date })
    }

    @Test
    fun dailyAverages_ignores_future_and_other_categories() {
        val data = listOf(
            checkin(LocalDate.of(2026, 7, 28), mapOf(moodId to 5)), // after today
            checkin(today, mapOf(energyId to 3)), // different category only
            checkin(today, mapOf(moodId to 2)),
        )
        val out = HistoryStats.dailyAverages(data, moodId, zone, HistoryStats.Window.ALL, today)
        assertEquals(listOf(HistoryStats.DayStat(today, 2.0, 1)), out)
    }

    @Test
    fun dayAveragesByDate_keys_by_local_date_and_means() {
        val d = LocalDate.of(2026, 7, 10)
        val data = listOf(
            checkin(d, mapOf(moodId to 1), hour = 8),
            checkin(d, mapOf(moodId to 2), hour = 20), // mean (1+2)/2 = 1.5
        )
        val map = HistoryStats.dayAveragesByDate(data, moodId, zone)
        assertEquals(1, map.size)
        assertEquals(HistoryStats.DayStat(d, 1.5, 2), map[d])
    }

    @Test
    fun tagCounts_sorts_by_count_then_alphabetical_within_window() {
        val data = listOf(
            checkin(today, mapOf(moodId to 3), tags = listOf("exercise", "social")),
            checkin(LocalDate.of(2026, 7, 26), mapOf(moodId to 3), tags = listOf("exercise")),
            checkin(LocalDate.of(2026, 7, 26), mapOf(moodId to 3), tags = listOf("social")),
            checkin(LocalDate.of(2026, 7, 1), mapOf(moodId to 3), tags = listOf("exercise")), // outside WEEK
        )
        val out = HistoryStats.tagCounts(data, zone, HistoryStats.Window.WEEK, today)
        // In-window: exercise x2, social x2 -> tie broken alphabetically (exercise before social).
        assertEquals(
            listOf(HistoryStats.TagCount("exercise", 2), HistoryStats.TagCount("social", 2)),
            out,
        )
    }
}
