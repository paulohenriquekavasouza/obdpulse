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

        val speed = data.speed
        if (speed != null) {
            val fraction = (speed / GaugeData.MAX_SPEED).toFloat().coerceIn(0f, 1f)
            arc.color = zoneColor(speed)
            canvas.drawArc(rect, START_ANGLE, SWEEP * fraction, false, arc)
        }

        label.textSize = 26f
        canvas.drawText("VELOCIDADE", CX, ARC_CY - 40f, label)
        value.color = if (speed != null) zoneColor(speed) else MUTED
        value.textSize = 74f
        canvas.drawText(data.speedText, CX, ARC_CY + 22f, value)
        value.color = WHITE
        label.textSize = 24f
        canvas.drawText("km/h", CX, ARC_CY + 58f, label)
    }

    private fun drawGrid(canvas: Canvas, data: GaugeData) {
        cell(canvas, LEFT, ROW1, "km/L", data.kmpl, hero = true)
        cell(canvas, RIGHT, ROW1, "MOTOR", data.coolant)
        cell(canvas, LEFT, ROW2, "ROTAÇÃO", data.rpm)
        cell(canvas, RIGHT, ROW2, "ÚLT. 0–100", data.lastZeroTo100)
    }

    private fun cell(canvas: Canvas, x: Float, y: Float, name: String, text: String, hero: Boolean = false) {
        label.textSize = 24f
        canvas.drawText(name, x, y, label)
        value.textSize = if (hero) 46f else 40f
        value.color = if (hero) ACCENT else WHITE
        canvas.drawText(text, x, y + if (hero) 46f else 42f, value)
        value.color = WHITE
    }

    private fun zoneColor(speed: Double): Int = when {
        speed < 120.0 -> GREEN
        speed < 180.0 -> AMBER
        else -> RED
    }

    private companion object {
        const val SIZE = 512
        const val CX = 256f
        const val ARC_CY = 176f
        const val RADIUS = 118f
        const val STROKE = 22f
        const val START_ANGLE = 135f
        const val SWEEP = 270f
        const val LEFT = 156f
        const val RIGHT = 356f
        const val ROW1 = 336f
        const val ROW2 = 428f

        const val BACKGROUND = 0xFF0E0E10.toInt()
        const val TRACK = 0xFF2A2A2E.toInt()
        const val WHITE = 0xFFFFFFFF.toInt()
        const val MUTED = 0xFF9AA0A6.toInt()
        const val ACCENT = 0xFFEF5350.toInt()
        const val GREEN = 0xFF66BB6A.toInt()
        const val AMBER = 0xFFFFCA28.toInt()
        const val RED = 0xFFEF5350.toInt()
    }
}
