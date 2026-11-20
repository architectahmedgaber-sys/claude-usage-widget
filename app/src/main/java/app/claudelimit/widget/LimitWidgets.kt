package app.claudelimit.widget

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.Image
import androidx.glance.ImageProvider
import androidx.glance.LocalSize
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.LinearProgressIndicator
import androidx.glance.appwidget.SizeMode
import androidx.glance.appwidget.action.actionRunCallback
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.provideContent
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.Column
import androidx.glance.layout.ContentScale
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.width
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import app.claudelimit.Tx
import app.claudelimit.core.Limit
import app.claudelimit.core.Pace
import app.claudelimit.core.PaceCalc
import app.claudelimit.core.PaceStatus
import app.claudelimit.core.TimeText
import kotlin.math.roundToInt

// ---------- 5 hours ----------

class SessionWidget : GlanceAppWidget() {
    override val sizeMode = SizeMode.Exact

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val d = WData.load(context)
        provideContent { SingleLimit(context, d, week = false) }
    }
}

class SessionWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = SessionWidget()
}

// ---------- Week ----------

class WeekWidget : GlanceAppWidget() {
    override val sizeMode = SizeMode.Exact

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val d = WData.load(context)
        provideContent { SingleLimit(context, d, week = true) }
    }
}

class WeekWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = WeekWidget()
}

/** A ring with what is left (or used), and when it resets. Wide widgets put the text beside the ring. */
@Composable
private fun SingleLimit(context: Context, d: WData, week: Boolean) {
    val size = LocalSize.current
    val now = System.currentTimeMillis()
    val u = d.state.usage()
    val limit: Limit? = if (week) u?.weekly else u?.sessionAt(now)
    val s = d.settings
    WidgetCard(context) {
        if (limit == null) {
            Note(
                when {
                    !d.state.signedIn -> Tx.t("افتح التطبيق وسجّل دخولك", "Open the app and sign in")
                    u != null && !u.hasData -> Tx.t("الخطة مفيهاش نسب استخدام", "No usage figures for this plan")
                    else -> Tx.t("لسه مفيش أرقام", "No numbers yet")
                }
            )
        } else {
            val shown = if (s.showUsed) limit.usedPercent else limit.remainingPercent
            val pct = "${shown.roundToInt()}%"
            val ring = ringColor(limit.usedPercent)
            val title = if (week) Tx.t("الأسبوع", "Weekly") else Tx.t("5 ساعات", "5 hours")
            val sub = if (s.showUsed) Tx.t("مستخدم", "used") else Tx.t("فاضل", "left")
            val r = limit.resetsAtMs
            val reset = if (s.showReset && r != null) {
                Tx.t("بعد ${TimeText.left(r - now, true)}", "in ${TimeText.left(r - now, false)}")
            } else null
            val updated = if (s.showUpdated) updatedText(d, now) else ""
            Box(modifier = GlanceModifier.fillMaxSize()) {
                if (size.width >= 150.dp) {
                    val ringSize = (size.height.value - 28f).coerceIn(40f, 120f).dp
                    Row(modifier = GlanceModifier.fillMaxSize(), verticalAlignment = Alignment.CenterVertically) {
                        RingBox(shown.toFloat() / 100f, ring, ringSize) {
                            Text(pct, style = TextStyle(color = inkColor, fontSize = (ringSize.value * 0.24f).sp, fontWeight = FontWeight.Bold))
                        }
                        Spacer(GlanceModifier.width(14.dp))
                        Column {
                            label(title)
                            value("$pct $sub", 22, textColor(limit.usedPercent))
                            if (reset != null) label(reset)
                            if (updated.isNotEmpty()) label(updated, 11)
                        }
                    }
                } else {
                    val reserve = (if (reset != null) 16 else 0) + 16
                    val ringSize = ringSizeFor(size, reserve)
                    Column(
                        modifier = GlanceModifier.fillMaxSize(),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        label(title)
                        RingBox(shown.toFloat() / 100f, ring, ringSize) {
                            Text(pct, style = TextStyle(color = inkColor, fontSize = (ringSize.value * 0.26f).sp, fontWeight = FontWeight.Bold))
                        }
                        if (reset != null) label(reset, 11)
                    }
                }
                RefreshCorner()
            }
        }
    }
}

@Composable
private fun RefreshCorner(corner: Alignment = Alignment.TopEnd) {
    Box(modifier = GlanceModifier.fillMaxSize(), contentAlignment = corner) {
        Text(
            "↻",
            modifier = GlanceModifier.padding(horizontal = 6.dp, vertical = 2.dp).clickable(actionRunCallback<RefreshAction>()),
            style = TextStyle(color = subColor, fontSize = 18.sp, fontWeight = FontWeight.Bold),
        )
    }
}

