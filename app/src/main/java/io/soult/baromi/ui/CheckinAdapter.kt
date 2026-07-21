package io.soult.baromi.ui

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
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
            if (axes.isEmpty()) {
                binding.axesText.visibility = View.GONE
            } else {
                binding.axesText.visibility = View.VISIBLE
                binding.axesText.text = axes
            }

            val comment = c.comment
            if (comment.isNullOrBlank()) {
                binding.commentText.visibility = View.GONE
            } else {
                binding.commentText.visibility = View.VISIBLE
                binding.commentText.text = comment
            }

            val tags = row.tags.map { it.label }.sorted()
            if (tags.isEmpty()) {
                binding.tagsText.visibility = View.GONE
            } else {
                binding.tagsText.visibility = View.VISIBLE
                binding.tagsText.text = tags.joinToString("  ·  ")
            }
        }

        private companion object {
            val FORMATTER: DateTimeFormatter =
                DateTimeFormatter.ofPattern("EEE, MMM d  ·  HH:mm", Locale.getDefault()).withZone(ZoneId.systemDefault())
        }
    }
}
