package app.claudelimit.widget

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import app.claudelimit.Pal
import app.claudelimit.levelColor
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sin

/** One thing a widget shows: a limit, or the weekly plan. */
class DrawItem(
    val title: String,
    val value: String,
    /** How full the shape is, 0..1. */
    val fraction: Float,
    /** 0 fine, 1 careful, 2 nearly out. */
    val level: Int,
    /** Where you should be by now, 0..1, or null. */
    val marker: Float?,
    val sub: String,
    /** How many cells the "cells" style splits this item into (10 for the 5-hour window, 7 for the week). */
    val segs: Int = 10,
)

/**
 * Draws a whole widget as one bitmap at its real size, so every style can adapt to the room it gets.
 * The six styles are inspired by the style picker of Limits for Claude (MIT), redrawn here.
 */
object WidgetDraw {

    fun render(
        wDp: Float,
        hDp: Float,
        density: Float,
        items: List<DrawItem>,
        style: String,
        pal: Pal,
        bgPercent: Int,
        header: String,
        headerRight: String,
        rtl: Boolean,
        message: String? = null,
    ): Bitmap {
        // Very large widgets are drawn smaller and stretched back, to keep the bitmap light.
        val scale = min(1f, 1000f / max(wDp * density, hDp * density))
        val d = density * scale
        val w = max(2, (wDp * d).roundToInt())
        val h = max(2, (hDp * d).roundToInt())
        val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val p = Painter(Canvas(bmp), d, pal, rtl)
        p.background(w.toFloat(), h.toFloat(), bgPercent)

        val pad = if (min(wDp, hDp) < 100f) 7f else 12f
        var top = pad
        val hasHeader = message == null && hDp >= 120f && (header.isNotEmpty() || headerRight.isNotEmpty())
        if (hasHeader) {
            p.header(header, headerRight, pad, wDp)
            top += 20f
        }
        val region = RectF(pad * d, top * d, (wDp - pad) * d, (hDp - pad) * d)
        if (message != null) {
            p.message(message, region)
            return bmp
        }
        when (style) {
            "BARS" -> p.rows(items, region, segmented = false)
            "CELLS" -> p.rows(items, region, segmented = true)
            else -> p.glyphs(items, region, style)
        }
        return bmp
    }

    private class Painter(val c: Canvas, val d: Float, val pal: Pal, val rtl: Boolean) {
        private val fill = Paint(Paint.ANTI_ALIAS_FLAG)
        private val tp = TextPaint(Paint.ANTI_ALIAS_FLAG)
        private val sans = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
        private val sansBold = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        private val monoBold = Typeface.create(Typeface.MONOSPACE, Typeface.BOLD)

        fun dp(v: Float) = v * d

        fun background(w: Float, h: Float, percent: Int) {
            val a = percent.coerceIn(0, 100) * 255 / 100
            fill.style = Paint.Style.FILL
            fill.color = (a shl 24) or (pal.bg and 0xFFFFFF)
            c.drawRoundRect(RectF(0f, 0f, w, h), dp(22f), dp(22f), fill)
        }

        /** Draws one line of text and shrinks it until it fits in [maxW] (pixels). */
        fun text(
            s: String, x: Float, baseline: Float, sizeDp: Float, color: Int,
            bold: Boolean = false, mono: Boolean = false,
            align: Paint.Align = Paint.Align.CENTER, maxW: Float = Float.MAX_VALUE,
        ) {
            if (s.isEmpty()) return
            tp.reset()
            tp.isAntiAlias = true
            tp.typeface = if (mono) monoBold else if (bold) sansBold else sans
            tp.color = color
            var size = dp(sizeDp)
            tp.textSize = size
            val smallest = dp(8f)
            while (tp.measureText(s) > maxW && size > smallest) {
                size -= dp(0.5f)
                tp.textSize = size
            }
            tp.textAlign = align
            c.drawText(s, x, baseline, tp)
        }

        fun header(left: String, right: String, pad: Float, wDp: Float) {
            val y = dp(pad + 11f)
            val maxW = dp(wDp - 2 * pad) * 0.5f
            val startX = if (rtl) dp(wDp - pad) else dp(pad)
            val endX = if (rtl) dp(pad) else dp(wDp - pad)
            val startAlign = if (rtl) Paint.Align.RIGHT else Paint.Align.LEFT
            val endAlign = if (rtl) Paint.Align.LEFT else Paint.Align.RIGHT
            text(left, startX, y, 12f, pal.ink, bold = true, align = startAlign, maxW = maxW)
            text(if (right.isEmpty()) "↻" else "$right  ↻", endX, y, 11f, pal.sub, align = endAlign, maxW = maxW)
        }

