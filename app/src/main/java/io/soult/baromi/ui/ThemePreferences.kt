package io.soult.baromi.ui

import android.content.Context
import androidx.appcompat.app.AppCompatDelegate

/**
 * Persists the user's day/night choice and maps it to an [AppCompatDelegate] night-mode constant.
 * Default is "follow the system". The stored value is one of the AppCompatDelegate MODE_NIGHT_*
 * ints so it can be handed straight to [AppCompatDelegate.setDefaultNightMode].
 */
object ThemePreferences {

    private const val PREFS = "baromi_prefs"
    private const val KEY_NIGHT_MODE = "night_mode"

    /** Reads the saved mode, or MODE_NIGHT_FOLLOW_SYSTEM if none was set. */
    fun load(context: Context): Int =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getInt(KEY_NIGHT_MODE, AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM)

    /** Persists [mode] and applies it immediately (recreates started activities as needed). */
    fun set(context: Context, mode: Int) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .putInt(KEY_NIGHT_MODE, mode)
            .apply()
        AppCompatDelegate.setDefaultNightMode(mode)
    }

    /** Applies the saved mode without touching storage — called once at app start. */
    fun apply(context: Context) {
        AppCompatDelegate.setDefaultNightMode(load(context))
    }
}
