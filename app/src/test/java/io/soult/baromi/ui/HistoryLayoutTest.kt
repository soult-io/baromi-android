package io.soult.baromi.ui

import android.view.View
import io.soult.baromi.R
import org.junit.Assert.assertNotNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner

/**
 * Smoke test: the History layout inflates under the app theme, which exercises the custom
 * [CalendarHeatmapView]/[BarChartView] constructors (they read Material color attrs) and confirms
 * the three mode containers + segmented control resolve.
 */
@RunWith(RobolectricTestRunner::class)
class HistoryLayoutTest {

    @Test
    fun inflates_with_mode_containers_and_custom_views() {
        val activity = Robolectric.buildActivity(MainActivity::class.java).setup().get()
        val view = activity.layoutInflater.inflate(R.layout.fragment_history, null)

        assertNotNull("mode toggle", view.findViewById<View>(R.id.modeToggle))
        assertNotNull("list container", view.findViewById<View>(R.id.listContainer))
        assertNotNull("calendar container", view.findViewById<View>(R.id.calendarContainer))
        assertNotNull("charts container", view.findViewById<View>(R.id.chartsContainer))
        assertNotNull("calendar heatmap custom view", view.findViewById<View>(R.id.calendarHeatmap))
        assertNotNull("charts list", view.findViewById<View>(R.id.chartsList))
        assertNotNull("window toggle", view.findViewById<View>(R.id.windowToggle))
    }
}
