package br.com.obdpulse.media

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import androidx.core.graphics.createBitmap

class GaugeRenderer {

    private val fill = Paint(Paint.ANTI_ALIAS_FLAG)
    private val arc = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
        strokeWidth = STROKE
    }
    private val label = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = MUTED
        textAlign = Paint.Align.CENTER
        typeface = Typeface.DEFAULT_BOLD
    }
    private val value = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = WHITE
        textAlign = Paint.Align.CENTER
        typeface = Typeface.DEFAULT_BOLD
    }

    fun render(data: GaugeData): Bitmap {
        val bitmap = createBitmap(SIZE, SIZE)
        val canvas = Canvas(bitmap)
        fill.color = BACKGROUND
        fill.style = Paint.Style.FILL
        canvas.drawRect(0f, 0f, SIZE.toFloat(), SIZE.toFloat(), fill)

        drawArc(canvas, data)
        drawGrid(canvas, data)
        return bitmap
    }

    private fun drawArc(canvas: Canvas, data: GaugeData) {
        val rect = RectF(CX - RADIUS, ARC_CY - RADIUS, CX + RADIUS, ARC_CY + RADIUS)
        arc.color = TRACK
        canvas.drawArc(rect, START_ANGLE, SWEEP, false, arc)

        val boost = data.boost
        if (boost != null) {
            val fraction = ((boost - MIN_BAR) / (MAX_BAR - MIN_BAR)).toFloat().coerceIn(0f, 1f)
            arc.color = zoneColor(boost)
            canvas.drawArc(rect, START_ANGLE, SWEEP * fraction, false, arc)

            val max = data.boostMax
            if (max != null) {
                val markFraction = ((max - MIN_BAR) / (MAX_BAR - MIN_BAR)).toFloat().coerceIn(0f, 1f)
                val angle = Math.toRadians((START_ANGLE + SWEEP * markFraction).toDouble())
                fill.color = WHITE
                fill.style = Paint.Style.FILL
                val outer = RADIUS + STROKE / 2f
                val inner = RADIUS - STROKE / 2f
                canvas.drawCircle(
                    CX + (outer + inner) / 2f * Math.cos(angle).toFloat(),
                    ARC_CY + (outer + inner) / 2f * Math.sin(angle).toFloat(),
                    5f,
                    fill,
                )
            }
        }

        label.textSize = 30f
        canvas.drawText("TURBO", CX, ARC_CY - 46f, label)
        value.color = if (boost != null) zoneColor(boost) else MUTED
        value.textSize = 92f
        canvas.drawText(data.boostText, CX, ARC_CY + 30f, value)
        value.color = WHITE
        label.textSize = 28f
        canvas.drawText("bar", CX, ARC_CY + 72f, label)
    }

    private fun drawGrid(canvas: Canvas, data: GaugeData) {
        cell(canvas, LEFT, ROW1, "km/L", data.kmpl, hero = true)
        cell(canvas, RIGHT, ROW1, "VELOCIDADE", data.speed)
        cell(canvas, LEFT, ROW2, "ROTAÇÃO", data.rpm)
        cell(canvas, RIGHT, ROW2, "MOTOR", data.coolant)
    }

    private fun cell(canvas: Canvas, x: Float, y: Float, name: String, text: String, hero: Boolean = false) {
        label.textSize = 26f
        canvas.drawText(name, x, y, label)
        value.textSize = if (hero) 60f else 44f
        value.color = if (hero) ACCENT else WHITE
        canvas.drawText(text, x, y + if (hero) 62f else 50f, value)
        value.color = WHITE
    }

    private fun zoneColor(boost: Double): Int = when {
        boost < 0.0 -> BLUE
        boost < 1.0 -> GREEN
        boost < 1.5 -> AMBER
        else -> RED
    }

    private companion object {
        const val SIZE = 512
        const val CX = 256f
        const val ARC_CY = 196f
        const val RADIUS = 150f
        const val STROKE = 26f
        const val START_ANGLE = 135f
        const val SWEEP = 270f
        const val MIN_BAR = -1.0
        const val MAX_BAR = 2.0
        const val LEFT = 148f
        const val RIGHT = 364f
        const val ROW1 = 372f
        const val ROW2 = 452f

        const val BACKGROUND = 0xFF0E0E10.toInt()
        const val TRACK = 0xFF2A2A2E.toInt()
        const val WHITE = 0xFFFFFFFF.toInt()
        const val MUTED = 0xFF9AA0A6.toInt()
        const val ACCENT = 0xFFEF5350.toInt()
        const val BLUE = 0xFF64B5F6.toInt()
        const val GREEN = 0xFF66BB6A.toInt()
        const val AMBER = 0xFFFFCA28.toInt()
        const val RED = 0xFFEF5350.toInt()
    }
}
