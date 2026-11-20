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
import app.claudelimit.core.TimeText
import app.claudelimit.levelOf
import app.claudelimit.work.RefreshWorker
import kotlin.math.roundToInt

object Widgets {
    suspend fun updateAll(context: Context) {
        SessionWidget().updateAll(context)
        WeekWidget().updateAll(context)
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
    const val ALL = "ALL"
}

/** Turns the numbers into the things a widget draws, following the person's choices. */
fun buildItems(kind: String, session: Limit?, weekly: Limit?, s: Settings, now: Long): List<DrawItem> {
    fun limitItem(title: String, l: Limit, expectedUsed: Double?, segs: Int): DrawItem {
        val shown = l.usedPercent
        val r = l.resetsAtMs
        val sub = if (s.showReset && r != null) {
            Tx.t("فاضل ${TimeText.left(r - now, true)} على التجديد", "${TimeText.left(r - now, false)} until reset")
        } else ""
        val marker = expectedUsed?.let { e -> (e / 100.0).toFloat() }
        return DrawItem(title, "${shown.roundToInt()}%", (shown / 100.0).toFloat().coerceIn(0f, 1f), levelOf(l.usedPercent), marker, sub, segs)
    }

    val pace = weekly?.let { PaceCalc.compute(it, now) }
    val sessionItem = session?.let { limitItem(Tx.t("5 ساعات", "5 hours"), it, null, 10) }
    val weekItem = weekly?.let { limitItem(Tx.t("الأسبوع", "Weekly"), it, pace?.expectedUsed, 7) }
    return when (kind) {
        Kind.SESSION -> listOfNotNull(sessionItem)
        Kind.WEEK -> listOfNotNull(weekItem)
        else -> buildList {
            if (s.showSession && sessionItem != null) add(sessionItem)
            if (s.showWeek && weekItem != null) add(weekItem)
        }
    }
}

fun styleFor(kind: String, s: Settings): String = when (kind) {
    Kind.SESSION -> s.styleSession
    Kind.WEEK -> s.styleWeek
    else -> s.styleAll
}
