package app.claudelimit.widget

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.Image
import androidx.glance.ImageProvider
import androidx.glance.LocalSize
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.SizeMode
import androidx.glance.appwidget.action.actionRunCallback
import androidx.glance.appwidget.appWidgetBackground
import androidx.glance.appwidget.provideContent
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.ContentScale
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import app.claudelimit.Palettes
import app.claudelimit.Store
import app.claudelimit.Tx

/** All four widgets are one bitmap drawn at the widget's real size; they differ only in what they show. */
abstract class ChartWidget(private val kind: String) : GlanceAppWidget() {
    override val sizeMode = SizeMode.Exact

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val d = WData.load(context)
        provideContent { ChartContent(context, d, kind) }
    }
}

class SessionWidget : ChartWidget(Kind.SESSION)
class WeekWidget : ChartWidget(Kind.WEEK)
class AllWidget : ChartWidget(Kind.ALL)

class SessionWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = SessionWidget()
}

class WeekWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = WeekWidget()
}

class AllWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = AllWidget()
}

private fun emptyMessage(d: WData, kind: String, hasUsage: Boolean, hasData: Boolean): String = when {
    !d.state.signedIn -> Tx.t("افتح التطبيق وسجّل دخولك", "Open the app and sign in")
    hasUsage && !hasData -> Tx.t("الخطة مفيهاش نسب استخدام", "No usage figures for this plan")
    hasUsage && kind == Kind.ALL -> Tx.t("اختار اللي يظهر من إعدادات التطبيق", "Choose what to show in the app's Settings")
    hasUsage && kind == Kind.WEEK -> Tx.t("لسه مفيش أرقام للأسبوع", "No weekly numbers yet")
    else -> Tx.t("لسه مفيش أرقام", "No numbers yet")
}

@Composable
private fun ChartContent(context: Context, d: WData, kind: String) {
    val size = LocalSize.current
    val now = System.currentTimeMillis()
    val s = d.settings
    val u = d.state.usage()
    val items = buildItems(kind, u?.sessionAt(now), u?.weekly, s, now)
    val density = context.resources.displayMetrics.density
    val plan = Store.planLabel(d.state.plan)
    val bmp = WidgetDraw.render(
        wDp = size.width.value,
        hDp = size.height.value,
        density = density,
        items = items,
        style = styleFor(kind, s),
        pal = Palettes.get(s.palette),
        bgPercent = s.bgAlpha,
        header = if (plan.isNotEmpty()) "Claude $plan" else "Claude",
        headerRight = if (s.showUpdated) updatedText(d, now) else "",
        rtl = Tx.ar,
        message = if (items.isEmpty()) emptyMessage(d, kind, u != null, u?.hasData == true) else null,
    )
    // The refresh strip only exists when the widget is big enough to draw its header.
    val canRefresh = items.isNotEmpty() && size.height >= 120.dp
    Box(modifier = GlanceModifier.fillMaxSize().appWidgetBackground().clickable(openApp(context))) {
        Image(
            provider = ImageProvider(bmp),
            contentDescription = null,
            modifier = GlanceModifier.fillMaxSize(),
            contentScale = ContentScale.FillBounds,
        )
        if (canRefresh) {
            // The whole top strip (the header row) refreshes; the rest of the widget opens the app.
            Box(modifier = GlanceModifier.fillMaxSize(), contentAlignment = Alignment.TopStart) {
                Box(modifier = GlanceModifier.fillMaxWidth().height(34.dp).clickable(actionRunCallback<RefreshAction>())) {}
            }
        }
    }
}
