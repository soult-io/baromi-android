package io.soult.baromi.ui

import android.os.Bundle
import android.util.TypedValue
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.android.material.chip.Chip
import io.soult.baromi.R
import io.soult.baromi.data.BaromiDatabase
import io.soult.baromi.data.Category
import io.soult.baromi.data.CheckinWithDetails
import io.soult.baromi.databinding.FragmentHistoryBinding
import io.soult.baromi.stats.HistoryStats
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * The History tab. A segmented control switches between three views over the same loaded data:
 * the chronological List (unchanged), a Calendar heatmap (a month tinted by a chosen category's
 * daily average, tap a day for its entries), and Charts (per-category daily-average bars plus tag
 * frequency, over a selectable window). All aggregation lives in [HistoryStats]; this fragment only
 * loads once and renders.
 */
class HistoryFragment : Fragment() {

    private var binding: FragmentHistoryBinding? = null

    private val listAdapter = CheckinAdapter()
    private val dayDetailAdapter = CheckinAdapter()

    private val zone: ZoneId = ZoneId.systemDefault()
    private val today: LocalDate = LocalDate.now(zone)

    private var items: List<CheckinWithDetails> = emptyList()
    private var categories: List<Category> = emptyList()
    private var enabledCategories: List<Category> = emptyList()
    private var loaded = false

    private var currentMonth: YearMonth = YearMonth.from(today)
    private var calendarCategoryId: Long = -1L
    private var window: HistoryStats.Window = HistoryStats.Window.MONTH

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        val b = FragmentHistoryBinding.inflate(inflater, container, false)
        binding = b
        return b.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        val b = binding ?: return
        b.historyList.layoutManager = LinearLayoutManager(requireContext())
        b.historyList.adapter = listAdapter
        b.dayDetailList.layoutManager = LinearLayoutManager(requireContext())
        b.dayDetailList.adapter = dayDetailAdapter

        b.modeToggle.addOnButtonCheckedListener { _, checkedId, isChecked ->
            if (isChecked) showMode(checkedId)
        }
        b.btnPrevMonth.setOnClickListener { changeMonth(-1) }
        b.btnNextMonth.setOnClickListener { changeMonth(1) }
        b.windowToggle.addOnButtonCheckedListener { _, checkedId, isChecked ->
            if (!isChecked) return@addOnButtonCheckedListener
            window = when (checkedId) {
                R.id.btnWeek -> HistoryStats.Window.WEEK
                R.id.btnAll -> HistoryStats.Window.ALL
                else -> HistoryStats.Window.MONTH
            }
            if (loaded) renderCharts()
        }
        b.calendarHeatmap.onDayClick = { showDayDetail(it) }

        b.modeToggle.check(R.id.btnList)
        b.windowToggle.check(R.id.btnMonth)

