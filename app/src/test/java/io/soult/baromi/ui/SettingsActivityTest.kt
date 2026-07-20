package io.soult.baromi.ui

import android.view.View
import io.soult.baromi.R
import org.junit.Assert.assertNotNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner

/** Smoke test: the settings layout inflates under the Material theme without crashing. */
@RunWith(RobolectricTestRunner::class)
class SettingsActivityTest {

    @Test
    fun launches_and_inflates_settings_screen() {
        val activity = Robolectric.buildActivity(SettingsActivity::class.java).setup().get()
        assertNotNull("add-time button present", activity.findViewById<View>(R.id.buttonAddTime))
        assertNotNull("add-tag button present", activity.findViewById<View>(R.id.buttonAddTag))
    }
}
