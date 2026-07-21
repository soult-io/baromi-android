package io.soult.baromi.e2e

import android.Manifest
import android.content.Context
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.action.ViewActions.click
import androidx.test.espresso.action.ViewActions.closeSoftKeyboard
import androidx.test.espresso.action.ViewActions.scrollTo
import androidx.test.espresso.action.ViewActions.typeText
import androidx.test.espresso.assertion.ViewAssertions.matches
import androidx.test.espresso.matcher.ViewMatchers.isDisplayed
import androidx.test.espresso.matcher.ViewMatchers.withContentDescription
import androidx.test.espresso.matcher.ViewMatchers.withId
import androidx.test.espresso.matcher.ViewMatchers.withText
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.rule.GrantPermissionRule
import io.soult.baromi.R
import io.soult.baromi.data.BaromiDatabase
import io.soult.baromi.reminder.AlarmScheduler
import io.soult.baromi.ui.MainActivity
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** Regression for the "added category doesn't show on check-in" bug: a category added in Settings
 *  must appear on the capture screen when the user returns to it. */
@RunWith(AndroidJUnit4::class)
class CategoryE2ETest {

    @get:Rule
    val permissions: GrantPermissionRule = GrantPermissionRule.grant(Manifest.permission.POST_NOTIFICATIONS)

    private lateinit var scenario: ActivityScenario<MainActivity>

    @Before
    fun setup() {
        AlarmScheduler.canScheduleExactOverride = true
        val ctx = ApplicationProvider.getApplicationContext<Context>()
        ctx.getSharedPreferences("baromi_prefs", Context.MODE_PRIVATE)
            .edit().putBoolean("onboarded", true).commit()
        BaromiDatabase.getInstance(ctx).clearAllTables()
        scenario = ActivityScenario.launch(MainActivity::class.java)
    }

    @After
    fun tearDown() {
        scenario.close()
        AlarmScheduler.canScheduleExactOverride = null
    }

    @Test
    fun category_added_in_settings_appears_on_check_in() {
        onView(withId(R.id.action_settings)).perform(click())
        Waits.waitUntil {
            onView(withText("Reminder times")).check(matches(isDisplayed()))
        }

        onView(withId(R.id.inputNewCategory)).perform(scrollTo(), typeText("Focus"), closeSoftKeyboard())
        onView(withId(R.id.buttonAddCategory)).perform(scrollTo(), click())
        Waits.waitUntil {
            onView(withText("Focus")).perform(scrollTo()).check(matches(isDisplayed()))
        }

        onView(withContentDescription("Navigate up")).perform(click())

        // Back on the check-in screen, the new category's row must be present.
        Waits.waitUntil {
            onView(withText("How are you right now?")).check(matches(isDisplayed()))
        }
        Waits.waitUntil {
            onView(withText("Focus")).perform(scrollTo()).check(matches(isDisplayed()))
        }
    }
}
