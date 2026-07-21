package io.soult.baromi.ui

import android.content.Context
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import io.soult.baromi.databinding.ActivityOnboardingBinding
import io.soult.baromi.notify.Notifications
import io.soult.baromi.reminder.ReminderPermissions

/**
 * First-run flow. Introduces the app and offers reminders as an optional step, so the (most
 * important) permission ask is surfaced up front instead of hidden in Settings. The app works
 * without reminders as a manual check-in journal.
 */
class OnboardingActivity : AppCompatActivity() {

    private lateinit var binding: ActivityOnboardingBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityOnboardingBinding.inflate(layoutInflater)
        setContentView(binding.root)
        Notifications.ensureChannel(this)

        binding.buttonEnableReminders.setOnClickListener {
            markOnboarded()
            ReminderPermissions.requestNotificationPermissionIfNeeded(this)
            ReminderPermissions.promptForExactAlarmIfNeeded(this)
            finish()
        }
        binding.buttonSkip.setOnClickListener {
            markOnboarded()
            finish()
        }
    }

    private fun markOnboarded() {
        getSharedPreferences(PREFS, MODE_PRIVATE).edit().putBoolean(KEY_ONBOARDED, true).apply()
    }

    companion object {
        private const val PREFS = "baromi_prefs"
        private const val KEY_ONBOARDED = "onboarded"

        fun isOnboarded(context: Context): Boolean =
            context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getBoolean(KEY_ONBOARDED, false)
    }
}
