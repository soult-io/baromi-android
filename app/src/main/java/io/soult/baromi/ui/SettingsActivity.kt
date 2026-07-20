package io.soult.baromi.ui

import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.google.android.material.button.MaterialButton
import com.google.android.material.timepicker.MaterialTimePicker
import com.google.android.material.timepicker.TimeFormat
import io.soult.baromi.R
import io.soult.baromi.data.BaromiDatabase
import io.soult.baromi.data.ReminderRepository
import io.soult.baromi.data.ReminderTime
import io.soult.baromi.data.Tag
import io.soult.baromi.data.TagRepository
import io.soult.baromi.databinding.ActivitySettingsBinding
import io.soult.baromi.reminder.AlarmScheduler
import io.soult.baromi.reminder.ReminderPermissions
import io.soult.baromi.reminder.rearmAllReminders
import kotlinx.coroutines.launch

/**
 * Module E — configure reminder times (add/remove within 1..8) and manage tags. Every reminder
 * change re-arms the alarms (and a removal cancels the removed time's alarm) so a change takes
 * effect immediately.
 */
class SettingsActivity : AppCompatActivity() {

    private lateinit var binding: ActivitySettingsBinding

    private val db by lazy { BaromiDatabase.getInstance(this) }
    private val reminders by lazy { ReminderRepository(db.reminderTimeDao()) }
    private val tags by lazy { TagRepository(db.tagDao()) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivitySettingsBinding.inflate(layoutInflater)
        setContentView(binding.root)
        title = getString(R.string.settings_title)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)

        binding.buttonAddTime.setOnClickListener { pickTime() }
        binding.buttonAddTag.setOnClickListener { addTag() }
        binding.buttonGrantPermissions.setOnClickListener { grantPermissions() }

        // Re-attach the result listener if a picker dialog survived a configuration change.
        (supportFragmentManager.findFragmentByTag(TIME_PICKER_TAG) as? MaterialTimePicker)
            ?.let(::attachTimePickerListener)

        ReminderPermissions.requestNotificationPermissionIfNeeded(this)
        ReminderPermissions.promptForExactAlarmIfNeeded(this)

        renderReminders()
        renderTags()
    }

    override fun onSupportNavigateUp(): Boolean {
        finish()
        return true
    }

    override fun onResume() {
        super.onResume()
        updateReminderStatus()
    }

    private fun updateReminderStatus() {
        val notificationsOk = Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
        val ready = notificationsOk && AlarmScheduler.canScheduleExact(this)
        binding.reminderStatus.text = getString(if (ready) R.string.reminders_active else R.string.reminders_need_permission)
        binding.buttonGrantPermissions.visibility = if (ready) View.GONE else View.VISIBLE
    }

    private fun grantPermissions() {
        ReminderPermissions.requestNotificationPermissionIfNeeded(this)
        ReminderPermissions.promptForExactAlarmIfNeeded(this)
    }

    private fun renderReminders() {
        lifecycleScope.launch {
            reminders.seedDefaultsIfEmpty()
            // Read first, then clear+populate in one non-suspending block so concurrent renders
            // can't interleave into duplicate rows.
            val times = reminders.all()
            binding.containerReminders.removeAllViews()
            times.forEach { time ->
                binding.containerReminders.addView(
                    listRow(getString(R.string.reminder_time_format, time.hour, time.minute)) { removeTime(time) },
                )
            }
        }
    }

    private fun renderTags() {
        lifecycleScope.launch {
            tags.seedDefaultsIfEmpty()
            val all = tags.all()
            binding.containerTags.removeAllViews()
            all.forEach { tag ->
                binding.containerTags.addView(listRow(tag.label) { removeTag(tag) })
            }
        }
    }

    private fun pickTime() {
        val picker = MaterialTimePicker.Builder()
            .setTimeFormat(TimeFormat.CLOCK_24H)
            .setHour(9)
            .setMinute(0)
            .build()
        attachTimePickerListener(picker)
        picker.show(supportFragmentManager, TIME_PICKER_TAG)
    }

    private fun attachTimePickerListener(picker: MaterialTimePicker) {
        picker.addOnPositiveButtonClickListener { addTime(picker.hour, picker.minute) }
    }

    private fun addTime(hour: Int, minute: Int) {
        lifecycleScope.launch {
            when (reminders.add(hour, minute)) {
                ReminderRepository.AddResult.ADDED -> {
                    rearmAllReminders(applicationContext)
                    renderReminders()
                }
                ReminderRepository.AddResult.DUPLICATE -> toast(R.string.reminder_duplicate)
                ReminderRepository.AddResult.MAX_REACHED -> toast(R.string.reminder_max_reached)
            }
        }
    }

    private fun removeTime(time: ReminderTime) {
        lifecycleScope.launch {
            if (reminders.remove(time)) {
                // Cancel the removed time's already-armed alarm — otherwise it keeps firing daily
                // (ReminderReceiver re-arms it from the intent), surviving until reboot.
                AlarmScheduler.cancelOne(applicationContext, time.id, time.hour, time.minute)
                rearmAllReminders(applicationContext)
                renderReminders()
            } else {
                toast(R.string.reminder_min_reached)
            }
        }
    }

    private fun addTag() {
        val label = binding.inputNewTag.text?.toString().orEmpty()
        lifecycleScope.launch {
            when (tags.add(label)) {
                TagRepository.AddResult.ADDED -> {
                    binding.inputNewTag.text?.clear()
                    renderTags()
                }
                TagRepository.AddResult.DUPLICATE -> toast(R.string.tag_duplicate)
                TagRepository.AddResult.INVALID -> toast(R.string.tag_invalid)
            }
        }
    }

    private fun removeTag(tag: Tag) {
        lifecycleScope.launch {
            if (tags.remove(tag)) renderTags() else toast(R.string.tag_in_use)
        }
    }

    private fun toast(resId: Int) = Toast.makeText(this, resId, Toast.LENGTH_SHORT).show()

    private fun listRow(label: String, onRemove: () -> Unit): View {
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        row.addView(
            TextView(this).apply {
                text = label
                textSize = 18f
                layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
            },
        )
        row.addView(
            MaterialButton(this, null, com.google.android.material.R.attr.materialButtonOutlinedStyle).apply {
                text = getString(R.string.remove)
                setOnClickListener { onRemove() }
            },
        )
        return row
    }

    private companion object {
        const val TIME_PICKER_TAG = "time-picker"
    }
}
