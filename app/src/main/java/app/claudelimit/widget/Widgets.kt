package app.claudelimit.widget

import android.content.Context
import android.content.Intent
import androidx.glance.GlanceId
import androidx.glance.action.Action
import androidx.glance.action.ActionParameters
import androidx.glance.appwidget.action.ActionCallback
import androidx.glance.appwidget.action.actionStartActivity
import androidx.glance.appwidget.updateAll
import app.claudelimit.MainActivity
import app.claudelimit.Settings
import app.claudelimit.Store
import app.claudelimit.StoreState
import app.claudelimit.Tx
import app.claudelimit.core.Limit
import app.claudelimit.core.PaceCalc
import app.claudelimit.core.PaceStatus
import app.claudelimit.core.TimeText
import app.claudelimit.levelOf
import app.claudelimit.work.RefreshWorker
import kotlin.math.roundToInt

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

fun openApp(context: Context): Action =
    actionStartActivity(Intent(context, MainActivity::class.java).apply {
        flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
    })

/** The refresh tap in the corner of a widget. */
class RefreshAction : ActionCallback {
    override suspend fun onAction(context: Context, glanceId: GlanceId, parameters: ActionParameters) {
        RefreshWorker.refreshNow(context)
    }
}

/** "قبل 5 د", with a warning sign when the numbers are old or the last try failed. */
fun updatedText(d: WData, now: Long): String {
    val f = d.state.fetchedAtMs
    if (f == 0L) return ""
    val ago = TimeText.ago(now - f, Tx.ar)
    val stale = d.state.error != null || now - f > maxOf(3L * d.settings.refreshMinutes, 45L) * 60_000L
    return (if (stale) "⚠ " else "") + ago
}

/** Which widget a set of items is for. */
object Kind {
    const val SESSION = "SESSION"
    const val WEEK = "WEEK"
    const val PACE = "PACE"
    const val ALL = "ALL"
}

/** Turns the numbers into the things a widget draws, following the person's choices. */
fun buildItems(kind: String, session: Limit?, weekly: Limit?, s: Settings, now: Long): List<DrawItem> {
    fun limitItem(title: String, l: Limit, expectedUsed: Double?, segs: Int): DrawItem {
        val shown = if (s.showUsed) l.usedPercent else l.remainingPercent
        val r = l.resetsAtMs
        val sub = if (s.showReset && r != null) {
            Tx.t("بعد ${TimeText.left(r - now, true)}", "in ${TimeText.left(r - now, false)}")
        } else ""
        val marker = expectedUsed?.let { e -> ((if (s.showUsed) e else 100.0 - e) / 100.0).toFloat() }
        return DrawItem(title, "${shown.roundToInt()}%", (shown / 100.0).toFloat().coerceIn(0f, 1f), levelOf(l.usedPercent), marker, sub, segs)
    }

    val pace = weekly?.let { PaceCalc.compute(it, now) }
    val sessionItem = session?.let { limitItem(Tx.t("5 ساعات", "5 hours"), it, null, 10) }
    val weekItem = weekly?.let { limitItem(Tx.t("الأسبوع", "Weekly"), it, pace?.expectedUsed, 7) }
    val paceItem = pace?.let { p ->
        val n = p.diff.roundToInt()
        val value = if (n > 0) "+$n%" else "$n%"
        val level = when {
            p.status != PaceStatus.OVER -> 0
            p.diff > 15.0 -> 2
            else -> 1
        }
        DrawItem(
            Tx.t("الخطة", "Plan"),
            value,
            (p.actualUsed / 100.0).toFloat().coerceIn(0f, 1f),
            level,
            (p.expectedUsed / 100.0).toFloat().coerceIn(0f, 1f),
            Tx.t("يوم ${p.dayNumber} · المفروض ${p.expectedUsed.roundToInt()}%", "day ${p.dayNumber} · plan ${p.expectedUsed.roundToInt()}%"),
            7,
        )
    }
    return when (kind) {
        Kind.SESSION -> listOfNotNull(sessionItem)
        Kind.WEEK -> listOfNotNull(weekItem)
        Kind.PACE -> listOfNotNull(paceItem)
        else -> buildList {
            if (s.showSession && sessionItem != null) add(sessionItem)
            if (s.showWeek && weekItem != null) add(weekItem)
            if (s.showPace && paceItem != null) add(paceItem)
        }
    }
}

fun styleFor(kind: String, s: Settings): String = when (kind) {
    Kind.SESSION -> s.styleSession
    Kind.WEEK -> s.styleWeek
    Kind.PACE -> s.stylePace
    else -> s.styleAll
}
