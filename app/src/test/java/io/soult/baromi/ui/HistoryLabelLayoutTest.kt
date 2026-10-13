package io.soult.baromi.ui

import android.content.Context
import android.content.res.Configuration
import android.view.View
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import androidx.test.core.app.ApplicationProvider
import io.soult.baromi.R
import io.soult.baromi.data.Category
import io.soult.baromi.data.Checkin
import io.soult.baromi.data.CheckinValue
import io.soult.baromi.data.CheckinWithDetails
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.GraphicsMode

/**
 * The category name under each History face must be readable, not clipped to the icon width
 * ("Energ", "Anxie"). Normal names render in full; a maximum-length custom name stays within 2
 * lines and ends in an ellipsis. Measured with real text layout at normal and 200% font scale.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class HistoryLabelLayoutTest {

    private val builtins = listOf(
        Category(id = 1, key = "mood", name = "Mood", builtin = true, position = 0),
        Category(id = 2, key = "energy", name = "Energy", builtin = true, position = 1),
        Category(id = 3, key = "anxiety", name = "Anxiety", builtin = true, position = 2),
    )
    private val longName = "Social battery after a long day at work" // 39 chars
    private val longCustom = Category(id = 4, key = null, name = longName.padEnd(Category.MAX_NAME_LENGTH, 'x'), builtin = false, position = 3)

    private fun context(fontScale: Float): Context {
        val base = ApplicationProvider.getApplicationContext<Context>()
        val config = Configuration(base.resources.configuration).apply { this.fontScale = fontScale }
        return base.createConfigurationContext(config).apply { setTheme(R.style.Theme_Baromi) }
    }

    /** Binds one row holding all [categories], lays it out on a 360dp-wide card, returns the name labels. */
    private fun labels(fontScale: Float, categories: List<Category>): List<TextView> {
        val ctx = context(fontScale)
        val row = CheckinWithDetails(
            checkin = Checkin(id = 1, promptTime = null, answeredTime = 1000L, comment = null),
            values = categories.map { CheckinValue(checkinId = 1, categoryId = it.id, value = 3) },
            tags = emptyList(),
        )
        val adapter = CheckinAdapter().apply { submit(listOf(row), categories) }
        val parent = FrameLayout(ctx)
        val vh = adapter.onCreateViewHolder(parent, 0)
        adapter.onBindViewHolder(vh, 0)
        val width = (360 * ctx.resources.displayMetrics.density).toInt()
        vh.itemView.measure(
            View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED),
        )
        vh.itemView.layout(0, 0, vh.itemView.measuredWidth, vh.itemView.measuredHeight)
        val axesRow = vh.itemView.findViewById<LinearLayout>(R.id.axesRow)
        return (0 until axesRow.childCount).map { (axesRow.getChildAt(it) as LinearLayout).getChildAt(1) as TextView }
    }

    private fun TextView.ellipsized(): Boolean =
        (0 until layout.lineCount).any { layout.getEllipsisCount(it) > 0 }

    /** A one-word name must sit on a single line, unbroken and not ellipsized ("Energ" + "y" fails). */
    private fun assertFullyShown(label: TextView) {
        assertFalse("'${label.text}' is ellipsized", label.ellipsized())
        assertEquals("'${label.text}' wraps mid-word", 1, label.layout.lineCount)
        val needed = label.paint.measureText(label.text.toString())
        val available = label.width - label.totalPaddingLeft - label.totalPaddingRight
        assertTrue("'${label.text}' needs ${needed}px but has ${available}px", needed <= available)
    }

    @Test
    fun builtin_names_render_in_full_at_normal_font() {
        labels(1.0f, builtins).forEach(::assertFullyShown)
    }

    @Test
    fun builtin_names_render_in_full_at_200_percent_font() {
        val large = labels(2.0f, builtins)
        // Guard against a vacuous pass: the larger font must actually apply.
        val ratio = large.first().textSize / labels(1.0f, builtins).first().textSize
        assertTrue("font scale not applied (ratio $ratio)", ratio in 1.9f..2.1f)
        large.forEach(::assertFullyShown)
    }

    @Test
    fun max_length_custom_name_stops_at_two_lines_with_ellipsis() {
        listOf(1.0f, 2.0f).forEach { scale ->
            val label = labels(scale, builtins + longCustom).last()
            assertTrue("scale $scale: more than 2 lines", label.layout.lineCount <= 2)
            assertTrue("scale $scale: long name not ellipsized", label.ellipsized())
        }
    }

    @Test
    fun columns_stay_between_56_and_88_dp() {
        val minDp = 56f
        val maxDp = 88f
        val pxRoundingDp = 0.5f // dp() truncates to whole pixels
        labels(1.0f, builtins + longCustom).forEach { label ->
            val cellWidthDp = (label.parent as View).width / label.resources.displayMetrics.density
            assertTrue("column ${cellWidthDp}dp < ${minDp}dp", cellWidthDp >= minDp - pxRoundingDp)
            assertTrue("column ${cellWidthDp}dp > ${maxDp}dp", cellWidthDp <= maxDp + pxRoundingDp)
        }
    }

    @Test
    fun label_keeps_the_full_name_for_screen_readers() {
        val label = labels(1.0f, builtins + longCustom).last()
        assertEquals(longCustom.name, label.text.toString())
    }
}
