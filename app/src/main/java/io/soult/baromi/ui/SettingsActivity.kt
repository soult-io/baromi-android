package io.soult.baromi.ui

import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.google.android.material.button.MaterialButton
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.timepicker.MaterialTimePicker
import com.google.android.material.timepicker.TimeFormat
import io.soult.baromi.R
import io.soult.baromi.data.BaromiDatabase
import io.soult.baromi.data.Category
import io.soult.baromi.data.CategoryRepository
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
 * Module E — configure reminder times (add/remove within 1..8), manage rating categories, and
 * manage tags. Built-in categories and tags can be disabled but not deleted; custom ones (up to 3
 * each) can be deleted. Every reminder change re-arms the alarms so a change takes effect at once.
 */
class SettingsActivity : AppCompatActivity() {

    private lateinit var binding: ActivitySettingsBinding

    private val db by lazy { BaromiDatabase.getInstance(this) }
    private val reminders by lazy { ReminderRepository(db.reminderTimeDao()) }
    private val tags by lazy { TagRepository(db.tagDao()) }
    private val categories by lazy { CategoryRepository(db.categoryDao()) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivitySettingsBinding.inflate(layoutInflater)
        setContentView(binding.root)
        title = getString(R.string.settings_title)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)

        binding.buttonAddTime.setOnClickListener { pickTime() }
        binding.buttonAddCategory.setOnClickListener { addCategory() }
        binding.buttonAddTag.setOnClickListener { addTag() }
        binding.buttonGrantPermissions.setOnClickListener { grantPermissions() }
        setupThemeToggle()

        // Re-attach the result listener if a picker dialog survived a configuration change.
        (supportFragmentManager.findFragmentByTag(TIME_PICKER_TAG) as? MaterialTimePicker)
            ?.let(::attachTimePickerListener)

        ReminderPermissions.requestNotificationPermissionIfNeeded(this)
        ReminderPermissions.promptForExactAlarmIfNeeded(this)

        renderReminders()
        renderCategories()
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

    private fun setupThemeToggle() {
        val checkedId = when (ThemePreferences.load(this)) {
            AppCompatDelegate.MODE_NIGHT_NO -> R.id.themeLight
            AppCompatDelegate.MODE_NIGHT_YES -> R.id.themeDark
            else -> R.id.themeSystem
        }
        binding.themeToggle.check(checkedId)
        // Listener added after check() so the initial state doesn't trigger a mode change; the
        // guard makes a redundant re-selection a no-op. Changing the mode recreates the activity.
        binding.themeToggle.addOnButtonCheckedListener { _, id, isChecked ->
            if (!isChecked) return@addOnButtonCheckedListener
            val mode = when (id) {
                R.id.themeLight -> AppCompatDelegate.MODE_NIGHT_NO
                R.id.themeDark -> AppCompatDelegate.MODE_NIGHT_YES
                else -> AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM
            }
            if (mode != ThemePreferences.load(this)) ThemePreferences.set(this, mode)
        }
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

    private fun renderCategories() {
        lifecycleScope.launch {
            categories.seedDefaultsIfEmpty()
            val all = categories.all()
            binding.containerCategories.removeAllViews()
            all.forEach { category ->
                binding.containerCategories.addView(
                    manageRow(
                        label = category.name,
                        enabled = category.enabled,
                        canDelete = !category.builtin,
                        onToggle = { setCategoryEnabled(category, it) },
                        onRemove = { removeCategory(category) },
                    ),
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
                binding.containerTags.addView(
                    manageRow(
                        label = tag.label,
                        enabled = tag.enabled,
                        canDelete = !tag.builtin,
                        onToggle = { setTagEnabled(tag, it) },
                        onRemove = { removeTag(tag) },
                    ),
                )
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

    private fun addCategory() {
        val name = binding.inputNewCategory.text?.toString().orEmpty()
        lifecycleScope.launch {
            when (categories.add(name)) {
                CategoryRepository.AddResult.ADDED -> {
                    binding.inputNewCategory.text?.clear()
                    renderCategories()
                }
                CategoryRepository.AddResult.DUPLICATE -> toast(R.string.category_duplicate)
                CategoryRepository.AddResult.INVALID -> toast(R.string.category_invalid)
                CategoryRepository.AddResult.MAX_REACHED -> toast(R.string.category_max_reached)
            }
        }
    }

    private fun setCategoryEnabled(category: Category, enabled: Boolean) {
        lifecycleScope.launch {
            // Enabling is always safe; disabling the only enabled category would leave nothing to
            // rate on a check-in, so it's blocked atomically (guards against a rapid double-tap).
            val ok = if (enabled) {
                categories.setEnabled(category, true)
                true
            } else {
                categories.disableUnlessLast(category)
            }
            if (ok) renderCategories() else showLastCategoryAlert()
        }
    }

    private fun removeCategory(category: Category) {
        lifecycleScope.launch {
            // Deleting the only enabled category would leave nothing to rate on a check-in.
            if (categories.removeUnlessLast(category)) renderCategories() else showLastCategoryAlert()
        }
    }

    private fun showLastCategoryAlert() {
        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.category_min_title)
            .setMessage(R.string.category_min_message)
            .setPositiveButton(android.R.string.ok, null)
            .show()
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

    private fun setTagEnabled(tag: Tag, enabled: Boolean) {
        lifecycleScope.launch {
            tags.setEnabled(tag, enabled)
            renderTags()
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

    /**
     * A manageable list row: a name, an Enable/Disable toggle, and (custom items only) a Remove
     * button. Disabled items are dimmed. Built-ins pass canDelete=false so they can't be deleted.
     */
    private fun manageRow(
        label: String,
        enabled: Boolean,
        canDelete: Boolean,
        onToggle: (Boolean) -> Unit,
        onRemove: () -> Unit,
    ): View {
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        row.addView(
            TextView(this).apply {
                text = label
                textSize = 18f
                alpha = if (enabled) 1f else 0.5f
                layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
            },
        )
        row.addView(
            MaterialButton(this, null, com.google.android.material.R.attr.materialButtonOutlinedStyle).apply {
                text = getString(if (enabled) R.string.disable else R.string.enable)
                setOnClickListener { onToggle(!enabled) }
            },
        )
        if (canDelete) {
            row.addView(
                MaterialButton(this, null, com.google.android.material.R.attr.materialButtonOutlinedStyle).apply {
                    text = getString(R.string.remove)
                    layoutParams = LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.WRAP_CONTENT,
                        LinearLayout.LayoutParams.WRAP_CONTENT,
                    ).apply { marginStart = (8 * resources.displayMetrics.density).toInt() }
                    setOnClickListener { onRemove() }
                },
            )
        }
        return row
    }

    private companion object {
        const val TIME_PICKER_TAG = "time-picker"
    }
}
