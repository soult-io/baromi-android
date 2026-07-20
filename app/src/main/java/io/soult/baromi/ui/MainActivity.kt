package io.soult.baromi.ui

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.Menu
import android.view.MenuItem
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.FileProvider
import androidx.lifecycle.lifecycleScope
import com.google.android.material.chip.Chip
import com.google.android.material.slider.Slider
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
 * The check-in screen (Module B). Opened from the launcher or a reminder notification; records
 * one check-in (mood/energy/anxiety + comment + tags) and, if it came from a notification,
 * carries that prompt's scheduled time. Menu opens Settings (Module E) and CSV export (Module F);
 * it also requests the reminder permissions and arms the alarms on launch.
 */
class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding

    private val db by lazy { BaromiDatabase.getInstance(this) }
    private val recorder by lazy { CheckinRecorder(db) }
    private val tagRepository by lazy { TagRepository(db.tagDao()) }
    private val exporter by lazy { CheckinExporter(db) }

    private var promptTime: Long? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        promptTime = intent.getLongExtra(Notifications.EXTRA_PROMPT_TIME, 0L).takeIf { it > 0L }

        bindAxis(binding.sliderMood, binding.valueMood, getString(R.string.axis_mood))
        bindAxis(binding.sliderEnergy, binding.valueEnergy, getString(R.string.axis_energy))
        bindAxis(binding.sliderAnxiety, binding.valueAnxiety, getString(R.string.axis_anxiety))

        loadTags()
        binding.buttonSave.setOnClickListener { save() }

        Notifications.ensureChannel(this)
        ReminderPermissions.requestNotificationPermissionIfNeeded(this)
        ReminderPermissions.promptForExactAlarmIfNeeded(this)
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

    private fun bindAxis(slider: Slider, value: TextView, label: String) {
        value.text = getString(R.string.axis_value_format, label, slider.value.toInt())
        slider.addOnChangeListener { _, v, _ ->
            value.text = getString(R.string.axis_value_format, label, v.toInt())
        }
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
        // Disable immediately so a double-tap can't write two near-duplicate check-ins.
        binding.buttonSave.isEnabled = false

        val tagIds = binding.chipGroupTags.checkedChipIds.mapNotNull { chipId ->
            binding.chipGroupTags.findViewById<Chip>(chipId)?.tag as? Long
        }
        val comment = binding.inputComment.text?.toString()
        val mood = binding.sliderMood.value.toInt()
        val energy = binding.sliderEnergy.value.toInt()
        val anxiety = binding.sliderAnxiety.value.toInt()

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
                binding.buttonSave.isEnabled = true
            }
        }
    }

    private fun exportCsv() {
        lifecycleScope.launch {
            try {
                // Serialize + write off the main thread; surface failures instead of crashing.
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
}
