package io.soult.baromi.ui

import android.os.Looper
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
import kotlinx.coroutines.CompletableDeferred
import org.junit.Assert.assertEquals
import java.time.LocalDate
import java.time.ZoneId
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf

/**
 * Drives the History views over fixed fixtures (no DB: [HistoryFragment.loader] is swapped for
 * fixtures, and reloads use [HistoryFragment.renderForTest] or a real stop/start) and
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

    private fun host(
        data: List<CheckinWithDetails> = items,
        cats: List<Category> = categories,
    ): Pair<View, HistoryFragment> {
        val activity = Robolectric.buildActivity(MainActivity::class.java).setup().get()
        val fragment = HistoryFragment().apply { loader = { data to cats } }
        activity.supportFragmentManager.beginTransaction()
            .replace(R.id.navHostContainer, fragment)
            .commitNow()
        shadowOf(Looper.getMainLooper()).idle()
        return fragment.requireView() to fragment
    }

    private fun selectCalendarCategory(view: View, index: Int) {
        val group = view.findViewById<ChipGroup>(R.id.calendarCategoryChips)
        group.check((group.getChildAt(index) as Chip).id)
    }

    private fun checkedCalendarCategory(view: View): String {
        val group = view.findViewById<ChipGroup>(R.id.calendarCategoryChips)
        return group.findViewById<Chip>(group.checkedChipId).text.toString()
    }

    @Test
    fun defaults_to_list_with_entries() {
        val (view, _) = host()
        assertEquals(View.VISIBLE, view.findViewById<View>(R.id.listContainer).visibility)
        assertEquals(View.GONE, view.findViewById<View>(R.id.emptyState).visibility)
        assertEquals(3, view.findViewById<RecyclerView>(R.id.historyList).adapter?.itemCount)
    }

    @Test
    fun segmented_control_swaps_containers() {
        val (view, _) = host()
        view.findViewById<MaterialButtonToggleGroup>(R.id.modeToggle).check(R.id.btnCalendar)
        assertEquals(View.VISIBLE, view.findViewById<View>(R.id.calendarContainer).visibility)
        assertEquals(View.GONE, view.findViewById<View>(R.id.listContainer).visibility)

        view.findViewById<MaterialButtonToggleGroup>(R.id.modeToggle).check(R.id.btnCharts)
        assertEquals(View.VISIBLE, view.findViewById<View>(R.id.chartsContainer).visibility)
        assertEquals(View.GONE, view.findViewById<View>(R.id.calendarContainer).visibility)
    }

    @Test
    fun calendar_builds_a_chip_per_category() {
        val (view, _) = host()
        assertEquals(3, view.findViewById<ChipGroup>(R.id.calendarCategoryChips).childCount)
    }

    @Test
    fun tapping_a_day_shows_its_entries_then_a_category_switch_clears_them() {
        val (view, _) = host()
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
        // Three distinct days clears the low-data threshold so the trend charts render.
        val data = listOf(
            checkin(today, 4, 3, 4, listOf("exercise")),
            checkin(yesterday, 5, 5, 5, listOf("social")),
            checkin(today.minusDays(2), 2, 2, 3, listOf("exercise")),
        )
        val (view, _) = host(data)
        // Per category: a title TextView + a BarChartView; plus a tag title + tag chart = 3*2 + 2.
        assertEquals(8, view.findViewById<LinearLayout>(R.id.chartsList).childCount)
        assertEquals(View.GONE, view.findViewById<View>(R.id.chartsEmpty).visibility)
    }

    @Test
    fun charts_show_guidance_when_too_few_days() {
        // host() data spans only two distinct days (< MIN_CHART_DAYS), so charts are gated.
        val (view, _) = host()
        assertEquals(0, view.findViewById<LinearLayout>(R.id.chartsList).childCount)
        assertEquals(View.VISIBLE, view.findViewById<View>(R.id.chartsEmpty).visibility)
    }

    @Test
    fun empty_record_shows_empty_state_and_no_charts() {
        val (view, _) = host(emptyList())

        assertEquals(View.VISIBLE, view.findViewById<View>(R.id.emptyState).visibility)
        assertEquals(0, view.findViewById<LinearLayout>(R.id.chartsList).childCount)
        assertEquals(View.VISIBLE, view.findViewById<View>(R.id.chartsEmpty).visibility)
    }

    @Test
    fun calendar_chips_cap_their_width_and_ellipsize() {
        val (view, _) = host()
        val chip = view.findViewById<ChipGroup>(R.id.calendarCategoryChips).getChildAt(0) as Chip
        val density = view.resources.displayMetrics.density
        assertEquals(TextUtils.TruncateAt.END, chip.ellipsize)
        assertEquals((HistoryFragment.CHIP_MAX_WIDTH_DP * density).toInt(), chip.maxWidth)
    }

    @Test
    fun returning_to_history_reloads_from_the_data_source() {
        val controller = Robolectric.buildActivity(MainActivity::class.java).setup()
        var loads = 0
        var data = items
        val fragment = HistoryFragment().apply { loader = { loads++; data to categories } }
        controller.get().supportFragmentManager.beginTransaction()
            .replace(R.id.navHostContainer, fragment)
            .commitNow()
        shadowOf(Looper.getMainLooper()).idle()
        val list = fragment.requireView().findViewById<RecyclerView>(R.id.historyList)
        assertEquals(3, list.adapter!!.itemCount)

        // Something changed elsewhere (e.g. a category deleted in Settings), then History is shown again.
        data = items.take(1)
        controller.pause().stop()
        controller.restart().start().resume()
        shadowOf(Looper.getMainLooper()).idle()

        assertEquals(2, loads)
        assertEquals(1, list.adapter!!.itemCount)
    }

    @Test
    fun reload_keeps_the_selected_calendar_category() {
        val (view, fragment) = host()
        selectCalendarCategory(view, 1) // Energy

        fragment.renderForTest(items, categories) // a reload with the same categories

        assertEquals("Energy", checkedCalendarCategory(view))
    }

    @Test
    fun reload_refreshes_an_open_day_panel() {
        val (view, fragment) = host()
        view.findViewById<CalendarHeatmapView>(R.id.calendarHeatmap).onDayClick!!.invoke(today)
        val panel = view.findViewById<RecyclerView>(R.id.dayDetailList)
        assertEquals(2, panel.adapter!!.itemCount)

        // Today's second check-in went away (e.g. replaced by a backup import), then History reloads.
        fragment.renderForTest(items.drop(1), categories)

        assertEquals(View.VISIBLE, view.findViewById<View>(R.id.dayDetailLabel).visibility)
        assertEquals(1, panel.adapter!!.itemCount)
    }

    @Test
    fun reload_falls_back_and_closes_the_day_panel_when_the_selected_category_is_disabled() {
        val (view, fragment) = host()
        selectCalendarCategory(view, 1) // Energy
        view.findViewById<CalendarHeatmapView>(R.id.calendarHeatmap).onDayClick!!.invoke(today)

        fragment.renderForTest(items, categories.map { if (it.id == energyId) it.copy(enabled = false) else it })

        assertEquals("Mood", checkedCalendarCategory(view))
        assertEquals(View.GONE, view.findViewById<View>(R.id.dayDetailLabel).visibility)
        assertEquals(0, view.findViewById<RecyclerView>(R.id.dayDetailList).adapter!!.itemCount)
    }

    @Test
    fun an_older_load_finishing_late_does_not_overwrite_a_newer_one() {
        val controller = Robolectric.buildActivity(MainActivity::class.java).setup()
        val pending = mutableListOf<CompletableDeferred<Pair<List<CheckinWithDetails>, List<Category>>>>()
        val fragment = HistoryFragment().apply { loader = { CompletableDeferred<Pair<List<CheckinWithDetails>, List<Category>>>().also { pending += it }.await() } }
        controller.get().supportFragmentManager.beginTransaction()
            .replace(R.id.navHostContainer, fragment)
            .commitNow()
        controller.pause().stop()
        controller.restart().start().resume() // second load starts while the first is still pending
        shadowOf(Looper.getMainLooper()).idle()
        assertEquals(2, pending.size)

        pending[1].complete(items.take(1) to categories) // newest finishes first
        shadowOf(Looper.getMainLooper()).idle()
        pending[0].complete(items to categories) // the older load completes late
        shadowOf(Looper.getMainLooper()).idle()

        val list = fragment.requireView().findViewById<RecyclerView>(R.id.historyList)
        assertEquals(1, list.adapter!!.itemCount)
    }
}