// ---------- Pace ----------

class PaceWidget : GlanceAppWidget() {
    override val sizeMode = SizeMode.Exact

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val d = WData.load(context)
        provideContent { PaceContent(context, d) }
    }
}

class PaceWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = PaceWidget()
}

private fun paceColorProvider(p: Pace) = when {
    p.status != PaceStatus.OVER -> goodColor
    p.diff > 15.0 -> badColor
    else -> warnColor
}

private fun paceRing(p: Pace) = when {
    p.status != PaceStatus.OVER -> RING_GREEN
    p.diff > 15.0 -> RING_RED
    else -> RING_AMBER
}

private fun paceStatusText(p: Pace): String {
    val n = kotlin.math.abs(p.diff).roundToInt()
    return when (p.status) {
        PaceStatus.OVER -> Tx.t("أعلى من المفروض بـ $n%", "$n% ahead of the plan")
        PaceStatus.UNDER -> Tx.t("أقل من المفروض بـ $n%", "$n% under the plan")
        PaceStatus.ON_TRACK -> Tx.t("ماشي مظبوط", "Right on plan")
    }
}

@Composable
private fun PaceContent(context: Context, d: WData) {
    val size = LocalSize.current
    val now = System.currentTimeMillis()
    val weekly = d.state.usage()?.weekly
    val pace = weekly?.let { PaceCalc.compute(it, now) }
    WidgetCard(context) {
        if (pace == null) {
            Note(
                if (!d.state.signedIn) Tx.t("افتح التطبيق وسجّل دخولك", "Open the app and sign in")
                else Tx.t("لسه مفيش أرقام للأسبوع", "No weekly numbers yet")
            )
        } else {
            val density = context.resources.displayMetrics.density
            val barW = ((size.width.value - 28f) * density).toInt().coerceIn(200, 1600)
            val barH = (16f * density).toInt()
            val bar = RingImage.paceBar(
                barW, barH,
                (pace.actualUsed / 100.0).toFloat(), (pace.expectedUsed / 100.0).toFloat(), paceRing(pace),
            )
            val tall = size.height >= 130.dp
            Box(modifier = GlanceModifier.fillMaxSize()) {
                Column(modifier = GlanceModifier.fillMaxSize(), verticalAlignment = Alignment.CenterVertically) {
                    label(Tx.t("الخطة الأسبوعية · يوم ${pace.dayNumber} من 7", "Weekly plan · day ${pace.dayNumber} of 7"))
                    Spacer(GlanceModifier.height(4.dp))
                    Row(modifier = GlanceModifier.fillMaxWidth()) {
                        Column(modifier = GlanceModifier.defaultWeight()) {
                            label(Tx.t("المفروض تكون خلصت", "You should have used"), 11)
                            value("${pace.expectedUsed.roundToInt()}%", 22)
                        }
                        Column(modifier = GlanceModifier.defaultWeight()) {
                            label(Tx.t("انت خلصت", "You have used"), 11)
                            value("${pace.actualUsed.roundToInt()}%", 22, paceColorProvider(pace))
                        }
                    }
                    Spacer(GlanceModifier.height(6.dp))
                    Image(
                        provider = ImageProvider(bar),
                        contentDescription = null,
                        modifier = GlanceModifier.fillMaxWidth().height(16.dp),
                        contentScale = ContentScale.FillBounds,
                    )
                    Spacer(GlanceModifier.height(4.dp))
                    Text(
                        paceStatusText(pace),
                        maxLines = 1,
                        style = TextStyle(color = paceColorProvider(pace), fontSize = 13.sp, fontWeight = FontWeight.Bold),
                    )
                    if (tall) {
                        label(
                            Tx.t(
                                "تقدر تستهلك ${pace.safePerDay.roundToInt()}% في اليوم لحد التصفير",
                                "You can use ${pace.safePerDay.roundToInt()}% a day until the reset",
                            ),
                            11,
                        )
                    }
                }
                RefreshCorner()
            }
        }
    }
}

// ---------- All in one: shows only the rows chosen in Settings ----------

class AllWidget : GlanceAppWidget() {
    override val sizeMode = SizeMode.Exact

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val d = WData.load(context)
        provideContent { AllContent(context, d) }
    }
}

class AllWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = AllWidget()
}

