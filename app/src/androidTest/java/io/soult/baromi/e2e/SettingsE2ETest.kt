package io.soult.baromi.e2e

import android.content.Context
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.action.ViewActions.click
import androidx.test.espresso.action.ViewActions.closeSoftKeyboard
import androidx.test.espresso.action.ViewActions.typeText
import androidx.test.espresso.assertion.ViewAssertions.matches
import androidx.test.espresso.matcher.ViewMatchers.isDisplayed
import androidx.test.espresso.matcher.ViewMatchers.withContentDescription
import androidx.test.espresso.matcher.ViewMatchers.withId
import androidx.test.espresso.matcher.ViewMatchers.withText
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.soult.baromi.R
import io.soult.baromi.data.BaromiDatabase
import io.soult.baromi.ui.MainActivity
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class SettingsE2ETest {

    private lateinit var scenario: ActivityScenario<MainActivity>

    @Before
    fun setup() {
        val ctx = ApplicationProvider.getApplicationContext<Context>()
        ctx.getSharedPreferences("baromi_prefs", Context.MODE_PRIVATE)
            .edit().putBoolean("onboarded", true).commit()
        BaromiDatabase.getInstance(ctx).clearAllTables()
        scenario = ActivityScenario.launch(MainActivity::class.java)
    }

    @After
    fun tearDown() {
        scenario.close()
    }

    @Test
    fun open_settings_add_tag_then_back() {
        onView(withId(R.id.action_settings)).perform(click())
        Waits.waitUntil {
            onView(withText("Reminder times")).check(matches(isDisplayed()))
        }

        onView(withId(R.id.inputNewTag)).perform(typeText("e2etag"), closeSoftKeyboard())
        onView(withId(R.id.buttonAddTag)).perform(click())
        Waits.waitUntil {
            onView(withText("e2etag")).check(matches(isDisplayed()))
        }

        onView(withContentDescription("Navigate up")).perform(click())
        Waits.waitUntil {
            onView(withText("How are you right now?")).check(matches(isDisplayed()))
        }
    }
}
