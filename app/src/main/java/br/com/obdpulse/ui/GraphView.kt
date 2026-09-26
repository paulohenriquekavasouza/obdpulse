package br.com.obdpulse.ui

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.util.AttributeSet
import android.view.View

class GraphView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyle: Int = 0,
) : View(context, attrs, defStyle) {

    private val samples = ArrayDeque<Double>()
    private val path = Path()

    private val gridPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0x33FFFFFF
        strokeWidth = 1f
    }
    private val linePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0xFFEF5350.toInt()
        style = Paint.Style.STROKE
        strokeWidth = 4f
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
    }
    private val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0x33EF5350
        style = Paint.Style.FILL
    }
    private val axisPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0xFF9AA0A6.toInt()
        textSize = 30f
    }

    fun addSample(value: Double) {
        samples.addLast(value)
        while (samples.size > MAX_SAMPLES) samples.removeFirst()
        invalidate()
    }

    fun clear() {
        samples.clear()
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val w = width.toFloat()
        val h = height.toFloat()
        for (i in 0..4) {
            val y = h * i / 4f
            canvas.drawLine(0f, y, w, y, gridPaint)
        }
        if (samples.size < 2) return

        var min = Double.MAX_VALUE
        var max = -Double.MAX_VALUE
        for (v in samples) {
            if (v < min) min = v
            if (v > max) max = v
        }
        if (max - min < 1e-6) {
            min -= 1.0
            max += 1.0
        }
        val padTop = 8f
        val padBottom = 8f
        val span = (max - min)
        fun x(index: Int) = w * index / (samples.size - 1)
        fun y(value: Double) = padTop + (h - padTop - padBottom) * (1.0 - (value - min) / span).toFloat()

        path.reset()
        val list = samples.toList()
        path.moveTo(x(0), y(list[0]))
        for (i in 1 until list.size) path.lineTo(x(i), y(list[i]))
        canvas.drawPath(path, linePaint)

        val fill = Path(path)
        fill.lineTo(x(list.size - 1), h)
        fill.lineTo(x(0), h)
        fill.close()
        canvas.drawPath(fill, fillPaint)

        canvas.drawText(fmt(max), 8f, padTop + 26f, axisPaint)
        canvas.drawText(fmt(min), 8f, h - padBottom - 4f, axisPaint)
    }

    private fun fmt(value: Double): String =
        if (value == value.toLong().toDouble()) value.toLong().toString() else String.format("%.1f", value)

    private companion object {
        const val MAX_SAMPLES = 120
    }
}
