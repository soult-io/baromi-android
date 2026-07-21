package io.soult.baromi.ui

import android.view.LayoutInflater
import android.view.ViewGroup
import android.widget.TextView
import androidx.core.view.isVisible
import androidx.recyclerview.widget.RecyclerView
import io.soult.baromi.data.Category
import io.soult.baromi.data.CheckinWithDetails
import io.soult.baromi.databinding.ItemCheckinBinding
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

class CheckinAdapter : RecyclerView.Adapter<CheckinAdapter.VH>() {

    private val items = mutableListOf<CheckinWithDetails>()

    // Category display order + names, so each row renders its values as "Name value" in order.
    private var categories: List<Category> = emptyList()

    @SuppressWarnings("NotifyDataSetChanged")
    fun submit(newItems: List<CheckinWithDetails>, categories: List<Category>) {
        this.categories = categories
        items.clear()
        items.addAll(newItems)
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH =
        VH(ItemCheckinBinding.inflate(LayoutInflater.from(parent.context), parent, false))

    override fun onBindViewHolder(holder: VH, position: Int) = holder.bind(items[position], categories)

    override fun getItemCount(): Int = items.size

    class VH(private val binding: ItemCheckinBinding) : RecyclerView.ViewHolder(binding.root) {
        fun bind(row: CheckinWithDetails, categories: List<Category>) {
            val c = row.checkin
            binding.timeText.text = FORMATTER.format(Instant.ofEpochMilli(c.answeredTime))

            val byCategory = row.values.associate { it.categoryId to it.value }
            val axes = categories
                .mapNotNull { cat -> byCategory[cat.id]?.let { "${cat.name} $it" } }
                .joinToString("  ·  ")
            binding.axesText.textOrGone(axes)
            binding.commentText.textOrGone(c.comment?.takeUnless { it.isBlank() })
            binding.tagsText.textOrGone(row.tags.map { it.label }.sorted().joinToString("  ·  "))
        }

        private companion object {
            val FORMATTER: DateTimeFormatter =
                DateTimeFormatter.ofPattern("EEE, MMM d  ·  HH:mm", Locale.getDefault()).withZone(ZoneId.systemDefault())
        }
    }
}

/** Sets the text and shows the view, or hides it (GONE) when the text is null/empty. */
private fun TextView.textOrGone(value: String?) {
    isVisible = !value.isNullOrEmpty()
    text = value
}
