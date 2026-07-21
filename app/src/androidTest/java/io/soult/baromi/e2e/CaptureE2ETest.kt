package io.soult.baromi.e2e

import android.Manifest
import android.content.Context
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.action.ViewActions.click
import androidx.test.espresso.assertion.ViewAssertions.matches
import androidx.test.espresso.matcher.ViewMatchers.isDisplayed
import androidx.test.espresso.matcher.ViewMatchers.isEnabled
import androidx.test.espresso.matcher.ViewMatchers.isNotEnabled
import androidx.test.espresso.matcher.ViewMatchers.withId
import androidx.test.espresso.matcher.ViewMatchers.withText
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.rule.GrantPermissionRule
import io.soult.baromi.R
import io.soult.baromi.data.BaromiDatabase
import io.soult.baromi.reminder.AlarmScheduler
import io.soult.baromi.ui.MainActivity
import org.hamcrest.Matchers.containsString
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class CaptureE2ETest {

    @get:Rule
    val permissions: GrantPermissionRule = GrantPermissionRule.grant(Manifest.permission.POST_NOTIFICATIONS)

    private lateinit var scenario: ActivityScenario<MainActivity>

    @Before
    fun setup() {
        // Force the exact-alarm capability true so Settings can't redirect to system settings and
        // steal foreground; the notifications permission is granted by the rule above.
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
    fun choose_all_axes_then_save_records_and_appears_in_history() {
        onView(withId(R.id.buttonSave)).check(matches(isNotEnabled()))

        onView(withText("😄")).perform(click()) // mood 5
        onView(withText("⚡")).perform(click())        // energy 5
        onView(withText("😌")).perform(click())  // anxiety 5

        onView(withId(R.id.buttonSave)).check(matches(isEnabled())).perform(click())

        Waits.waitUntil {
            onView(withText(containsString("Mood 5"))).check(matches(isDisplayed()))
        }
    }
}
