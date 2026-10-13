package io.soult.baromi.ui

import android.content.Context
import android.content.res.ColorStateList
import android.text.TextUtils
import android.util.TypedValue
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.core.view.isVisible
import androidx.recyclerview.widget.RecyclerView
import io.soult.baromi.data.Category
import io.soult.baromi.data.CategoryDefs
import io.soult.baromi.data.CheckinWithDetails
import io.soult.baromi.databinding.ItemCheckinBinding
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

class CheckinAdapter : RecyclerView.Adapter<CheckinAdapter.VH>() {

    private val items = mutableListOf<CheckinWithDetails>()

    // Category display order + names, so each row renders its axes in the configured order.
    private var categories: List<Category> = emptyList()

    /** Invoked when a row is tapped (e.g. to edit that check-in). */
    var onItemClick: ((CheckinWithDetails) -> Unit)? = null

    @SuppressWarnings("NotifyDataSetChanged")
    fun submit(newItems: List<CheckinWithDetails>, categories: List<Category>) {
        this.categories = categories
        items.clear()
        items.addAll(newItems)
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH =
        VH(ItemCheckinBinding.inflate(LayoutInflater.from(parent.context), parent, false))

    override fun onBindViewHolder(holder: VH, position: Int) {
        val item = items[position]
        holder.bind(item, categories)
        holder.itemView.setOnClickListener { onItemClick?.invoke(item) }
    }

    override fun getItemCount(): Int = items.size

    class VH(private val binding: ItemCheckinBinding) : RecyclerView.ViewHolder(binding.root) {
        fun bind(row: CheckinWithDetails, categories: List<Category>) {
            val c = row.checkin
            binding.timeText.text = FORMATTER.format(Instant.ofEpochMilli(c.answeredTime))

            val byCategory = row.values.associate { it.categoryId to it.value }
            val axesRow = binding.axesRow
            axesRow.removeAllViews()
            categories.forEach { category ->
                byCategory[category.id]?.let { value -> axesRow.addView(axisCell(axesRow.context, category, value)) }
            }
            binding.axesScroll.scrollTo(0, 0) // a recycled row could keep a prior scroll-X

            binding.commentText.textOrGone(c.comment?.takeUnless { it.isBlank() })
            binding.tagsText.textOrGone(row.tags.map { it.label }.sorted().joinToString("  ·  "))
        }

        // One axis: the recorded affect face (built-in categories) or its number (custom categories),
        // with the category name beneath. The name decides the column width, held between
        // LABEL_MIN_WIDTH_DP and LABEL_MAX_WIDTH_DP; a longer name wraps to 2 lines, then ends in "…".
        // The full name stays in the text (and so for screen readers).
        private fun axisCell(context: Context, category: Category, value: Int): View {
            val cell = LinearLayout(context).apply {
                orientation = LinearLayout.VERTICAL
                gravity = Gravity.CENTER_HORIZONTAL
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                ).apply { marginEnd = dp(context, 8) }
            }
            val icons = CategoryDefs.iconsFor(category)
            val labels = CategoryDefs.labelsFor(category)
            val size = dp(context, 32)
            if (icons != null) {
                cell.addView(
                    ImageView(context).apply {
                        setImageResource(icons[value - 1])
                        layoutParams = LinearLayout.LayoutParams(size, size)
                        imageTintList = ColorStateList.valueOf(themeColor(context, androidx.appcompat.R.attr.colorPrimary))
                        contentDescription = "${category.name}: ${labels[value - 1]}"
                    },
                )
            } else {
                cell.addView(
                    TextView(context).apply {
                        text = value.toString()
                        gravity = Gravity.CENTER
                        setTextAppearance(com.google.android.material.R.style.TextAppearance_Material3_TitleMedium)
                        layoutParams = LinearLayout.LayoutParams(size, size)
                        contentDescription = "${category.name}: $value"
                    },
                )
            }
            cell.addView(
                TextView(context).apply {
                    text = category.name
                    gravity = Gravity.CENTER_HORIZONTAL
                    setTextAppearance(com.google.android.material.R.style.TextAppearance_Material3_LabelSmall)
                    setTextColor(themeColor(context, com.google.android.material.R.attr.colorOnSurfaceVariant))
                    minWidth = dp(context, LABEL_MIN_WIDTH_DP)
                    maxWidth = dp(context, LABEL_MAX_WIDTH_DP)
                    maxLines = LABEL_MAX_LINES
                    ellipsize = TextUtils.TruncateAt.END
                    // Without explicit params a vertical LinearLayout gives children MATCH_PARENT width,
                    // which clipped every name to the icon's width ("Energ", "Anxie").
                    layoutParams = LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.WRAP_CONTENT,
                        LinearLayout.LayoutParams.WRAP_CONTENT,
                    )
                },
            )
            return cell
        }

        private fun dp(context: Context, value: Int): Int =
            (value * context.resources.displayMetrics.density).toInt()

        private fun themeColor(context: Context, attr: Int): Int {
            val tv = TypedValue()
            context.theme.resolveAttribute(attr, tv, true)
            return if (tv.resourceId != 0) ContextCompat.getColor(context, tv.resourceId) else tv.data
        }

        private companion object {
            const val LABEL_MIN_WIDTH_DP = 56
            const val LABEL_MAX_WIDTH_DP = 88
            const val LABEL_MAX_LINES = 2
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
