package io.soult.baromi

import android.app.Application
import io.soult.baromi.ui.ThemePreferences

/** Applies the saved day/night preference before any activity is created. */
class BaromiApp : Application() {
    override fun onCreate() {
        super.onCreate()
        ThemePreferences.apply(this)
    }
}
