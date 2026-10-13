package io.soult.baromi.ui

import android.text.TextUtils
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.button.MaterialButtonToggleGroup
import com.google.android.material.chip.Chip
import com.google.android.material.chip.ChipGroup
import io.soult.baromi.R
import io.soult.baromi.data.Category
import io.soult.baromi.data.Checkin
import io.soult.baromi.data.CheckinValue
import io.soult.baromi.data.CheckinWithDetails
import io.soult.baromi.data.Tag
import org.junit.Assert.assertEquals
import java.time.LocalDate
import java.time.ZoneId
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner

/**
 * Drives the History views over fixed fixtures (no DB, via [HistoryFragment.renderForTest]) and
 * asserts the wiring: the segmented control swaps containers, the calendar builds a chip per
 * category and shows a tapped day's entries (clearing them on a category switch), and Charts builds
 * one titled chart per category plus the tag chart.
 */
@RunWith(RobolectricTestRunner::class)
class HistoryFragmentTest {

    private val zone: ZoneId = ZoneId.systemDefault()
    private val today: LocalDate = LocalDate.now(zone)
    private val yesterday: LocalDate = today.minusDays(1)

    private val moodId = 1L
    private val energyId = 2L
    private val anxietyId = 3L

    private val categories = listOf(
        Category(id = moodId, key = "mood", name = "Mood", builtin = true, position = 0),
        Category(id = energyId, key = "energy", name = "Energy", builtin = true, position = 1),
        Category(id = anxietyId, key = "anxiety", name = "Anxiety", builtin = true, position = 2),
    )

    private fun ms(date: LocalDate): Long =
        date.atTime(12, 0).atZone(zone).toInstant().toEpochMilli()

    private fun checkin(date: LocalDate, mood: Int, energy: Int, anxiety: Int, tags: List<String>) =
        CheckinWithDetails(
            checkin = Checkin(promptTime = null, answeredTime = ms(date), comment = null),
            values = listOf(
                CheckinValue(0L, moodId, mood),
                CheckinValue(0L, energyId, energy),
                CheckinValue(0L, anxietyId, anxiety),
            ),
            tags = tags.mapIndexed { i, label -> Tag(id = i.toLong() + 1, label = label) },
        )

    private val items = listOf(
        checkin(today, 4, 3, 4, listOf("exercise")),
        checkin(today, 2, 2, 3, listOf("exercise", "social")),
        checkin(yesterday, 5, 5, 5, listOf("social")),
    )

    private fun hostWithData(): Pair<View, HistoryFragment> {
        val activity = Robolectric.buildActivity(MainActivity::class.java).setup().get()
        val fragment = HistoryFragment().apply { autoLoad = false }
        activity.supportFragmentManager.beginTransaction()
            .replace(R.id.navHostContainer, fragment)
            .commitNow()
        fragment.renderForTest(items, categories)
        return fragment.requireView() to fragment
    }

    @Test
    fun defaults_to_list_with_entries() {
        val (view, _) = hostWithData()
        assertEquals(View.VISIBLE, view.findViewById<View>(R.id.listContainer).visibility)
        assertEquals(View.GONE, view.findViewById<View>(R.id.emptyState).visibility)
        assertEquals(3, view.findViewById<RecyclerView>(R.id.historyList).adapter?.itemCount)
    }

    @Test
    fun segmented_control_swaps_containers() {
        val (view, _) = hostWithData()
        view.findViewById<MaterialButtonToggleGroup>(R.id.modeToggle).check(R.id.btnCalendar)
        assertEquals(View.VISIBLE, view.findViewById<View>(R.id.calendarContainer).visibility)
        assertEquals(View.GONE, view.findViewById<View>(R.id.listContainer).visibility)

        view.findViewById<MaterialButtonToggleGroup>(R.id.modeToggle).check(R.id.btnCharts)
        assertEquals(View.VISIBLE, view.findViewById<View>(R.id.chartsContainer).visibility)
        assertEquals(View.GONE, view.findViewById<View>(R.id.calendarContainer).visibility)
    }

