package io.soult.baromi.stats

import io.soult.baromi.data.CheckinWithDetails
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/**
 * Pure, Android-free aggregation of check-in history for the calendar and chart views. Everything
 * here is deterministic given its inputs (the "now" is passed in as [today]), so it is unit-tested
 * without Robolectric. Categories are dynamic, so callers pass whichever [categoryId] to summarise.
 */
object HistoryStats {

    /** One day's aggregate for a category: the mean 1..5 value and how many check-ins fed it. */
    data class DayStat(val date: LocalDate, val average: Double, val count: Int)

    /** A tag and how many check-ins carry it within a window. */
    data class TagCount(val label: String, val count: Int)

    /** Chart/calendar time window. [days] null means "all history". */
    enum class Window(val days: Int?) {
        WEEK(7),
        MONTH(30),
        ALL(null),
    }

    private fun dateOf(epochMs: Long, zone: ZoneId): LocalDate =
        Instant.ofEpochMilli(epochMs).atZone(zone).toLocalDate()

    /** Inclusive first day of [window] ending at [today]; null for [Window.ALL]. */
    private fun windowStart(window: Window, today: LocalDate): LocalDate? =
        window.days?.let { today.minusDays((it - 1).toLong()) }

    private fun inWindow(date: LocalDate, start: LocalDate?, today: LocalDate): Boolean =
        date <= today && (start == null || date >= start)

    /**
     * Per-day mean value for [categoryId] over [window] ending at [today], oldest day first. Only
     * days with at least one value for the category appear (gaps stay gaps, so a bar chart never
     * interpolates over missing days). Days after [today] are ignored. This is [dayAveragesByDate]
     * narrowed to the window and ordered — the two share one aggregation so they can't drift.
     */
    fun dailyAverages(
        checkins: List<CheckinWithDetails>,
        categoryId: Long,
        zone: ZoneId,
        window: Window,
        today: LocalDate,
    ): List<DayStat> {
        val start = windowStart(window, today)
        return dayAveragesByDate(checkins, categoryId, zone)
            .filterKeys { inWindow(it, start, today) }
            .toSortedMap()
            .values
            .toList()
    }

    /**
     * Per-day mean value for [categoryId] across all history, keyed by date. The calendar slices
     * this by whichever month is on screen, so it computes the whole map once per category.
     */
    fun dayAveragesByDate(
        checkins: List<CheckinWithDetails>,
        categoryId: Long,
        zone: ZoneId,
    ): Map<LocalDate, DayStat> {
        val byDay = HashMap<LocalDate, MutableList<Int>>()
        for (row in checkins) {
            val value = row.values.firstOrNull { it.categoryId == categoryId } ?: continue
            val date = dateOf(row.checkin.answeredTime, zone)
            byDay.getOrPut(date) { mutableListOf() }.add(value.value)
        }
        return byDay.mapValues { (date, values) -> DayStat(date, values.average(), values.size) }
    }

    /**
     * Tag usage counts over [window] ending at [today], most-used first then alphabetical. A tag is
     * counted once per check-in that carries it.
     */
    fun tagCounts(
        checkins: List<CheckinWithDetails>,
        zone: ZoneId,
        window: Window,
        today: LocalDate,
    ): List<TagCount> {
        val start = windowStart(window, today)
        val counts = LinkedHashMap<String, Int>()
        for (row in checkins) {
            val date = dateOf(row.checkin.answeredTime, zone)
            if (!inWindow(date, start, today)) continue
            for (tag in row.tags) counts[tag.label] = (counts[tag.label] ?: 0) + 1
        }
        return counts.entries
            .map { TagCount(it.key, it.value) }
            .sortedWith(compareByDescending<TagCount> { it.count }.thenBy { it.label })
    }
}