        viewLifecycleOwner.lifecycleScope.launch {
            val db = BaromiDatabase.getInstance(requireContext().applicationContext)
            items = db.checkinDao().getAllWithDetails()
            categories = db.categoryDao().getAll()
            enabledCategories = categories.filter { it.enabled }
            loaded = true
            calendarCategoryId = enabledCategories.firstOrNull()?.id ?: -1L
            renderList()
            buildCategoryChips()
            renderCalendar()
            renderCharts()
        }
    }

    private fun showMode(checkedId: Int) {
        val b = binding ?: return
        b.listContainer.isVisible = checkedId == R.id.btnList
        b.calendarContainer.isVisible = checkedId == R.id.btnCalendar
        b.chartsContainer.isVisible = checkedId == R.id.btnCharts
    }

    private fun renderList() {
        val b = binding ?: return
        listAdapter.submit(items, categories)
        val empty = items.isEmpty()
        b.emptyState.isVisible = empty
        b.historyList.isVisible = !empty
    }

    private fun buildCategoryChips() {
        val b = binding ?: return
        val group = b.calendarCategoryChips
        group.removeAllViews()
        group.setOnCheckedStateChangeListener(null)
        for (category in enabledCategories) {
            val chip = Chip(requireContext()).apply {
                text = category.name
                isCheckable = true
                id = View.generateViewId()
                tag = category.id
                isChecked = category.id == calendarCategoryId
            }
            group.addView(chip)
        }
        group.setOnCheckedStateChangeListener { chipGroup, checkedIds ->
            val id = checkedIds.firstOrNull() ?: return@setOnCheckedStateChangeListener
            calendarCategoryId = chipGroup.findViewById<Chip>(id).tag as Long
            renderCalendar()
        }
    }

    private fun changeMonth(delta: Long) {
        currentMonth = currentMonth.plusMonths(delta)
        clearDayDetail()
        renderCalendar()
    }

    private fun renderCalendar() {
        val b = binding ?: return
        if (!loaded) return
        b.monthLabel.text = MONTH_FORMAT.format(currentMonth)
        if (calendarCategoryId < 0) {
            b.calendarHeatmap.bind(currentMonth, emptyMap(), today)
            return
        }
        val stats = HistoryStats.dayAveragesByDate(items, calendarCategoryId, zone)
        b.calendarHeatmap.bind(currentMonth, stats, today)
    }

    private fun showDayDetail(date: LocalDate) {
        val b = binding ?: return
        val dayItems = items.filter {
            Instant.ofEpochMilli(it.checkin.answeredTime).atZone(zone).toLocalDate() == date
        }
        b.dayDetailLabel.isVisible = true
        b.dayDetailLabel.text = DAY_FORMAT.format(date)
        dayDetailAdapter.submit(dayItems, categories)
    }

    private fun clearDayDetail() {
        val b = binding ?: return
        b.dayDetailLabel.isVisible = false
        dayDetailAdapter.submit(emptyList(), categories)
    }

    private fun renderCharts() {
        val b = binding ?: return
        val list = b.chartsList
        list.removeAllViews()
        val empty = items.isEmpty()
        b.chartsEmpty.isVisible = empty
        if (empty) return

        for (category in enabledCategories) {
            val series = HistoryStats.dailyAverages(items, category.id, zone, window, today)
            list.addView(chartTitle(category.name))
            list.addView(
                BarChartView(requireContext()).apply {
                    layoutParams = chartParams()
                    // 0..5 baseline so a low average still renders a visible bar.
                    setData(series.map { BarChartView.Bar(it.average, DAY_FORMAT.format(it.date)) }, 0.0, 5.0)
                },
            )
        }

        val tags = HistoryStats.tagCounts(items, zone, window, today)
        if (tags.isNotEmpty()) {
            list.addView(chartTitle(getString(R.string.chart_tag_frequency)))
            val maxCount = tags.maxOf { it.count }.toDouble()
            list.addView(
                BarChartView(requireContext()).apply {
                    layoutParams = chartParams()
                    setData(tags.map { BarChartView.Bar(it.count.toDouble(), it.label) }, 0.0, maxCount)
                },
            )
        }
    }

    private fun chartTitle(text: String): TextView = TextView(requireContext()).apply {
        this.text = text
        setTextAppearance(resolveAttr(R.attr.textAppearanceTitleSmall))
        val top = dp(16)
        setPadding(0, top, 0, dp(4))
    }

    private fun chartParams(): LinearLayout.LayoutParams =
        LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(120))

    private fun dp(value: Int): Int =
        (value * resources.displayMetrics.density).toInt()

    private fun resolveAttr(attr: Int): Int {
        val tv = TypedValue()
        requireContext().theme.resolveAttribute(attr, tv, true)
        return tv.resourceId
    }

    override fun onDestroyView() {
        super.onDestroyView()
        binding = null
    }

    private companion object {
        val MONTH_FORMAT: DateTimeFormatter = DateTimeFormatter.ofPattern("LLLL yyyy", Locale.getDefault())
        val DAY_FORMAT: DateTimeFormatter = DateTimeFormatter.ofPattern("EEE, MMM d", Locale.getDefault())
    }
}