        fun message(msg: String, r: RectF) {
            val t = TextPaint(Paint.ANTI_ALIAS_FLAG)
            t.color = pal.ink
            t.textSize = dp(13f)
            t.typeface = sansBold
            val width = max(1, r.width().toInt())
            val layout = StaticLayout.Builder.obtain(msg, 0, msg.length, t, width)
                .setAlignment(Layout.Alignment.ALIGN_CENTER)
                .build()
            c.save()
            c.translate(r.left, r.centerY() - layout.height / 2f)
            layout.draw(c)
            c.restore()
        }

        // ---------------- rows: bars and cells ----------------

        fun rows(items: List<DrawItem>, r: RectF, segmented: Boolean) {
            val n = items.size
            if (n == 0) return
            val rowH = r.height() / n
            val rowDp = rowH / d
            val textDp = (rowDp * 0.30f).coerceIn(9f, 14f)
            val valueDp = (textDp * 1.25f).coerceAtMost(18f)
            val barDp = (rowDp * 0.22f).coerceIn(5f, 12f)
            val blockDp = valueDp * 1.15f + 3f + barDp
            val startX = if (rtl) r.right else r.left
            val endX = if (rtl) r.left else r.right
            val startAlign = if (rtl) Paint.Align.RIGHT else Paint.Align.LEFT
            val endAlign = if (rtl) Paint.Align.LEFT else Paint.Align.RIGHT
            items.forEachIndexed { i, it ->
                val y0 = r.top + rowH * i + (rowH - dp(blockDp)) / 2f
                val baseline = y0 + dp(valueDp)
                val color = pal.levelColor(it.level)
                val showSub = rowDp >= 46f && it.sub.isNotEmpty()
                val title = if (showSub) it.title + " · " + it.sub else it.title
                text(it.value, endX, baseline, valueDp, color, bold = true, mono = true, align = endAlign)
                text(title, startX, baseline, textDp, pal.sub, align = startAlign, maxW = r.width() * 0.62f)
                val barTop = y0 + dp(valueDp * 1.15f + 3f)
                val barH = dp(barDp)
                if (segmented) segments(it, r.left, r.right, barTop, barH, color)
                else bar(it, r.left, r.right, barTop, barH, color)
            }
        }

        private fun bar(it: DrawItem, left: Float, right: Float, top: Float, h: Float, color: Int) {
            fill.style = Paint.Style.FILL
            fill.color = pal.track
            c.drawRoundRect(RectF(left, top, right, top + h), h / 2f, h / 2f, fill)
            val f = it.fraction.coerceIn(0f, 1f)
            if (f > 0.005f) {
                fill.color = color
                c.drawRoundRect(RectF(left, top, max(left + (right - left) * f, left + h), top + h), h / 2f, h / 2f, fill)
            }
            val m = it.marker
            if (m != null) {
                val x = left + (right - left) * m.coerceIn(0f, 1f)
                tick(x, top - dp(3f), top + h + dp(3f))
            }
        }

        private fun segments(it: DrawItem, left: Float, right: Float, top: Float, h: Float, color: Int) {
            val n = it.segs
            val gap = dp(if (right - left < dp(120f)) 1.5f else 3f)
            val segW = ((right - left) - gap * (n - 1)) / n
            val f = it.fraction.coerceIn(0f, 1f)
            val rad = min(dp(3f), h / 2f)
            for (i in 0 until n) {
                val x = left + i * (segW + gap)
                fill.style = Paint.Style.FILL
                fill.color = pal.track
                c.drawRoundRect(RectF(x, top, x + segW, top + h), rad, rad, fill)
                val part = (f * n - i).coerceIn(0f, 1f)
                if (part > 0.03f) {
                    fill.color = color
                    c.drawRoundRect(RectF(x, top, x + max(segW * part, rad * 2f), top + h), rad, rad, fill)
                }
            }
            val m = it.marker
            if (m != null) {
                val x = left + m.coerceIn(0f, 1f) * n * (segW + gap) - gap / 2f
                tick(x.coerceIn(left, right), top - dp(3f), top + h + dp(3f))
            }
        }