    @Test
    fun calendar_builds_a_chip_per_category() {
        val (view, _) = hostWithData()
        assertEquals(3, view.findViewById<ChipGroup>(R.id.calendarCategoryChips).childCount)
    }

    @Test
    fun tapping_a_day_shows_its_entries_then_a_category_switch_clears_them() {
        val (view, _) = hostWithData()
        val heatmap = view.findViewById<CalendarHeatmapView>(R.id.calendarHeatmap)
        heatmap.onDayClick?.invoke(today)

        val detailLabel = view.findViewById<TextView>(R.id.dayDetailLabel)
        val detailList = view.findViewById<RecyclerView>(R.id.dayDetailList)
        assertEquals(View.VISIBLE, detailLabel.visibility)
        assertEquals(2, detailList.adapter?.itemCount) // two check-ins today

        val chips = view.findViewById<ChipGroup>(R.id.calendarCategoryChips)
        chips.check((chips.getChildAt(1) as Chip).id) // switch to Energy
        assertEquals(View.GONE, detailLabel.visibility)
        assertEquals(0, detailList.adapter?.itemCount)
    }

    @Test
    fun charts_render_one_per_category_plus_tag_chart() {
        val activity = Robolectric.buildActivity(MainActivity::class.java).setup().get()
        val fragment = HistoryFragment().apply { autoLoad = false }
        activity.supportFragmentManager.beginTransaction()
            .replace(R.id.navHostContainer, fragment)
            .commitNow()
        // Three distinct days clears the low-data threshold so the trend charts render.
        val data = listOf(
            checkin(today, 4, 3, 4, listOf("exercise")),
            checkin(yesterday, 5, 5, 5, listOf("social")),
            checkin(today.minusDays(2), 2, 2, 3, listOf("exercise")),
        )
        fragment.renderForTest(data, categories)
        val view = fragment.requireView()
        // Per category: a title TextView + a BarChartView; plus a tag title + tag chart = 3*2 + 2.
        assertEquals(8, view.findViewById<LinearLayout>(R.id.chartsList).childCount)
        assertEquals(View.GONE, view.findViewById<View>(R.id.chartsEmpty).visibility)
    }

    @Test
    fun charts_show_guidance_when_too_few_days() {
        // hostWithData spans only two distinct days (< MIN_CHART_DAYS), so charts are gated.
        val (view, _) = hostWithData()
        assertEquals(0, view.findViewById<LinearLayout>(R.id.chartsList).childCount)
        assertEquals(View.VISIBLE, view.findViewById<View>(R.id.chartsEmpty).visibility)
    }

    @Test
    fun empty_record_shows_empty_state_and_no_charts() {
        val activity = Robolectric.buildActivity(MainActivity::class.java).setup().get()
        val fragment = HistoryFragment().apply { autoLoad = false }
        activity.supportFragmentManager.beginTransaction()
            .replace(R.id.navHostContainer, fragment)
            .commitNow()
        fragment.renderForTest(emptyList(), categories)
        val view = fragment.requireView()

        assertEquals(View.VISIBLE, view.findViewById<View>(R.id.emptyState).visibility)
        assertEquals(0, view.findViewById<LinearLayout>(R.id.chartsList).childCount)
        assertEquals(View.VISIBLE, view.findViewById<View>(R.id.chartsEmpty).visibility)
    }

    @Test
    fun calendar_chips_cap_their_width_and_ellipsize() {
        val (view, _) = hostWithData()
        val chip = view.findViewById<ChipGroup>(R.id.calendarCategoryChips).getChildAt(0) as Chip
        val density = view.resources.displayMetrics.density
        assertEquals(TextUtils.TruncateAt.END, chip.ellipsize)
        assertEquals((HistoryFragment.CHIP_MAX_WIDTH_DP * density).toInt(), chip.maxWidth)
    }
}
