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
import androidx.test.espresso.matcher.ViewMatchers.isChecked
import androidx.test.espresso.matcher.ViewMatchers.isDisplayed
import androidx.test.espresso.matcher.ViewMatchers.isNotChecked
import androidx.test.espresso.matcher.ViewMatchers.withContentDescription
import androidx.test.espresso.matcher.ViewMatchers.withId
import androidx.test.espresso.matcher.ViewMatchers.withText
import androidx.appcompat.app.AppCompatDelegate
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.rule.GrantPermissionRule
import io.soult.baromi.R
import io.soult.baromi.data.BaromiDatabase
import io.soult.baromi.reminder.AlarmScheduler
import io.soult.baromi.ui.MainActivity
import org.hamcrest.Matchers.containsString
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class SettingsE2ETest {

    @get:Rule
    val permissions: GrantPermissionRule = GrantPermissionRule.grant(Manifest.permission.POST_NOTIFICATIONS)

    private lateinit var scenario: ActivityScenario<MainActivity>

    @Before
    fun setup() {
        AlarmScheduler.canScheduleExactOverride = true
        val ctx = ApplicationProvider.getApplicationContext<Context>()
        ctx.getSharedPreferences("baromi_prefs", Context.MODE_PRIVATE)
            .edit().putBoolean("onboarded", true).remove("night_mode").commit()
        resetNightMode()
        BaromiDatabase.getInstance(ctx).clearAllTables()
        scenario = ActivityScenario.launch(MainActivity::class.java)
    }

    @After
    fun tearDown() {
        scenario.close()
        AlarmScheduler.canScheduleExactOverride = null
        // Night mode is global process state — reset it so it can't leak into other tests.
        resetNightMode()
    }

    // setDefaultNightMode recreates active delegates, which must happen on the main thread.
    private fun resetNightMode() {
        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM)
        }
    }

    @Test
    fun open_settings_add_tag_then_back() {
        onView(withId(R.id.action_settings)).perform(click())
        Waits.waitUntil {
            onView(withText("Reminder times")).check(matches(isDisplayed()))
        }

        onView(withId(R.id.inputNewTag)).perform(scrollTo(), typeText("e2etag"), closeSoftKeyboard())
        onView(withId(R.id.buttonAddTag)).perform(scrollTo(), click())
        Waits.waitUntil {
            onView(withText("e2etag")).check(matches(isDisplayed()))
        }

        onView(withContentDescription("Navigate up")).perform(click())
        Waits.waitUntil {
            onView(withText("How are you right now?")).check(matches(isDisplayed()))
        }
    }

    @Test
    fun disabling_the_last_category_is_blocked_with_an_alert() {
        onView(withId(R.id.action_settings)).perform(click())
        Waits.waitUntil {
            onView(withText("Reminder times")).check(matches(isDisplayed()))
        }

        // Disable two of the three built-in categories.
        disableCategory("Mood")
        disableCategory("Energy")

        // Disabling the last enabled category (Anxiety) must be blocked with an alert.
        onView(withContentDescription("Anxiety")).perform(scrollTo(), click())
        Waits.waitUntil {
            onView(withText(containsString("add or enable another category first")))
                .check(matches(isDisplayed()))
        }
        onView(withText(android.R.string.ok)).perform(click())

        // Anxiety stays enabled — its switch is re-synced back to on.
        Waits.waitUntil {
            onView(withContentDescription("Anxiety")).check(matches(isChecked()))
        }
    }

    @Test
    fun theme_toggle_switches_to_dark() {
        onView(withId(R.id.action_settings)).perform(click())
        Waits.waitUntil {
            onView(withText("Appearance")).check(matches(isDisplayed()))
        }
        onView(withId(R.id.themeDark)).perform(scrollTo(), click())
        Waits.waitUntil {
            assertEquals(AppCompatDelegate.MODE_NIGHT_YES, AppCompatDelegate.getDefaultNightMode())
        }
    }

    /** Taps the given category's switch (named by its label) and waits for it to read off. */
    private fun disableCategory(name: String) {
        onView(withContentDescription(name)).perform(scrollTo(), click())
        Waits.waitUntil {
            onView(withContentDescription(name)).check(matches(isNotChecked()))
        }
    }
}
