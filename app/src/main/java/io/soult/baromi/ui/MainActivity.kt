package io.soult.baromi.ui

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.FileProvider
import androidx.lifecycle.lifecycleScope
import com.google.android.material.button.MaterialButton
import com.google.android.material.button.MaterialButtonToggleGroup
import com.google.android.material.chip.Chip
import io.soult.baromi.R
import io.soult.baromi.capture.CheckinRecorder
import io.soult.baromi.data.BaromiDatabase
import io.soult.baromi.data.TagRepository
import io.soult.baromi.databinding.ActivityMainBinding
import io.soult.baromi.export.CheckinExporter
import io.soult.baromi.notify.Notifications
import io.soult.baromi.reminder.ReminderPermissions
import io.soult.baromi.reminder.rearmAllReminders
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException

/**
 * The check-in screen (Module B). Each axis is picked from a row of five emoji (mapped to the
 * frozen 1..5 scale, so the data model and CSV are unchanged); Save stays disabled until all
 * three axes are chosen, so every recorded value is a deliberate one. Opened from the launcher
 * or a reminder notification (which carries that prompt's scheduled time). Menu opens Settings
 * (Module E) and CSV export (Module F).
 */
class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding

    private val db by lazy { BaromiDatabase.getInstance(this) }
    private val recorder by lazy { CheckinRecorder(db) }
    private val tagRepository by lazy { TagRepository(db.tagDao()) }
    private val exporter by lazy { CheckinExporter(db) }

    private var promptTime: Long? = null
    private var moodValue: Int? = null
    private var energyValue: Int? = null
    private var anxietyValue: Int? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        promptTime = intent.getLongExtra(Notifications.EXTRA_PROMPT_TIME, 0L).takeIf { it > 0L }

        setupAxis(binding.toggleMood, binding.captionMood, getString(R.string.axis_mood), MOOD_EMOJI, MOOD_LABELS) { moodValue = it }
        setupAxis(binding.toggleEnergy, binding.captionEnergy, getString(R.string.axis_energy), ENERGY_EMOJI, ENERGY_LABELS) { energyValue = it }
        setupAxis(binding.toggleAnxiety, binding.captionAnxiety, getString(R.string.axis_anxiety), ANXIETY_EMOJI, ANXIETY_LABELS) { anxietyValue = it }
        updateSaveEnabled()

        loadTags()
        binding.buttonSave.setOnClickListener { save() }

        Notifications.ensureChannel(this)
        // Notification permission is a normal in-app dialog. The exact-alarm grant (which leaves
        // the app for a system settings screen) is prompted from Settings instead.
        ReminderPermissions.requestNotificationPermissionIfNeeded(this)
    }

    override fun onStart() {
        super.onStart()
        // Arm here so returning from the exact-alarm settings grant actually arms the reminders.
        lifecycleScope.launch { rearmAllReminders(applicationContext) }
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.main_menu, menu)
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

    /** Fills a toggle group with five emoji buttons (value = 1..5) and wires the caption + save gate. */
    private fun setupAxis(
        group: MaterialButtonToggleGroup,
        caption: TextView,
        axisName: String,
        emojis: List<String>,
        labels: List<String>,
        onValue: (Int?) -> Unit,
    ) {
        caption.text = axisName
        emojis.forEachIndexed { index, emoji ->
            group.addView(emojiButton(emoji, value = index + 1, label = labels[index]))
        }
        group.addOnButtonCheckedListener { checkedGroup, _, _ ->
            val checkedId = checkedGroup.checkedButtonId
            val value = if (checkedId == View.NO_ID) null else checkedGroup.findViewById<View>(checkedId)?.tag as? Int
            caption.text = if (value == null) axisName else getString(R.string.axis_caption_format, axisName, labels[value - 1])
            onValue(value)
            updateSaveEnabled()
        }
    }

    private fun emojiButton(emoji: String, value: Int, label: String): MaterialButton =
        MaterialButton(this, null, com.google.android.material.R.attr.materialButtonOutlinedStyle).apply {
            id = View.generateViewId()
            text = emoji
            textSize = 20f
            tag = value
            contentDescription = label
            insetTop = 0
            insetBottom = 0
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        }

    private fun updateSaveEnabled() {
        binding.buttonSave.isEnabled = moodValue != null && energyValue != null && anxietyValue != null
    }

    private fun loadTags() {
        lifecycleScope.launch {
            tagRepository.seedDefaultsIfEmpty()
            tagRepository.all().forEach { tag ->
                binding.chipGroupTags.addView(
                    Chip(this@MainActivity).apply {
                        text = tag.label
                        isCheckable = true
                        this.tag = tag.id
                    },
                )
            }
        }
    }

    private fun save() {
        val mood = moodValue ?: return
        val energy = energyValue ?: return
        val anxiety = anxietyValue ?: return

        // Disable immediately so a double-tap can't write two near-duplicate check-ins.
        binding.buttonSave.isEnabled = false

        val tagIds = binding.chipGroupTags.checkedChipIds.mapNotNull { chipId ->
            binding.chipGroupTags.findViewById<Chip>(chipId)?.tag as? Long
        }
        val comment = binding.inputComment.text?.toString()

        lifecycleScope.launch {
            try {
                // NonCancellable: a rotation/kill mid-write must not drop a committed check-in.
                withContext(NonCancellable) {
                    recorder.record(
                        mood = mood,
                        energy = energy,
                        anxiety = anxiety,
                        comment = comment,
                        tagIds = tagIds,
                        promptTime = promptTime,
                        answeredTime = System.currentTimeMillis(),
                    )
                }
                Toast.makeText(this@MainActivity, R.string.saved_confirmation, Toast.LENGTH_SHORT).show()
                finish()
            } catch (e: Exception) {
                updateSaveEnabled()
            }
        }
    }

    private fun exportCsv() {
        lifecycleScope.launch {
            try {
                val uri = withContext(Dispatchers.IO) { writeCsvToCache(exporter.exportCsv()) }
                shareCsv(uri)
            } catch (e: Exception) {
                Toast.makeText(this@MainActivity, R.string.export_failed, Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun writeCsvToCache(csv: String): Uri {
        val dir = File(cacheDir, "exports")
        if (!dir.exists() && !dir.mkdirs()) {
            throw IOException("could not create export directory: $dir")
        }
        val file = File(dir, "baromi-export.csv")
        file.writeText(csv)
        return FileProvider.getUriForFile(this, "$packageName.fileprovider", file)
    }

    private fun shareCsv(uri: Uri) {
        val send = Intent(Intent.ACTION_SEND).apply {
            type = "text/csv"
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        startActivity(Intent.createChooser(send, getString(R.string.menu_export)))
    }

    private companion object {
        // Ordered low(1) -> high(5), matching the frozen ScaleSchema direction (higher = better).
        val MOOD_EMOJI = listOf("😢", "😕", "😐", "🙂", "😄")
        val MOOD_LABELS = listOf("very low", "low", "okay", "good", "very good")
        val ENERGY_EMOJI = listOf("😴", "🥱", "😐", "🙂", "⚡")
        val ENERGY_LABELS = listOf("drained", "low", "okay", "good", "energized")
        val ANXIETY_EMOJI = listOf("😰", "😟", "😐", "🙂", "😌")
        val ANXIETY_LABELS = listOf("very anxious", "anxious", "okay", "calm", "very calm")
    }
}