@Composable
private fun LimitRow(title: String, limit: Limit, now: Long, d: WData) {
    val s = d.settings
    val shown = if (s.showUsed) limit.usedPercent else limit.remainingPercent
    val r = limit.resetsAtMs
    val reset = if (s.showReset && r != null) Tx.t("بعد ${TimeText.left(r - now, true)}", "in ${TimeText.left(r - now, false)}") else ""
    Column(modifier = GlanceModifier.fillMaxWidth()) {
        Row(modifier = GlanceModifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(title, maxLines = 1, modifier = GlanceModifier.defaultWeight(), style = TextStyle(color = subColor, fontSize = 12.sp))
            if (reset.isNotEmpty()) {
                Text(reset, maxLines = 1, style = TextStyle(color = subColor, fontSize = 11.sp))
                Spacer(GlanceModifier.width(8.dp))
            }
            Text(
                "${shown.roundToInt()}% " + (if (s.showUsed) Tx.t("مستخدم", "used") else Tx.t("فاضل", "left")),
                maxLines = 1,
                style = TextStyle(color = textColor(limit.usedPercent), fontSize = 15.sp, fontWeight = FontWeight.Bold),
            )
        }
        Spacer(GlanceModifier.height(3.dp))
        LinearProgressIndicator(
            progress = (shown / 100.0).toFloat().coerceIn(0f, 1f),
            modifier = GlanceModifier.fillMaxWidth().height(7.dp).cornerRadius(4.dp),
            color = textColor(limit.usedPercent),
            backgroundColor = trackColor,
        )
    }
}

@Composable
private fun PaceRow(context: Context, pace: Pace) {
    val size = LocalSize.current
    val density = context.resources.displayMetrics.density
    val barW = ((size.width.value - 28f) * density).toInt().coerceIn(200, 1600)
    val barH = (14f * density).toInt()
    val bar = RingImage.paceBar(
        barW, barH, (pace.actualUsed / 100.0).toFloat(), (pace.expectedUsed / 100.0).toFloat(), paceRing(pace),
    )
    Column(modifier = GlanceModifier.fillMaxWidth()) {
        Row(modifier = GlanceModifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(
                Tx.t("المفروض ${pace.expectedUsed.roundToInt()}% · انت ${pace.actualUsed.roundToInt()}%", "Plan ${pace.expectedUsed.roundToInt()}% · you ${pace.actualUsed.roundToInt()}%"),
                maxLines = 1,
                modifier = GlanceModifier.defaultWeight(),
                style = TextStyle(color = subColor, fontSize = 12.sp),
            )
            Text(
                paceStatusText(pace),
                maxLines = 1,
                style = TextStyle(color = paceColorProvider(pace), fontSize = 13.sp, fontWeight = FontWeight.Bold),
            )
        }
        Spacer(GlanceModifier.height(2.dp))
        Image(
            provider = ImageProvider(bar),
            contentDescription = null,
            modifier = GlanceModifier.fillMaxWidth().height(14.dp),
            contentScale = ContentScale.FillBounds,
        )
    }
}

@Composable
private fun AllContent(context: Context, d: WData) {
    val now = System.currentTimeMillis()
    val s = d.settings
    val u = d.state.usage()
    val session = u?.sessionAt(now)
    val weekly = u?.weekly
    val pace = weekly?.let { PaceCalc.compute(it, now) }
    WidgetCard(context) {
        if (u == null || !u.hasData) {
            Note(
                when {
                    !d.state.signedIn -> Tx.t("افتح التطبيق وسجّل دخولك", "Open the app and sign in")
                    u != null -> Tx.t("الخطة مفيهاش نسب استخدام", "No usage figures for this plan")
                    else -> Tx.t("لسه مفيش أرقام", "No numbers yet")
                }
            )
        } else if (!s.showSession && !s.showWeek && !s.showPace) {
            Note(Tx.t("اختار اللي يظهر من إعدادات التطبيق", "Choose what to show in the app's Settings"))
        } else {
            val updated = if (s.showUpdated) updatedText(d, now) else ""
            Box(modifier = GlanceModifier.fillMaxSize()) {
                Column(modifier = GlanceModifier.fillMaxSize(), verticalAlignment = Alignment.CenterVertically) {
                    var first = true
                    if (s.showSession && session != null) {
                        LimitRow(Tx.t("5 ساعات", "5 hours"), session, now, d)
                        first = false
                    }
                    if (s.showWeek && weekly != null) {
                        if (!first) Spacer(GlanceModifier.height(10.dp))
                        LimitRow(Tx.t("الأسبوع", "Weekly"), weekly, now, d)
                        first = false
                    }
                    if (s.showPace && pace != null) {
                        if (!first) Spacer(GlanceModifier.height(10.dp))
                        PaceRow(context, pace)
                        first = false
                    }
                    if (updated.isNotEmpty()) {
                        Spacer(GlanceModifier.height(6.dp))
                        label(updated, 10)
                    }
                }
                RefreshCorner(Alignment.BottomEnd)
            }
        }
    }
}
