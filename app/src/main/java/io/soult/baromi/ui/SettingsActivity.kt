package io.soult.baromi.ui

import android.os.Bundle
import android.text.InputFilter
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import android.Manifest
import android.content.res.ColorStateList
import android.content.pm.PackageManager
import android.os.Build
import android.net.Uri
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.switchmaterial.SwitchMaterial
import com.google.android.material.timepicker.MaterialTimePicker
import com.google.android.material.timepicker.TimeFormat
import io.soult.baromi.R
import io.soult.baromi.backup.BackupManager
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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.LocalDate

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
    private val backup by lazy { BackupManager(db) }

    // SAF pickers: create a document to save the backup into, and open one to import from.
    private val exportBackupLauncher =
        registerForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
            uri?.let(::writeBackup)
        }
    private val importBackupLauncher =
        registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
            uri?.let(::readBackup)
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivitySettingsBinding.inflate(layoutInflater)
        setContentView(binding.root)
        // The category-name limit lives in Category; the tag input keeps its own XML maxLength.
        binding.inputNewCategory.filters = arrayOf(InputFilter.LengthFilter(Category.MAX_NAME_LENGTH))
        title = getString(R.string.settings_title)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)

        binding.buttonAddTime.setOnClickListener { pickTime() }
        binding.buttonAddCategory.setOnClickListener { addCategory() }
        binding.buttonAddTag.setOnClickListener { addTag() }
        binding.buttonGrantPermissions.setOnClickListener { grantPermissions() }
        binding.buttonExportBackup.setOnClickListener {
            exportBackupLauncher.launch("baromi-backup-${LocalDate.now()}.json")
        }
        binding.buttonImportBackup.setOnClickListener {
            importBackupLauncher.launch(arrayOf("application/json", "application/octet-stream", "text/*"))
        }
        setupThemeToggle()

        // Re-attach the result listener if a picker dialog survived a configuration change.
        (supportFragmentManager.findFragmentByTag(TIME_PICKER_TAG) as? MaterialTimePicker)
            ?.let(::attachTimePickerListener)

        // No cold permission prompt on open: the reminder status + "Grant permission" button surface
        // it when needed, and adding a reminder time primes the notification permission in context.
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
                    // Contextual prime: the user just asked for a reminder, so request the permission
                    // it needs now (a dialog). Exact-alarm access stays behind the Grant button.
                    ReminderPermissions.requestNotificationPermissionIfNeeded(this@SettingsActivity)
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
            if (ok) {
                renderCategories()
            } else {
                showLastCategoryAlert()
                renderCategories() // re-sync the switch, which the tap already flipped
            }
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

    private fun writeBackup(uri: Uri) {
        lifecycleScope.launch {
            try {
                val json = backup.exportJson()
                withContext(Dispatchers.IO) {
                    contentResolver.openOutputStream(uri)?.use { it.write(json.toByteArray()) }
                        ?: throw IllegalStateException("no output stream for $uri")
                }
                toast(R.string.backup_export_done)
            } catch (e: Exception) {
                toast(R.string.backup_export_failed)
            }
        }
    }

    private fun readBackup(uri: Uri) {
        lifecycleScope.launch {
            try {
                val json = withContext(Dispatchers.IO) {
                    contentResolver.openInputStream(uri)?.use { it.readBytes().decodeToString() }
                        ?: throw IllegalStateException("no input stream for $uri")
                }
                val result = backup.importJson(json)
                // Imported reminder times need arming, and the lists may have grown.
                rearmAllReminders(applicationContext)
                renderReminders()
                renderCategories()
                renderTags()
                Toast.makeText(
                    this@SettingsActivity,
                    getString(R.string.backup_import_done, result.imported, result.skipped),
                    Toast.LENGTH_LONG,
                ).show()
            } catch (e: Exception) {
                toast(R.string.backup_import_failed)
            }
        }
    }

    private fun toast(resId: Int) = Toast.makeText(this, resId, Toast.LENGTH_SHORT).show()

    // A reminder-time row: clock + time on a rounded surface, with a delete button.
    private fun listRow(label: String, onRemove: () -> Unit): View {
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setBackgroundResource(R.drawable.bg_settings_row)
            setPadding(dp(14), dp(8), dp(6), dp(8))
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT,
            ).apply { bottomMargin = dp(8) }
        }
        row.addView(
            ImageView(this).apply {
                setImageResource(R.drawable.ic_reminder_time)
                imageTintList = ColorStateList.valueOf(themeColor(androidx.appcompat.R.attr.colorPrimary))
                layoutParams = LinearLayout.LayoutParams(dp(24), dp(24)).apply { marginEnd = dp(14) }
            },
        )
        row.addView(
            TextView(this).apply {
                text = label
                setTextAppearance(com.google.android.material.R.style.TextAppearance_Material3_TitleMedium)
                layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
            },
        )
        row.addView(iconButton(R.drawable.ic_delete, getString(R.string.remove), onRemove))
        return row
    }

    /**
     * A manageable list row: a name, an on/off switch, and (custom items only) a delete button,
     * with a bottom divider. Disabled items are dimmed. Built-ins pass canDelete=false.
     */
    private fun manageRow(
        label: String,
        enabled: Boolean,
        canDelete: Boolean,
        onToggle: (Boolean) -> Unit,
        onRemove: () -> Unit,
    ): View {
        val wrapper = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT,
            )
        }
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(0, dp(6), 0, dp(6))
        }
        row.addView(
            TextView(this).apply {
                text = label
                setTextAppearance(com.google.android.material.R.style.TextAppearance_Material3_BodyLarge)
                alpha = if (enabled) 1f else 0.5f
                layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
            },
        )
        row.addView(
            SwitchMaterial(this).apply {
                isChecked = enabled
                contentDescription = label
                // Apply the switch's live post-tap state (not the captured `enabled`) so a rapid
                // double-tap settles to the final tap; a blocked disable re-renders to reset it.
                setOnClickListener { onToggle(isChecked) }
            },
        )
        if (canDelete) {
            row.addView(iconButton(R.drawable.ic_delete, getString(R.string.remove), onRemove))
        }
        wrapper.addView(row)
        wrapper.addView(
            View(this).apply {
                layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(1))
                setBackgroundColor(themeColor(com.google.android.material.R.attr.colorOutlineVariant))
            },
        )
        return wrapper
    }

    private fun iconButton(iconRes: Int, description: String, onClick: () -> Unit): View =
        ImageButton(this).apply {
            setImageResource(iconRes)
            contentDescription = description
            imageTintList = ColorStateList.valueOf(themeColor(com.google.android.material.R.attr.colorOnSurfaceVariant))
            val tv = TypedValue()
            theme.resolveAttribute(android.R.attr.selectableItemBackgroundBorderless, tv, true)
            setBackgroundResource(tv.resourceId)
            setPadding(dp(12), dp(12), dp(12), dp(12))
            layoutParams = LinearLayout.LayoutParams(dp(48), dp(48))
            setOnClickListener { onClick() }
        }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()

    private fun themeColor(attr: Int): Int {
        val tv = TypedValue()
        theme.resolveAttribute(attr, tv, true)
        return if (tv.resourceId != 0) ContextCompat.getColor(this, tv.resourceId) else tv.data
    }

    private companion object {
        const val TIME_PICKER_TAG = "time-picker"
    }
}
