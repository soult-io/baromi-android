package io.soult.baromi.ui

import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
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
import io.soult.baromi.reminder.ReminderPermissions
import io.soult.baromi.reminder.rearmAllReminders
import kotlinx.coroutines.launch

/**
 * Module E — configure reminder times (add/remove within 1..8) and manage tags. Every reminder
 * change re-arms the alarms so a change takes effect immediately.
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

        binding.buttonAddTime.setOnClickListener { pickTime() }
        binding.buttonAddTag.setOnClickListener { addTag() }

        ReminderPermissions.requestNotificationPermissionIfNeeded(this)
        ReminderPermissions.promptForExactAlarmIfNeeded(this)

        renderReminders()
        renderTags()
    }

    private fun renderReminders() {
        lifecycleScope.launch {
            reminders.seedDefaultsIfEmpty()
            binding.containerReminders.removeAllViews()
            reminders.all().forEach { time ->
                binding.containerReminders.addView(
                    listRow(getString(R.string.reminder_time_format, time.hour, time.minute)) { removeTime(time) },
                )
            }
        }
    }

    private fun renderTags() {
        lifecycleScope.launch {
            tags.seedDefaultsIfEmpty()
            binding.containerTags.removeAllViews()
            tags.all().forEach { tag ->
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
        picker.addOnPositiveButtonClickListener { addTime(picker.hour, picker.minute) }
        picker.show(supportFragmentManager, "time-picker")
    }

    private fun addTime(hour: Int, minute: Int) {
        lifecycleScope.launch {
            if (!reminders.add(hour, minute)) {
                toast(R.string.reminder_max_reached)
            }
            rearmAllReminders(applicationContext)
            renderReminders()
        }
    }

    private fun removeTime(time: ReminderTime) {
        lifecycleScope.launch {
            if (reminders.remove(time)) {
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
}
