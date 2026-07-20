package io.soult.baromi.ui

import android.widget.Button
import io.soult.baromi.R
import org.junit.Assert.assertNotNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner

/** Smoke test: the capture layout inflates under the Material theme without crashing. */
@RunWith(RobolectricTestRunner::class)
class MainActivityTest {

    @Test
    fun launches_and_inflates_capture_screen() {
        val activity = Robolectric.buildActivity(MainActivity::class.java).setup().get()
        assertNotNull("save button present", activity.findViewById<Button>(R.id.buttonSave))
        assertNotNull("mood slider present", activity.findViewById<android.view.View>(R.id.sliderMood))
    }
}
