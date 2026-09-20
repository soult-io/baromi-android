package io.soult.baromi.ui

import android.content.Intent
import android.os.Bundle
import android.view.Menu
import android.view.MenuItem
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import io.soult.baromi.R
import io.soult.baromi.databinding.ActivityMainBinding
import io.soult.baromi.export.exportAndShareCsv
import io.soult.baromi.notify.Notifications
import io.soult.baromi.reminder.ReminderPermissions
import io.soult.baromi.reminder.rearmAllReminders
import kotlinx.coroutines.launch

/**
 * Host screen: a bottom bar switching between Check-in (capture) and History (journal). The top
 * app bar shows the brand title ("Baromi"), a Settings action, and (on History) Export. Opened from
 * the launcher or a reminder notification (whose prompt time is passed to the initial Check-in
 * fragment).
 */
class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private var showingHistory = false

    // Set by editCheckin() just before switching to the Check-in tab; consumed by showCapture()
    // so selecting that tab builds an edit form instead of a blank one.
    private var pendingEditCheckinId: Long? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
        // Brand title in the app bar; the bottom-nav label already names the current tab, so the
        // bar shows "Baromi" rather than duplicating "Check in" / "History".
        title = getString(R.string.app_name)

        binding.bottomNav.setOnItemSelectedListener { item ->
            when (item.itemId) {
                R.id.nav_capture -> {
                    showCapture()
                    true
                }
                R.id.nav_history -> {
                    show(HistoryFragment(), history = true)
                    true
                }
                else -> false
            }
        }

        if (savedInstanceState == null) {
            val promptTime = intent.getLongExtra(Notifications.EXTRA_PROMPT_TIME, 0L).takeIf { it > 0L }
            show(CaptureFragment.newInstance(promptTime), history = false)
        }

        if (savedInstanceState == null && !OnboardingActivity.isOnboarded(this)) {
            startActivity(Intent(this, OnboardingActivity::class.java))
        }

        Notifications.ensureChannel(this)
        ReminderPermissions.requestNotificationPermissionIfNeeded(this)
    }

    override fun onStart() {
        super.onStart()
        lifecycleScope.launch { rearmAllReminders(applicationContext) }
    }

    /** Switch to the History tab (used after a check-in is saved). */
    fun showHistory() {
        binding.bottomNav.selectedItemId = R.id.nav_history
    }

    /** Open the Check-in tab to edit an existing check-in (called from History). */
    fun editCheckin(checkinId: Long) {
        pendingEditCheckinId = checkinId
        if (binding.bottomNav.selectedItemId == R.id.nav_capture) {
            showCapture() // already on the tab; rebuild it as an edit form
        } else {
            binding.bottomNav.selectedItemId = R.id.nav_capture // triggers the listener -> showCapture()
        }
    }

    private fun showCapture() {
        val editId = pendingEditCheckinId
        pendingEditCheckinId = null
        val fragment = if (editId != null) {
            CaptureFragment.newInstanceForEdit(editId)
        } else {
            CaptureFragment.newInstance(null)
        }
        show(fragment, history = false)
    }

    private fun show(fragment: Fragment, history: Boolean) {
        supportFragmentManager.beginTransaction()
            .replace(R.id.navHostContainer, fragment)
            .commit()
        showingHistory = history
        invalidateOptionsMenu()
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(if (showingHistory) R.menu.menu_history else R.menu.menu_capture, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean =
        when (item.itemId) {
            R.id.action_settings -> {
                startActivity(Intent(this, SettingsActivity::class.java))
                true
            }
            R.id.action_export -> {
                exportCsv()
                true
            }
            else -> super.onOptionsItemSelected(item)
        }

    private fun exportCsv() {
        lifecycleScope.launch {
            try {
                exportAndShareCsv(this@MainActivity)
            } catch (e: Exception) {
                Toast.makeText(this@MainActivity, R.string.export_failed, Toast.LENGTH_SHORT).show()
            }
        }
    }
}
