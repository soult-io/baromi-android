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
import io.soult.baromi.data.Category
import io.soult.baromi.data.CategoryDefs
import io.soult.baromi.data.CategoryRepository
import io.soult.baromi.data.TagRepository
import io.soult.baromi.databinding.FragmentCaptureBinding
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * The check-in screen (Check-in tab). One row per enabled category — five emoji (built-in faces or
 * number keycaps) mapped to the 1..5 scale. Save unlocks once every enabled category has a value.
 */
class CaptureFragment : Fragment() {

    private var binding: FragmentCaptureBinding? = null

    private val db by lazy { BaromiDatabase.getInstance(requireContext().applicationContext) }
    private val recorder by lazy { CheckinRecorder(db) }
    private val tagRepository by lazy { TagRepository(db.tagDao()) }
    private val categoryRepository by lazy { CategoryRepository(db.categoryDao()) }

    private var promptTime: Long? = null

    private var categories: List<Category> = emptyList()
    private val selected = mutableMapOf<Long, Int>()
    private var loaded = false

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
        updateSaveEnabled()
        loadCategoriesAndTags()
        b.buttonSave.setOnClickListener { save() }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        binding = null
    }

    private fun loadCategoriesAndTags() {
        viewLifecycleOwner.lifecycleScope.launch {
            categoryRepository.seedDefaultsIfEmpty()
            tagRepository.seedDefaultsIfEmpty()
            val cats = categoryRepository.enabled()
            val tags = tagRepository.enabled()
            val b = binding ?: return@launch
            categories = cats
            cats.forEach { addCategoryRow(b.categoriesContainer, it) }
            tags.forEach { tag ->
                b.chipGroupTags.addView(
                    Chip(requireContext()).apply {
                        text = tag.label
                        isCheckable = true
                        this.tag = tag.id
                    },
                )
            }
            loaded = true
            updateSaveEnabled()
        }
    }

    private fun addCategoryRow(container: LinearLayout, category: Category) {
        val emojis = CategoryDefs.emojisFor(category)
        val labels = CategoryDefs.labelsFor(category)

        val caption = TextView(requireContext()).apply {
            text = category.name
            setTextAppearance(com.google.android.material.R.style.TextAppearance_Material3_TitleMedium)
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT,
            ).apply { topMargin = dp(8) }
        }
        val group = MaterialButtonToggleGroup(requireContext()).apply {
            isSingleSelection = true
            isSelectionRequired = false
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT,
            ).apply { topMargin = dp(4); bottomMargin = dp(8) }
        }
        emojis.forEachIndexed { index, emoji -> group.addView(emojiButton(emoji, index + 1, labels[index])) }
        group.addOnButtonCheckedListener { checkedGroup, _, _ ->
            val checkedId = checkedGroup.checkedButtonId
            val value = if (checkedId == View.NO_ID) null else checkedGroup.findViewById<View>(checkedId)?.tag as? Int
            if (value == null) selected.remove(category.id) else selected[category.id] = value
            caption.text = if (value == null) category.name else getString(R.string.axis_caption_format, category.name, labels[value - 1])
            updateSaveEnabled()
        }
        container.addView(caption)
        container.addView(group)
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
        binding?.buttonSave?.isEnabled = loaded && categories.all { selected.containsKey(it.id) }
    }

    private fun save() {
        if (!loaded || categories.any { !selected.containsKey(it.id) }) return
        val b = binding ?: return
        b.buttonSave.isEnabled = false

        val values = selected.toMap()
        val tagIds = b.chipGroupTags.checkedChipIds.mapNotNull { chipId ->
            b.chipGroupTags.findViewById<Chip>(chipId)?.tag as? Long
        }
        val comment = b.inputComment.text?.toString()

        viewLifecycleOwner.lifecycleScope.launch {
            try {
                withContext(NonCancellable) {
                    recorder.record(
                        values = values,
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

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()

    companion object {
        private const val ARG_PROMPT_TIME = "prompt_time"

        fun newInstance(promptTime: Long?): CaptureFragment =
            CaptureFragment().apply {
                arguments = Bundle().apply { if (promptTime != null) putLong(ARG_PROMPT_TIME, promptTime) }
            }
    }
}
