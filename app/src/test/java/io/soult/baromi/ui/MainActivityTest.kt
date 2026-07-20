package io.soult.baromi.ui

import android.view.View
import io.soult.baromi.R
import org.junit.Assert.assertNotNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner

/** Smoke test: the host inflates with a bottom nav and a fragment container. */
@RunWith(RobolectricTestRunner::class)
class MainActivityTest {

    @Test
    fun launches_with_bottom_navigation() {
        val activity = Robolectric.buildActivity(MainActivity::class.java).setup().get()
        assertNotNull("bottom nav present", activity.findViewById<View>(R.id.bottomNav))
        assertNotNull("nav host present", activity.findViewById<View>(R.id.navHostContainer))
    }
}
