package app.claudelimit.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/** A half-circle dial with tick marks and a needle. [fraction] is 0..1; [marker] is where you should be by now. */
@Composable
fun GaugeDial(fraction: Float, marker: Float?, color: Color, modifier: Modifier = Modifier) {
    val track = MaterialTheme.colorScheme.surfaceVariant
    val ink = MaterialTheme.colorScheme.onSurface
    val sub = MaterialTheme.colorScheme.onSurfaceVariant
    val f = fraction.coerceIn(0f, 1f)
    Canvas(modifier.fillMaxWidth().aspectRatio(1.75f)) {
        val w = size.width
        val h = size.height
        val stroke = w * 0.07f
        val r = w / 2f - stroke * 1.9f
        val cx = w / 2f
        val cy = h - stroke
        val topLeft = Offset(cx - r, cy - r)
        val box = Size(2 * r, 2 * r)
        drawArc(
            color = track, startAngle = 180f, sweepAngle = 180f, useCenter = false,
            topLeft = topLeft, size = box, style = Stroke(width = stroke, cap = StrokeCap.Round),
        )
        if (f > 0.005f) {
            drawArc(
                color = color, startAngle = 180f, sweepAngle = 180f * f, useCenter = false,
                topLeft = topLeft, size = box, style = Stroke(width = stroke, cap = StrokeCap.Round),
            )
        }
        for (i in 0..10) {
            val a = PI * (1.0 - i / 10.0)
            val r1 = r + stroke * 1.15f
            val r2 = r + stroke * (if (i % 5 == 0) 1.75f else 1.45f)
            drawLine(
                sub,
                Offset(cx + cos(a).toFloat() * r1, cy - sin(a).toFloat() * r1),
                Offset(cx + cos(a).toFloat() * r2, cy - sin(a).toFloat() * r2),
                strokeWidth = 1.5.dp.toPx(), cap = StrokeCap.Round,
            )
        }
        if (marker != null) {
            val a = PI * (1.0 - marker.coerceIn(0f, 1f))
            drawLine(
                ink,
                Offset(cx + cos(a).toFloat() * (r - stroke * 0.8f), cy - sin(a).toFloat() * (r - stroke * 0.8f)),
                Offset(cx + cos(a).toFloat() * (r + stroke * 0.8f), cy - sin(a).toFloat() * (r + stroke * 0.8f)),
                strokeWidth = 2.5.dp.toPx(), cap = StrokeCap.Round,
            )
        }
        val needle = PI * (1.0 - f)
        drawLine(
            ink,
            Offset(cx, cy),
            Offset(cx + cos(needle).toFloat() * r * 0.74f, cy - sin(needle).toFloat() * r * 0.74f),
            strokeWidth = 3.dp.toPx(), cap = StrokeCap.Round,
        )
        drawCircle(ink, 6.dp.toPx(), Offset(cx, cy))
    }
}
