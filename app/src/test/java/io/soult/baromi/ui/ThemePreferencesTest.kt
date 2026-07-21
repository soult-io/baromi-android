package io.soult.baromi.ui

import android.content.Context
import androidx.appcompat.app.AppCompatDelegate
import androidx.test.core.app.ApplicationProvider
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class ThemePreferencesTest {

    private val ctx = ApplicationProvider.getApplicationContext<Context>()

    @After
    fun reset() {
        ctx.getSharedPreferences("baromi_prefs", Context.MODE_PRIVATE).edit().clear().apply()
        AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM)
    }

    @Test
    fun default_is_follow_system() {
        assertEquals(AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM, ThemePreferences.load(ctx))
    }

    @Test
    fun set_persists_and_applies() {
        ThemePreferences.set(ctx, AppCompatDelegate.MODE_NIGHT_YES)
        assertEquals(AppCompatDelegate.MODE_NIGHT_YES, ThemePreferences.load(ctx))
        assertEquals(AppCompatDelegate.MODE_NIGHT_YES, AppCompatDelegate.getDefaultNightMode())
    }

    @Test
    fun apply_reflects_saved_mode() {
        ThemePreferences.set(ctx, AppCompatDelegate.MODE_NIGHT_NO)
        AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM) // clobber
        ThemePreferences.apply(ctx)
        assertEquals(AppCompatDelegate.MODE_NIGHT_NO, AppCompatDelegate.getDefaultNightMode())
    }
}