        private fun tick(x: Float, y1: Float, y2: Float) {
            fill.style = Paint.Style.STROKE
            fill.strokeCap = Paint.Cap.ROUND
            fill.strokeWidth = dp(2f)
            fill.color = pal.marker
            c.drawLine(x, y1, x, y2, fill)
            fill.style = Paint.Style.FILL
        }

        // ---------------- glyphs: rings, batteries, columns, gauges ----------------

        fun glyphs(items: List<DrawItem>, r: RectF, style: String) {
            val n = items.size
            if (n == 0) return
            val cols = if (n == 1) 1 else if (r.width() / n >= r.height() * 0.5f) n else 1
            val rowsN = (n + cols - 1) / cols
            val cw = r.width() / cols
            val ch = r.height() / rowsN
            items.forEachIndexed { i, it ->
                val col0 = i % cols
                val col = if (rtl) cols - 1 - col0 else col0
                val row = i / cols
                val cell = RectF(r.left + cw * col, r.top + ch * row, r.left + cw * (col + 1), r.top + ch * (row + 1))
                glyphCell(it, cell, style)
            }
        }

        private fun glyphCell(it: DrawItem, cell: RectF, style: String) {
            val cellH = cell.height() / d
            val showTitle = cellH >= 62f
            val showSub = cellH >= 100f && it.sub.isNotEmpty()
            val maxW = cell.width() - dp(4f)
            if (showTitle) text(it.title, cell.centerX(), cell.top + dp(11f), 11f, pal.sub, maxW = maxW)
            if (showSub) text(it.sub, cell.centerX(), cell.bottom - dp(3f), 10f, pal.sub, maxW = maxW)
            val g = RectF(
                cell.left + dp(2f),
                cell.top + dp(if (showTitle) 15f else 2f),
                cell.right - dp(2f),
                cell.bottom - dp(if (showSub) 16f else 2f),
            )
            when (style) {
                "BATTERIES" -> battery(it, g)
                "COLUMNS" -> column(it, g)
                "GAUGES" -> gauge(it, g)
                else -> ring(it, g)
            }
        }

        private fun ring(it: DrawItem, g: RectF) {
            val s = min(g.width(), g.height())
            if (s < dp(20f)) return
            val cx = g.centerX()
            val cy = g.centerY()
            val stroke = s * 0.14f
            val oval = RectF(cx - s / 2 + stroke / 2, cy - s / 2 + stroke / 2, cx + s / 2 - stroke / 2, cy + s / 2 - stroke / 2)
            fill.style = Paint.Style.STROKE
            fill.strokeWidth = stroke
            fill.strokeCap = Paint.Cap.ROUND
            fill.color = pal.track
            c.drawArc(oval, 0f, 360f, false, fill)
            val f = it.fraction.coerceIn(0f, 1f)
            if (f > 0.005f) {
                fill.color = pal.levelColor(it.level)
                c.drawArc(oval, -90f, 360f * f, false, fill)
            }
            val m = it.marker
            if (m != null) {
                val a = (-90.0 + 360.0 * m.coerceIn(0f, 1f)) * PI / 180.0
                val r0 = (s - stroke) / 2f
                radialTick(cx, cy, a, r0 - stroke * 0.75f, r0 + stroke * 0.75f)
            }
            fill.style = Paint.Style.FILL
            text(it.value, cx, cy + s * 0.09f, (s / d) * 0.27f, pal.ink, bold = true, mono = true, maxW = s - stroke * 2.4f)
        }

        private fun radialTick(cx: Float, cy: Float, a: Double, r1: Float, r2: Float) {
            fill.style = Paint.Style.STROKE
            fill.strokeCap = Paint.Cap.ROUND
            fill.strokeWidth = dp(2f)
            fill.color = pal.marker
            c.drawLine(
                cx + cos(a).toFloat() * r1, cy + sin(a).toFloat() * r1,
                cx + cos(a).toFloat() * r2, cy + sin(a).toFloat() * r2,
                fill,
            )
            fill.style = Paint.Style.FILL
        }

