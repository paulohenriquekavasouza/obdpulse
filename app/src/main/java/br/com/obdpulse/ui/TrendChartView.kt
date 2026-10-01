package br.com.obdpulse.ui

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.util.AttributeSet
import android.view.View
import br.com.obdpulse.obd.Format

class TrendChartView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyle: Int = 0,
) : View(context, attrs, defStyle) {

    private var values: List<Double> = emptyList()
    private var firstLabel = ""
    private var lastLabel = ""
    private var emptyText = ""

    private val density = resources.displayMetrics.density
    private val path = Path()

    private val gridPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0x33FFFFFF
        strokeWidth = 1f
    }
    private val linePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = ACCENT
        style = Paint.Style.STROKE
        strokeWidth = 2.5f * density
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
    }
    private val dotPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = ACCENT
        style = Paint.Style.FILL
    }
    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = MUTED
        textSize = 11f * density
    }

    fun setData(values: List<Double>, firstLabel: String, lastLabel: String, emptyText: String) {
        this.values = values
        this.firstLabel = firstLabel
        this.lastLabel = lastLabel
        this.emptyText = emptyText
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val w = width.toFloat()
        val h = height.toFloat()
        val bottom = h - LABEL_AREA_DP * density
        val top = TOP_PAD_DP * density

        for (i in 0..3) {
            val y = top + (bottom - top) * i / 3f
            canvas.drawLine(0f, y, w, y, gridPaint)
        }

        if (values.isEmpty()) {
            textPaint.textAlign = Paint.Align.CENTER
            canvas.drawText(emptyText, w / 2f, (top + bottom) / 2f, textPaint)
            return
        }

        var min = values.min()
        var max = values.max()
        if (max - min < 1e-6) {
            min -= 1.0
            max += 1.0
        }
        val span = max - min
        val inset = DOT_RADIUS_DP * density + 2f * density
        fun x(index: Int): Float =
            if (values.size == 1) w / 2f else inset + (w - 2 * inset) * index / (values.size - 1)
        fun y(value: Double): Float = bottom - (bottom - top) * ((value - min) / span).toFloat()

        path.reset()
        path.moveTo(x(0), y(values[0]))
        for (i in 1 until values.size) path.lineTo(x(i), y(values[i]))
        if (values.size > 1) canvas.drawPath(path, linePaint)
        for (i in values.indices) canvas.drawCircle(x(i), y(values[i]), DOT_RADIUS_DP * density, dotPaint)

        textPaint.textAlign = Paint.Align.LEFT
        canvas.drawText(Format.number(max, 1), 4f * density, top + textPaint.textSize, textPaint)
        canvas.drawText(Format.number(min, 1), 4f * density, bottom - 4f * density, textPaint)

        val labelY = h - 4f * density
        canvas.drawText(firstLabel, 0f, labelY, textPaint)
        textPaint.textAlign = Paint.Align.RIGHT
        canvas.drawText(lastLabel, w, labelY, textPaint)
    }

    private companion object {
        const val ACCENT = 0xFFEF5350.toInt()
        const val MUTED = 0xFF9AA0A6.toInt()
        const val LABEL_AREA_DP = 22f
        const val TOP_PAD_DP = 8f
        const val DOT_RADIUS_DP = 3.5f
    }
}
