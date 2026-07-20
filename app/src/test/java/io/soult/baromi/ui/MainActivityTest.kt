package io.soult.baromi.ui

import android.widget.Button
import com.google.android.material.button.MaterialButtonToggleGroup
import io.soult.baromi.R
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class MainActivityTest {

    @Test
    fun launches_with_five_emoji_per_axis_and_save_disabled() {
        val activity = Robolectric.buildActivity(MainActivity::class.java).setup().get()
        val save = activity.findViewById<Button>(R.id.buttonSave)
        assertNotNull("save button present", save)
        assertFalse("save is disabled until all axes are chosen", save.isEnabled)
        assertEquals(5, activity.findViewById<MaterialButtonToggleGroup>(R.id.toggleMood).childCount)
        assertEquals(5, activity.findViewById<MaterialButtonToggleGroup>(R.id.toggleEnergy).childCount)
        assertEquals(5, activity.findViewById<MaterialButtonToggleGroup>(R.id.toggleAnxiety).childCount)
    }

    @Test
    fun choosing_all_three_axes_enables_save() {
        val activity = Robolectric.buildActivity(MainActivity::class.java).setup().get()
        val save = activity.findViewById<Button>(R.id.buttonSave)
        val mood = activity.findViewById<MaterialButtonToggleGroup>(R.id.toggleMood)
        val energy = activity.findViewById<MaterialButtonToggleGroup>(R.id.toggleEnergy)
        val anxiety = activity.findViewById<MaterialButtonToggleGroup>(R.id.toggleAnxiety)

        mood.check(mood.getChildAt(4).id) // very good
        energy.check(energy.getChildAt(2).id) // okay
        assertFalse("only two of three chosen", save.isEnabled)

        anxiety.check(anxiety.getChildAt(0).id) // very anxious
        assertTrue("all three chosen -> save enabled", save.isEnabled)
    }
}