        private fun battery(it: DrawItem, g: RectF) {
            val totalH = min(g.height(), g.width() * 1.8f)
            if (totalH < dp(26f)) return
            val bodyW = totalH * 0.55f
            val capH = totalH * 0.08f
            val cx = g.centerX()
            val top = g.centerY() - totalH / 2f + capH
            val bodyH = totalH - capH
            val left = cx - bodyW / 2f
            fill.style = Paint.Style.FILL
            fill.color = pal.track
            c.drawRoundRect(RectF(cx - bodyW * 0.22f, top - capH, cx + bodyW * 0.22f, top + dp(1f)), dp(2f), dp(2f), fill)
            c.drawRoundRect(RectF(left, top, left + bodyW, top + bodyH), bodyW * 0.22f, bodyW * 0.22f, fill)
            val pad = bodyW * 0.1f
            val inLeft = left + pad
            val inRight = left + bodyW - pad
            val inTop = top + pad
            val inBottom = top + bodyH - pad
            val innerH = inBottom - inTop
            val f = it.fraction.coerceIn(0f, 1f)
            if (f > 0.005f) {
                fill.color = pal.levelColor(it.level)
                val fh = max(innerH * f, dp(4f))
                c.drawRoundRect(RectF(inLeft, inBottom - fh, inRight, inBottom), bodyW * 0.14f, bodyW * 0.14f, fill)
            }
            val m = it.marker
            if (m != null) {
                val y = inBottom - innerH * m.coerceIn(0f, 1f)
                tickH(left - dp(3f), left + bodyW + dp(3f), y)
            }
            val onFilled = f >= 0.5f
            text(
                it.value, cx, top + bodyH / 2f + (bodyW / d) * 0.12f * d, (bodyW / d) * 0.34f,
                if (onFilled) pal.onFill else pal.ink, bold = true, mono = true, maxW = bodyW - pad * 2f,
            )
        }

        private fun tickH(x1: Float, x2: Float, y: Float) {
            fill.style = Paint.Style.STROKE
            fill.strokeCap = Paint.Cap.ROUND
            fill.strokeWidth = dp(2f)
            fill.color = pal.marker
            c.drawLine(x1, y, x2, y, fill)
            fill.style = Paint.Style.FILL
        }

        private fun column(it: DrawItem, g: RectF) {
            val valDp = min(15f, (g.width() / d) * 0.3f).coerceAtLeast(10f)
            val colTop = g.top + dp(valDp + 5f)
            val colH = g.bottom - colTop
            if (colH < dp(18f)) return
            val w = max(dp(14f), min(g.width() * 0.5f, colH * 0.55f))
            val cx = g.centerX()
            text(it.value, cx, g.top + dp(valDp), valDp, pal.ink, bold = true, mono = true, maxW = g.width())
            val rad = w * 0.35f
            fill.style = Paint.Style.FILL
            fill.color = pal.track
            c.drawRoundRect(RectF(cx - w / 2, colTop, cx + w / 2, g.bottom), rad, rad, fill)
            val f = it.fraction.coerceIn(0f, 1f)
            if (f > 0.005f) {
                fill.color = pal.levelColor(it.level)
                val fh = max(colH * f, rad * 1.6f)
                c.drawRoundRect(RectF(cx - w / 2, g.bottom - fh, cx + w / 2, g.bottom), rad, rad, fill)
            }
            val m = it.marker
            if (m != null) {
                val y = g.bottom - colH * m.coerceIn(0f, 1f)
                tickH(cx - w / 2 - dp(3f), cx + w / 2 + dp(3f), y)
            }
        }

        private fun gauge(it: DrawItem, g: RectF) {
            val outer = min(g.width() / 2f, g.height() * 0.95f)
            if (outer < dp(18f)) return
            val stroke = outer * 0.22f
            val r = outer - stroke / 2f
            val cx = g.centerX()
            val cy = g.centerY() + outer / 2f
            val oval = RectF(cx - r, cy - r, cx + r, cy + r)
            fill.style = Paint.Style.STROKE
            fill.strokeWidth = stroke
            fill.strokeCap = Paint.Cap.ROUND
            fill.color = pal.track
            c.drawArc(oval, 180f, 180f, false, fill)
            val f = it.fraction.coerceIn(0f, 1f)
            if (f > 0.005f) {
                fill.color = pal.levelColor(it.level)
                c.drawArc(oval, 180f, 180f * f, false, fill)
            }
            val m = it.marker
            if (m != null) {
                val a = (180.0 + 180.0 * m.coerceIn(0f, 1f)) * PI / 180.0
                radialTick(cx, cy, a, r - stroke * 0.75f, r + stroke * 0.75f)
            }
            fill.style = Paint.Style.FILL
            text(it.value, cx, cy - r * 0.12f, (r / d) * 0.55f, pal.ink, bold = true, mono = true, maxW = 2f * r - stroke * 2f)
        }
    }
}
