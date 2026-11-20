package app.claudelimit.widget

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.Dp
import androidx.glance.GlanceModifier
import androidx.glance.Image
import androidx.glance.ImageProvider
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.size

/** Glance has no arc or marker shape, so rings and the pace bar are small drawn bitmaps. */
object RingImage {
    fun ring(fraction: Float, color: Int, px: Int = 240): Bitmap {
        val bmp = Bitmap.createBitmap(px, px, Bitmap.Config.ARGB_8888)
        val c = Canvas(bmp)
        val stroke = px * 0.13f
        val rect = RectF(stroke / 2, stroke / 2, px - stroke / 2, px - stroke / 2)
        val p = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = stroke
            strokeCap = Paint.Cap.ROUND
        }
        p.color = 0x33888888
        c.drawArc(rect, 0f, 360f, false, p)
        val f = fraction.coerceIn(0f, 1f)
        if (f > 0.005f) {
            p.color = color
            c.drawArc(rect, -90f, 360f * f, false, p)
        }
        return bmp
    }

    /**
     * A thin bar: the filled part is what you used, the tick is where you should be by now.
     * [wPx] and [hPx] are the real size on screen, so nothing stretches.
     */
    fun paceBar(wPx: Int, hPx: Int, usedFraction: Float, expectedFraction: Float, color: Int): Bitmap {
        val bmp = Bitmap.createBitmap(wPx, hPx, Bitmap.Config.ARGB_8888)
        val c = Canvas(bmp)
        val p = Paint(Paint.ANTI_ALIAS_FLAG)
        val barH = hPx * 0.55f
        val top = (hPx - barH) / 2f
        val r = barH / 2f
        p.color = 0x33888888
        c.drawRoundRect(RectF(0f, top, wPx.toFloat(), top + barH), r, r, p)
        val u = usedFraction.coerceIn(0f, 1f)
        if (u > 0.005f) {
            p.color = color
            c.drawRoundRect(RectF(0f, top, maxOf(wPx * u, barH), top + barH), r, r, p)
        }
        val x = (wPx * expectedFraction.coerceIn(0f, 1f)).coerceIn(3f, wPx - 3f)
        val tick = (hPx * 0.09f).coerceAtLeast(2f)
        p.color = 0xFF000000.toInt()
        c.drawRoundRect(RectF(x - tick - 1.5f, 0f, x + tick + 1.5f, hPx.toFloat()), tick, tick, p)
        p.color = 0xFFFFFFFF.toInt()
        c.drawRoundRect(RectF(x - tick / 2f, 1.5f, x + tick / 2f, hPx - 1.5f), tick / 2f, tick / 2f, p)
        return bmp
    }
}

@Composable
fun RingBox(fraction: Float, color: Int, size: Dp, content: @Composable () -> Unit) {
    Box(
        modifier = GlanceModifier.size(size),
        contentAlignment = Alignment.Center,
    ) {
        Image(
            provider = ImageProvider(RingImage.ring(fraction, color)),
            contentDescription = null,
            modifier = GlanceModifier.fillMaxSize(),
        )
        content()
    }
}
