package io.soult.baromi.ui

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View
import androidx.core.graphics.ColorUtils
import com.google.android.material.color.MaterialColors
import io.soult.baromi.stats.CalendarGrid
import io.soult.baromi.stats.HistoryStats
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.TextStyle
import java.util.Locale

/**
 * A GitHub-style month heatmap drawn on a [Canvas] (no charting dependency). Each day is tinted by
 * that day's mean value for the selected category (1..5 -> pale..strong teal); empty days stay
 * blank. Tapping a day with data reports its date via [onDayClick]. The week starts on the locale's
 * first day, so the column order follows the user's calendar convention.
 */
class CalendarHeatmapView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0,
) : View(context, attrs, defStyleAttr) {

    var onDayClick: ((LocalDate) -> Unit)? = null

    private var month: YearMonth = YearMonth.of(2000, 1)
    private var stats: Map<LocalDate, HistoryStats.DayStat> = emptyMap()
    private var today: LocalDate = LocalDate.of(2000, 1, 1)

    private val firstDayOfWeek: DayOfWeek =
        java.time.temporal.WeekFields.of(Locale.getDefault()).firstDayOfWeek

    // Pure grid geometry (leading blanks, rows, tap → date); the view only draws what it returns.
    private var grid = CalendarGrid(month, firstDayOfWeek)

    private val density = context.resources.displayMetrics.density
    private var headerHeight = 0f
    private var cellSize = 0f

    private val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val emptyPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = MaterialColors.getColor(this@CalendarHeatmapView, com.google.android.material.R.attr.colorSurfaceContainerHighest)
    }
    private val todayRing = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 2f * density
        color = MaterialColors.getColor(this@CalendarHeatmapView, androidx.appcompat.R.attr.colorPrimary)
    }
    private val dayText = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = MaterialColors.getColor(this@CalendarHeatmapView, com.google.android.material.R.attr.colorOnSurface)
        textAlign = Paint.Align.CENTER
        textSize = 13f * density
    }
    private val headerText = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = MaterialColors.getColor(this@CalendarHeatmapView, com.google.android.material.R.attr.colorOnSurfaceVariant)
        textAlign = Paint.Align.CENTER
        textSize = 12f * density
    }
    private val primary =
        MaterialColors.getColor(this, androidx.appcompat.R.attr.colorPrimary)
    private val cellRect = RectF()

    /** Show [month], tinting days from [stats] (all-history map, sliced here), with [today] ringed. */
    fun bind(month: YearMonth, stats: Map<LocalDate, HistoryStats.DayStat>, today: LocalDate) {
        this.month = month
        this.stats = stats
        this.today = today
        this.grid = CalendarGrid(month, firstDayOfWeek)
        requestLayout()
        invalidate()
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val w = MeasureSpec.getSize(widthMeasureSpec)
        cellSize = w / 7f
        headerHeight = cellSize * 0.5f
        val h = (headerHeight + grid.rowCount * cellSize).toInt()
        setMeasuredDimension(w, h)
    }

    override fun onDraw(canvas: Canvas) {
        val len = month.lengthOfMonth()

        val headerBaseline = headerHeight * 0.5f - (headerText.ascent() + headerText.descent()) / 2f
        for (col in 0 until 7) {
            val dow = firstDayOfWeek.plus(col.toLong())
            val name = dow.getDisplayName(TextStyle.NARROW, Locale.getDefault())
            canvas.drawText(name, col * cellSize + cellSize / 2f, headerBaseline, headerText)
        }

        val inset = 3f * density
        for (day in 1..len) {
            val (row, col) = grid.cellOf(day)
            val left = col * cellSize + inset
            val top = headerHeight + row * cellSize + inset
            cellRect.set(left, top, left + cellSize - 2 * inset, top + cellSize - 2 * inset)
            val radius = 6f * density

            val date = month.atDay(day)
            val stat = stats[date]
            if (stat == null) {
                canvas.drawRoundRect(cellRect, radius, radius, emptyPaint)
            } else {
                // Mean 1..5 -> alpha 0.18..1.0 of the primary teal over the surface.
                val norm = ((stat.average - 1.0) / 4.0).coerceIn(0.0, 1.0)
                val alpha = (0.18 + 0.82 * norm)
                fillPaint.color = ColorUtils.setAlphaComponent(primary, (alpha * 255).toInt())
                canvas.drawRoundRect(cellRect, radius, radius, fillPaint)
            }
            if (date == today) canvas.drawRoundRect(cellRect, radius, radius, todayRing)

            val textBaseline = top + (cellSize - 2 * inset) / 2f - (dayText.ascent() + dayText.descent()) / 2f
            canvas.drawText(day.toString(), left + (cellSize - 2 * inset) / 2f, textBaseline, dayText)
        }
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (event.action != MotionEvent.ACTION_UP) return true
        val date = grid.dateAt(event.x, event.y, cellSize, headerHeight) ?: return true
        if (stats[date] != null) {
            performClick()
            onDayClick?.invoke(date)
        }
        return true
    }

    override fun performClick(): Boolean {
        super.performClick()
        return true
    }
}
