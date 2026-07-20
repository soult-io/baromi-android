package io.soult.baromi.ui

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import io.soult.baromi.R
import io.soult.baromi.data.CheckinWithTags
import io.soult.baromi.databinding.ItemCheckinBinding
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

class CheckinAdapter : RecyclerView.Adapter<CheckinAdapter.VH>() {

    private val items = mutableListOf<CheckinWithTags>()

    @SuppressWarnings("NotifyDataSetChanged")
    fun submit(newItems: List<CheckinWithTags>) {
        items.clear()
        items.addAll(newItems)
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH =
        VH(ItemCheckinBinding.inflate(LayoutInflater.from(parent.context), parent, false))

    override fun onBindViewHolder(holder: VH, position: Int) = holder.bind(items[position])

    override fun getItemCount(): Int = items.size

    class VH(private val binding: ItemCheckinBinding) : RecyclerView.ViewHolder(binding.root) {
        fun bind(row: CheckinWithTags) {
            val c = row.checkin
            val ctx = binding.root.context
            binding.timeText.text = FORMATTER.format(Instant.ofEpochMilli(c.answeredTime))
            binding.axesText.text = ctx.getString(R.string.checkin_axes_format, c.mood, c.energy, c.anxiety)

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
