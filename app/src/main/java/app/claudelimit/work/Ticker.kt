package app.claudelimit.work

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.SystemClock
import app.claudelimit.Store

/**
 * Keeps the numbers fresh in the background.
 *
 * Android's WorkManager never runs more often than every 15 minutes, so that stays as the base. For 1 to 10
 * minutes a light alarm fires while the phone is awake (it does not wake a sleeping phone), and each alarm
 * starts an instant refresh and sets the next one.
 */
object Ticker {
    private const val REQUEST = 77

    fun apply(context: Context, minutes: Int) {
        RefreshWorker.schedule(context, minutes)
        if (minutes < 15) arm(context, minutes) else cancel(context)
    }

    fun arm(context: Context, minutes: Int) {
        val app = context.applicationContext
        val am = app.getSystemService(AlarmManager::class.java) ?: return
        val every = minutes.coerceAtLeast(1) * 60_000L
        val at = SystemClock.elapsedRealtime() + every
        try {
            am.setWindow(AlarmManager.ELAPSED_REALTIME, at, maxOf(20_000L, every / 5), pending(app))
        } catch (e: Exception) {
            // If the system refuses, the 15-minute base still runs.
        }
    }

    fun cancel(context: Context) {
        val app = context.applicationContext
        app.getSystemService(AlarmManager::class.java)?.cancel(pending(app))
    }

    private fun pending(context: Context): PendingIntent = PendingIntent.getBroadcast(
        context, REQUEST, Intent(context, TickReceiver::class.java),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
    )
}

class TickReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        Store.init(context)
        val minutes = Store.settings.value.refreshMinutes
        if (minutes >= 15) return
        Ticker.arm(context, minutes)
        if (Store.state.value.signedIn) RefreshWorker.refreshNow(context, replace = false)
    }
}
