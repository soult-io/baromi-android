package io.soult.baromi.ui

import android.content.Context
import android.widget.FrameLayout
import android.widget.LinearLayout
import androidx.test.core.app.ApplicationProvider
import io.soult.baromi.R
import io.soult.baromi.data.Category
import io.soult.baromi.data.Checkin
import io.soult.baromi.data.CheckinValue
import io.soult.baromi.data.CheckinWithDetails
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class CheckinAdapterTest {

    private val context = ApplicationProvider.getApplicationContext<Context>().apply { setTheme(R.style.Theme_Baromi) }

    private fun row(vararg values: Pair<Long, Int>) = CheckinWithDetails(
        checkin = Checkin(id = 1, promptTime = null, answeredTime = 1000L, comment = null),
        values = values.map { (catId, v) -> CheckinValue(checkinId = 1, categoryId = catId, value = v) },
        tags = emptyList(),
    )

    private fun bindFirst(adapter: CheckinAdapter): LinearLayout {
        val parent = FrameLayout(context)
        val vh = adapter.onCreateViewHolder(parent, 0)
        adapter.onBindViewHolder(vh, 0)
        return vh.itemView.findViewById(R.id.axesRow)
    }

    @Test
    fun renders_one_cell_per_axis_with_face_content_descriptions() {
        val mood = Category(id = 1, key = "mood", name = "Mood", builtin = true, position = 0)
        val adapter = CheckinAdapter()
        adapter.submit(listOf(row(1L to 5)), listOf(mood))

        val axesRow = bindFirst(adapter)
        assertEquals(1, axesRow.childCount)
        val cell = axesRow.getChildAt(0) as LinearLayout
        // Built-in category: the face icon carries the worded label for accessibility.
        assertEquals("Mood: very good", cell.getChildAt(0).contentDescription)
    }

    @Test
    fun custom_category_falls_back_to_the_number() {
        val focus = Category(id = 2, key = null, name = "Focus", builtin = false, position = 3)
        val adapter = CheckinAdapter()
        adapter.submit(listOf(row(2L to 3)), listOf(focus))

        val axesRow = bindFirst(adapter)
        val cell = axesRow.getChildAt(0) as LinearLayout
        assertEquals("Focus: 3", cell.getChildAt(0).contentDescription)
    }
}
