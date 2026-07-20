package io.soult.baromi.ui

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.google.android.material.button.MaterialButton
import com.google.android.material.button.MaterialButtonToggleGroup
import com.google.android.material.chip.Chip
import io.soult.baromi.R
import io.soult.baromi.capture.CheckinRecorder
import io.soult.baromi.data.BaromiDatabase
import io.soult.baromi.data.TagRepository
import io.soult.baromi.databinding.FragmentCaptureBinding
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** The check-in screen (Check-in tab). Each axis is a row of five emoji mapped to the 1..5 scale. */
class CaptureFragment : Fragment() {

    private var binding: FragmentCaptureBinding? = null

    private val db by lazy { BaromiDatabase.getInstance(requireContext().applicationContext) }
    private val recorder by lazy { CheckinRecorder(db) }
    private val tagRepository by lazy { TagRepository(db.tagDao()) }

    private var promptTime: Long? = null
    private var moodValue: Int? = null
    private var energyValue: Int? = null
    private var anxietyValue: Int? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        promptTime = (arguments?.getLong(ARG_PROMPT_TIME, 0L) ?: 0L).takeIf { it > 0L }
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        val b = FragmentCaptureBinding.inflate(inflater, container, false)
        binding = b
        return b.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        val b = binding ?: return
        setupAxis(b.toggleMood, b.captionMood, getString(R.string.axis_mood), MOOD_EMOJI, MOOD_LABELS) { moodValue = it }
        setupAxis(b.toggleEnergy, b.captionEnergy, getString(R.string.axis_energy), ENERGY_EMOJI, ENERGY_LABELS) { energyValue = it }
        setupAxis(b.toggleAnxiety, b.captionAnxiety, getString(R.string.axis_anxiety), ANXIETY_EMOJI, ANXIETY_LABELS) { anxietyValue = it }
        updateSaveEnabled()
        loadTags()
        b.buttonSave.setOnClickListener { save() }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        binding = null
    }

    private fun setupAxis(group: MaterialButtonToggleGroup, caption: TextView, axisName: String, emojis: List<String>, labels: List<String>, onValue: (Int?) -> Unit) {
        caption.text = axisName
        emojis.forEachIndexed { index, emoji -> group.addView(emojiButton(emoji, index + 1, labels[index])) }
        group.addOnButtonCheckedListener { checkedGroup, _, _ ->
            val checkedId = checkedGroup.checkedButtonId
            val value = if (checkedId == View.NO_ID) null else checkedGroup.findViewById<View>(checkedId)?.tag as? Int
            caption.text = if (value == null) axisName else getString(R.string.axis_caption_format, axisName, labels[value - 1])
            onValue(value)
            updateSaveEnabled()
        }
    }

    private fun emojiButton(emoji: String, value: Int, label: String): MaterialButton =
        MaterialButton(requireContext(), null, com.google.android.material.R.attr.materialButtonOutlinedStyle).apply {
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
        binding?.buttonSave?.isEnabled = moodValue != null && energyValue != null && anxietyValue != null
    }

    private fun loadTags() {
        val b = binding ?: return
        viewLifecycleOwner.lifecycleScope.launch {
            tagRepository.seedDefaultsIfEmpty()
            tagRepository.all().forEach { tag ->
                b.chipGroupTags.addView(
                    Chip(requireContext()).apply {
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
        val b = binding ?: return
        b.buttonSave.isEnabled = false

        val tagIds = b.chipGroupTags.checkedChipIds.mapNotNull { chipId ->
            b.chipGroupTags.findViewById<Chip>(chipId)?.tag as? Long
        }
        val comment = b.inputComment.text?.toString()

        viewLifecycleOwner.lifecycleScope.launch {
            try {
                withContext(NonCancellable) {
                    recorder.record(
                        mood = mood, energy = energy, anxiety = anxiety,
                        comment = comment, tagIds = tagIds,
                        promptTime = promptTime, answeredTime = System.currentTimeMillis(),
                    )
                }
                Toast.makeText(requireContext(), R.string.saved_confirmation, Toast.LENGTH_SHORT).show()
                (activity as? MainActivity)?.showHistory()
            } catch (e: Exception) {
                updateSaveEnabled()
            }
        }
    }

    companion object {
        private const val ARG_PROMPT_TIME = "prompt_time"

        fun newInstance(promptTime: Long?): CaptureFragment =
            CaptureFragment().apply {
                arguments = Bundle().apply { if (promptTime != null) putLong(ARG_PROMPT_TIME, promptTime) }
            }

        private val MOOD_EMOJI = listOf("😢", "😕", "😐", "🙂", "😄")
        private val MOOD_LABELS = listOf("very low", "low", "okay", "good", "very good")
        private val ENERGY_EMOJI = listOf("😴", "🥱", "😐", "🙂", "⚡")
        private val ENERGY_LABELS = listOf("drained", "low", "okay", "good", "energized")
        private val ANXIETY_EMOJI = listOf("😰", "😟", "😐", "🙂", "😌")
        private val ANXIETY_LABELS = listOf("very anxious", "anxious", "okay", "calm", "very calm")
    }
}
