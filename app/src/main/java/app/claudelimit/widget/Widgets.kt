package app.claudelimit.widget

import android.content.Context
import android.content.Intent
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceModifier
import androidx.glance.action.Action
import androidx.glance.action.ActionParameters
import androidx.glance.action.clickable
import androidx.glance.appwidget.action.ActionCallback
import androidx.glance.appwidget.action.actionRunCallback
import androidx.glance.appwidget.action.actionStartActivity
import androidx.glance.appwidget.appWidgetBackground
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.updateAll
import androidx.glance.background
import androidx.glance.color.ColorProvider
import androidx.glance.GlanceId
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.padding
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider
import androidx.glance.LocalSize
import app.claudelimit.MainActivity
import app.claudelimit.Settings
import app.claudelimit.Store
import app.claudelimit.StoreState
import app.claudelimit.Tx
import app.claudelimit.core.TimeText
import app.claudelimit.work.RefreshWorker

object Widgets {
    suspend fun updateAll(context: Context) {
        SessionWidget().updateAll(context)
        WeekWidget().updateAll(context)
        PaceWidget().updateAll(context)
        AllWidget().updateAll(context)
    }
}

/** What every widget needs: the person's choices and the last numbers. */
data class WData(val settings: Settings, val state: StoreState) {
    companion object {
        fun load(context: Context): WData {
            Store.init(context)
            Tx.ar = Store.settings.value.lang == "AR"
            return WData(Store.settings.value, Store.state.value)
        }
    }
}

// ---- Palette: the same greens as the app, readable on light and dark home screens ----
val cardBg: ColorProvider = ColorProvider(day = Color(0xFFFBFDF9), night = Color(0xFF232A26))
val inkColor: ColorProvider = ColorProvider(day = Color(0xFF18231D), night = Color(0xFFEEF4EE))
val subColor: ColorProvider = ColorProvider(day = Color(0xFF5C6B62), night = Color(0xFFAAB8AE))
val goodColor: ColorProvider = ColorProvider(day = Color(0xFF1F7A3F), night = Color(0xFF8FD8A4))
val warnColor: ColorProvider = ColorProvider(day = Color(0xFF9A6B00), night = Color(0xFFFFC94D))
val badColor: ColorProvider = ColorProvider(day = Color(0xFFB3261E), night = Color(0xFFF2B8B5))
val trackColor: ColorProvider = ColorProvider(day = Color(0xFFDCE8DF), night = Color(0xFF38433C))

// Fixed colours for drawings (bitmaps can't follow light/dark, so these work on both).
const val RING_GREEN = 0xFF2E9E6E.toInt()
const val RING_AMBER = 0xFFE0A000.toInt()
const val RING_RED = 0xFFD9453B.toInt()

/** 0 = fine (under 50% used), 1 = careful (under 80%), 2 = nearly out. */
fun levelOf(usedPercent: Double): Int = when {
    usedPercent >= 80.0 -> 2
    usedPercent >= 50.0 -> 1
    else -> 0
}

fun ringColor(usedPercent: Double): Int = when (levelOf(usedPercent)) {
    2 -> RING_RED
    1 -> RING_AMBER
    else -> RING_GREEN
}

fun textColor(usedPercent: Double): ColorProvider = when (levelOf(usedPercent)) {
    2 -> badColor
    1 -> warnColor
    else -> goodColor
}

fun openApp(context: Context): Action =
    actionStartActivity(Intent(context, MainActivity::class.java).apply {
        flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
    })

/** The small refresh button in the corner of every widget. */
class RefreshAction : ActionCallback {
    override suspend fun onAction(context: Context, glanceId: GlanceId, parameters: ActionParameters) {
        RefreshWorker.refreshNow(context)
    }
}

/** The rounded card every widget sits on. */
@Composable
fun WidgetCard(context: Context, content: @Composable () -> Unit) {
    val size = LocalSize.current
    val compact = size.height < 90.dp || size.width < 90.dp
    Box(
        modifier = GlanceModifier
            .fillMaxSize()
            .appWidgetBackground()
            .cornerRadius(26.dp)
            .background(cardBg)
            .clickable(openApp(context))
            .padding(if (compact) 8.dp else 14.dp),
        contentAlignment = Alignment.Center,
    ) {
        content()
    }
}

@Composable
fun label(text: String, size: Int = 12, color: ColorProvider? = null) = Text(
    text,
    maxLines = 1,
    style = TextStyle(color = color ?: subColor, fontSize = size.sp),
)

@Composable
fun value(text: String, size: Int = 24, color: ColorProvider? = null) = Text(
    text,
    maxLines = 1,
    style = TextStyle(color = color ?: inkColor, fontSize = size.sp, fontWeight = FontWeight.Bold),
)

@Composable
fun Note(text: String) = Text(
    text,
    style = TextStyle(color = inkColor, fontSize = 13.sp, fontWeight = FontWeight.Medium),
)

/** "قبل 5 د", with a warning sign when the numbers are old or the last try failed. */
fun updatedText(d: WData, now: Long): String {
    val f = d.state.fetchedAtMs
    if (f == 0L) return ""
    val ago = TimeText.ago(now - f, Tx.ar)
    val stale = d.state.error != null || now - f > maxOf(3L * d.settings.refreshMinutes, 45L) * 60_000L
    return (if (stale) "⚠ " else "") + ago
}

/** A ring size that fits the widget with room for [reserve] dp of text under it. */
fun ringSizeFor(size: DpSize, reserve: Int): androidx.compose.ui.unit.Dp =
    minOf(size.width.value - 28f, size.height.value - 28f - reserve).coerceIn(40f, 120f).dp
