package app.claudelimit.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.claudelimit.LocalAr

/** A flat panel with a thin outline, like a plate on an instrument panel. */
@Composable
fun SectionCard(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    val shape = RoundedCornerShape(16.dp)
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(shape)
            .background(MaterialTheme.colorScheme.surfaceContainer)
            .border(1.dp, MaterialTheme.colorScheme.outline, shape)
            .padding(16.dp),
        content = content,
    )
}

/** Small grey heading on top of a panel. Letter spacing would break Arabic letters apart, so it is English-only. */
@Composable
fun Label(text: String) = Text(
    if (LocalAr.current) text else text.uppercase(),
    fontSize = 12.sp,
    fontWeight = FontWeight.Medium,
    color = MaterialTheme.colorScheme.onSurfaceVariant,
    letterSpacing = if (LocalAr.current) TextUnit.Unspecified else 1.2.sp,
)

/** Numbers use a fixed-width face, which gives the app its dial-and-meter feel. */
@Composable
fun Big(text: String, color: Color = Color.Unspecified, size: Int = 34) =
    Text(text, fontSize = size.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace, color = color, maxLines = 1)

/** A small outlined stamp. */
@Composable
fun Tag(text: String, color: Color = MaterialTheme.colorScheme.onSurfaceVariant) {
    Box(
        Modifier.border(1.dp, color, RoundedCornerShape(6.dp)).padding(horizontal = 10.dp, vertical = 4.dp)
    ) { Text(text, color = color, fontSize = 13.sp, fontWeight = FontWeight.Medium, maxLines = 1) }
}

@Composable
fun Gap(dp: Int) = Spacer(Modifier.height(dp.dp))

@Composable
fun Bar(fraction: Float, color: Color) {
    val track = MaterialTheme.colorScheme.surfaceVariant
    Box(Modifier.fillMaxWidth().height(8.dp).clip(RoundedCornerShape(50)).background(track)) {
        Box(Modifier.fillMaxWidth(fraction.coerceIn(0f, 1f)).fillMaxHeight().clip(RoundedCornerShape(50)).background(color))
    }
}

/** The filled part is what you used; the white tick is where you should be by now. */
@Composable
fun PaceBar(used: Float, expected: Float, color: Color) {
    val track = MaterialTheme.colorScheme.surfaceVariant
    val mark = MaterialTheme.colorScheme.onSurface
    Box(Modifier.fillMaxWidth().height(20.dp)) {
        Box(Modifier.align(Alignment.CenterStart).fillMaxWidth().height(8.dp).clip(RoundedCornerShape(50)).background(track))
        Box(Modifier.align(Alignment.CenterStart).fillMaxWidth(used.coerceIn(0f, 1f)).height(8.dp).clip(RoundedCornerShape(50)).background(color))
        Box(Modifier.align(Alignment.CenterStart).fillMaxWidth(expected.coerceIn(0.01f, 1f)), contentAlignment = Alignment.CenterEnd) {
            Box(Modifier.width(3.dp).height(20.dp).clip(RoundedCornerShape(2.dp)).background(mark))
        }
    }
}

/** Separate cells, each one a slice of the whole: ten for the 5 hours, seven for the days of the week. */
@Composable
fun SegmentRow(n: Int, fraction: Float, marker: Float?, color: Color, height: Dp = 20.dp) {
    val track = MaterialTheme.colorScheme.surfaceVariant
    val mark = MaterialTheme.colorScheme.onSurface
    Canvas(Modifier.fillMaxWidth().height(height + 8.dp)) {
        val gap = 4.dp.toPx()
        val segW = (size.width - gap * (n - 1)) / n
        val top = 4.dp.toPx()
        val h = height.toPx()
        val corner = CornerRadius(4.dp.toPx())
        val f = fraction.coerceIn(0f, 1f)
        for (i in 0 until n) {
            val x = i * (segW + gap)
            drawRoundRect(track, Offset(x, top), Size(segW, h), corner)
            val part = (f * n - i).coerceIn(0f, 1f)
            if (part > 0.03f) drawRoundRect(color, Offset(x, top), Size(segW * part, h), corner)
        }
        if (marker != null) {
            val x = (marker.coerceIn(0f, 1f) * n * (segW + gap) - gap / 2f).coerceIn(0f, size.width)
            drawRoundRect(mark, Offset(x - 1.5.dp.toPx(), 0f), Size(3.dp.toPx(), size.height), CornerRadius(1.5.dp.toPx()))
        }
    }
}

@Composable
fun Toggle(title: String, checked: Boolean, subtitle: String? = null, onChange: (Boolean) -> Unit) {
    Row(
        Modifier.fillMaxWidth().padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Column(Modifier.weight(1f)) {
            Text(title)
            if (subtitle != null) Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Switch(checked = checked, onCheckedChange = onChange)
    }
}

/** Square-cornered choice chips: the chosen one is filled with the main colour. */
@Composable
fun <T> Choices(options: List<T>, selected: T, label: @Composable (T) -> String, onSelect: (T) -> Unit) {
    val shape = RoundedCornerShape(8.dp)
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        options.forEach { o ->
            val on = o == selected
            val bg = if (on) MaterialTheme.colorScheme.primary else Color.Transparent
            val fg = if (on) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface
            Box(
                Modifier
                    .clip(shape)
                    .background(bg)
                    .border(1.dp, if (on) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline, shape)
                    .clickable { onSelect(o) }
                    .padding(horizontal = 14.dp, vertical = 8.dp)
            ) { Text(label(o), color = fg, fontSize = 14.sp, fontWeight = if (on) FontWeight.Bold else FontWeight.Normal) }
        }
    }
}
