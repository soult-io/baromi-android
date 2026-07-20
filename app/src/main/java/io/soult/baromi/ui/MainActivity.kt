package io.soult.baromi.ui

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.google.android.material.button.MaterialButton
import com.google.android.material.chip.Chip
import com.google.android.material.chip.ChipGroup
import com.google.android.material.slider.Slider
import com.google.android.material.textfield.TextInputEditText
import io.soult.baromi.R
import io.soult.baromi.capture.CheckinRecorder
import io.soult.baromi.data.BaromiDatabase
import io.soult.baromi.data.TagRepository
import io.soult.baromi.notify.Notifications
import io.soult.baromi.reminder.AlarmScheduler
import io.soult.baromi.reminder.rearmAllReminders
import kotlinx.coroutines.launch

/**
 * The check-in screen (Module B). Opened from the launcher or a reminder notification; records
 * one check-in (mood/energy/anxiety + comment + tags) and, if it came from a notification,
 * carries that prompt's scheduled time. Also hosts the reminder engine's permission + arming
 * wiring (Module C/D) until a dedicated settings screen (Module E) takes it over.
 */
class MainActivity : AppCompatActivity() {

    private val db by lazy { BaromiDatabase.getInstance(this) }
    private val recorder by lazy { CheckinRecorder(db) }
    private val tagRepository by lazy { TagRepository(db.tagDao()) }

    private var promptTime: Long? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        promptTime = intent.getLongExtra(Notifications.EXTRA_PROMPT_TIME, 0L).takeIf { it > 0L }

        bindAxis(R.id.sliderMood, R.id.valueMood, getString(R.string.axis_mood))
        bindAxis(R.id.sliderEnergy, R.id.valueEnergy, getString(R.string.axis_energy))
        bindAxis(R.id.sliderAnxiety, R.id.valueAnxiety, getString(R.string.axis_anxiety))

        loadTags()
        findViewById<MaterialButton>(R.id.buttonSave).setOnClickListener { save() }

        Notifications.ensureChannel(this)
        requestNotificationPermissionIfNeeded()
        promptForExactAlarmIfNeeded()
    }

    override fun onStart() {
        super.onStart()
        // Arm here so returning from the exact-alarm settings grant actually arms the reminders.
        lifecycleScope.launch { rearmAllReminders(applicationContext) }
    }

    private fun bindAxis(sliderId: Int, valueId: Int, label: String) {
        val slider = findViewById<Slider>(sliderId)
        val value = findViewById<TextView>(valueId)
        value.text = getString(R.string.axis_value_format, label, slider.value.toInt())
        slider.addOnChangeListener { _, v, _ ->
            value.text = getString(R.string.axis_value_format, label, v.toInt())
        }
    }

    private fun loadTags() {
        val chipGroup = findViewById<ChipGroup>(R.id.chipGroupTags)
        lifecycleScope.launch {
            tagRepository.seedDefaultsIfEmpty()
            tagRepository.all().forEach { tag ->
                chipGroup.addView(
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
        val chipGroup = findViewById<ChipGroup>(R.id.chipGroupTags)
        val tagIds = chipGroup.checkedChipIds.mapNotNull { chipId ->
            (chipGroup.findViewById<Chip>(chipId))?.tag as? Long
        }
        val comment = findViewById<TextInputEditText>(R.id.inputComment).text?.toString()

        lifecycleScope.launch {
            recorder.record(
                mood = findViewById<Slider>(R.id.sliderMood).value.toInt(),
                energy = findViewById<Slider>(R.id.sliderEnergy).value.toInt(),
                anxiety = findViewById<Slider>(R.id.sliderAnxiety).value.toInt(),
                comment = comment,
                tagIds = tagIds,
                promptTime = promptTime,
                answeredTime = System.currentTimeMillis(),
            )
            Toast.makeText(this@MainActivity, R.string.saved_confirmation, Toast.LENGTH_SHORT).show()
            finish()
        }
    }

    private fun requestNotificationPermissionIfNeeded() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), REQ_POST_NOTIFICATIONS)
        }
    }

    private fun promptForExactAlarmIfNeeded() {
        if (!AlarmScheduler.canScheduleExact(this) && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            runCatching {
                startActivity(
                    Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM, Uri.parse("package:$packageName")),
                )
            }
        }
    }

    private companion object {
        const val REQ_POST_NOTIFICATIONS = 1
    }
}
