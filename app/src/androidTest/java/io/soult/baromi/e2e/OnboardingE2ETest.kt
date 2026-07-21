package io.soult.baromi.e2e

import android.Manifest
import android.content.Context
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.action.ViewActions.click
import androidx.test.espresso.assertion.ViewAssertions.matches
import androidx.test.espresso.matcher.ViewMatchers.isDisplayed
import androidx.test.espresso.matcher.ViewMatchers.withText
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.rule.GrantPermissionRule
import io.soult.baromi.reminder.AlarmScheduler
import io.soult.baromi.ui.MainActivity
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class OnboardingE2ETest {

    @get:Rule
    val permissions: GrantPermissionRule = GrantPermissionRule.grant(Manifest.permission.POST_NOTIFICATIONS)

    private lateinit var scenario: ActivityScenario<MainActivity>

    @Before
    fun setup() {
        AlarmScheduler.canScheduleExactOverride = true
        val ctx = ApplicationProvider.getApplicationContext<Context>()
        // Fresh install: not yet onboarded.
        ctx.getSharedPreferences("baromi_prefs", Context.MODE_PRIVATE)
            .edit().putBoolean("onboarded", false).commit()
        scenario = ActivityScenario.launch(MainActivity::class.java)
    }

    @After
    fun tearDown() {
        scenario.close()
        AlarmScheduler.canScheduleExactOverride = null
    }

    @Test
    fun first_run_shows_onboarding_then_skip_lands_on_check_in() {
        Waits.waitUntil {
            onView(withText("Welcome to Baromi")).check(matches(isDisplayed()))
        }
        onView(withText("Not now")).perform(click())
        Waits.waitUntil {
            onView(withText("How are you right now?")).check(matches(isDisplayed()))
        }
    }
}
