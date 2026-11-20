package app.claudelimit.ui

import androidx.compose.foundation.background
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
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.claudelimit.LocalAr

@Composable
fun SectionCard(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
    ) {
        Column(Modifier.padding(16.dp), content = content)
    }
}

/** Small grey heading on top of a card. Letter spacing would break Arabic letters apart, so it is English-only. */
@Composable
fun Label(text: String) = Text(
    if (LocalAr.current) text else text.uppercase(),
    style = MaterialTheme.typography.labelMedium,
    color = MaterialTheme.colorScheme.onSurfaceVariant,
    letterSpacing = if (LocalAr.current) TextUnit.Unspecified else 0.8.sp,
)

@Composable
fun Big(text: String, color: Color = Color.Unspecified, size: Int = 34) =
    Text(text, fontSize = size.sp, fontWeight = FontWeight.Bold, color = color, maxLines = 1)

@Composable
fun Pill(text: String, color: Color = MaterialTheme.colorScheme.onSurfaceVariant) {
    Box(
        Modifier.clip(RoundedCornerShape(50)).background(color.copy(alpha = 0.16f)).padding(horizontal = 10.dp, vertical = 3.dp)
    ) { Text(text, color = color, style = MaterialTheme.typography.labelMedium, maxLines = 1) }
}

@Composable
fun Gap(dp: Int) = Spacer(Modifier.height(dp.dp))

@Composable
fun Bar(fraction: Float, color: Color) {
    val track = MaterialTheme.colorScheme.surfaceVariant
    Box(Modifier.fillMaxWidth().height(10.dp).clip(RoundedCornerShape(50)).background(track)) {
        Box(Modifier.fillMaxWidth(fraction.coerceIn(0f, 1f)).fillMaxHeight().clip(RoundedCornerShape(50)).background(color))
    }
}

/** The filled part is what you used; the dark tick is where you should be by now. */
@Composable
fun PaceBar(used: Float, expected: Float, color: Color) {
    val track = MaterialTheme.colorScheme.surfaceVariant
    Box(Modifier.fillMaxWidth().height(20.dp)) {
        Box(Modifier.align(Alignment.CenterStart).fillMaxWidth().height(10.dp).clip(RoundedCornerShape(50)).background(track))
        Box(Modifier.align(Alignment.CenterStart).fillMaxWidth(used.coerceIn(0f, 1f)).height(10.dp).clip(RoundedCornerShape(50)).background(color))
        Box(Modifier.align(Alignment.CenterStart).fillMaxWidth(expected.coerceIn(0.01f, 1f)), contentAlignment = Alignment.CenterEnd) {
            Box(Modifier.width(4.dp).height(20.dp).clip(RoundedCornerShape(2.dp)).background(MaterialTheme.colorScheme.onSurface))
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

@Composable
fun <T> Choices(options: List<T>, selected: T, label: @Composable (T) -> String, onSelect: (T) -> Unit) {
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        options.forEach { o ->
            FilterChip(selected = o == selected, onClick = { onSelect(o) }, label = { Text(label(o)) })
        }
    }
}
