package app.claudelimit.work

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.pm.ServiceInfo
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.ForegroundInfo
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.OutOfQuotaPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import app.claudelimit.R
import app.claudelimit.Store
import app.claudelimit.core.UsageParser
import app.claudelimit.net.FetchException
import app.claudelimit.net.FetchKind
import app.claudelimit.net.WebFetcher
import app.claudelimit.widget.Widgets
import kotlinx.coroutines.sync.Mutex
import java.util.concurrent.TimeUnit

/** Reads the numbers from claude.ai, saves them and redraws the widgets. One at a time. */
object Refresher {
    private val mutex = Mutex()

    suspend fun run(context: Context) {
        val app = context.applicationContext
        Store.init(app)
        if (!Store.state.value.signedIn) return
        if (!mutex.tryLock()) return
        try {
            try {
                val r = WebFetcher.fetch(app, Store.state.value.org)
                // Keep the last good numbers if what came back is not usage data.
                val ok = runCatching { UsageParser.parse(r.body, 0L) }.isSuccess
                if (ok) Store.saveUsage(app, r.body, r.plan, r.org) else Store.saveError(app, "OTHER:format")
            } catch (e: FetchException) {
                if (e.kind == FetchKind.SESSION) Store.setSignedIn(app, false)
                Store.saveError(
                    app,
                    when (e.kind) {
                        FetchKind.SESSION -> "SESSION"
                        FetchKind.NETWORK -> "NETWORK"
                        FetchKind.OTHER -> "OTHER:${e.message}"
                    },
                )
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Exception) {
                Store.saveError(app, "OTHER:${e.javaClass.simpleName}")
            }
            Widgets.updateAll(app)
        } finally {
            mutex.unlock()
        }
    }
}

class RefreshWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        Refresher.run(applicationContext)
        return Result.success()
    }

    /** Needed by instant (expedited) refreshes on Android 10 and 11. */
    override suspend fun getForegroundInfo(): ForegroundInfo {
        val nm = applicationContext.getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(
            NotificationChannel(CHANNEL, applicationContext.getString(R.string.refresh_channel), NotificationManager.IMPORTANCE_MIN)
        )
        val n = NotificationCompat.Builder(applicationContext, CHANNEL)
            .setSmallIcon(R.drawable.ic_stat)
            .setContentTitle(applicationContext.getString(R.string.refresh_title))
            .setOngoing(true)
            .build()
        return if (Build.VERSION.SDK_INT >= 29) ForegroundInfo(NOTIFICATION_ID, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
        else ForegroundInfo(NOTIFICATION_ID, n)
    }

    companion object {
        private const val CHANNEL = "refresh"
        private const val NOTIFICATION_ID = 41
        private val online = Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build()

        /** Regular refresh in the background. Android runs it at most every 15 minutes. */
        fun schedule(context: Context, minutes: Int) {
            val req = PeriodicWorkRequestBuilder<RefreshWorker>(minutes.coerceAtLeast(15).toLong(), TimeUnit.MINUTES)
                .setConstraints(online)
                .build()
            WorkManager.getInstance(context).enqueueUniquePeriodicWork("periodic", ExistingPeriodicWorkPolicy.UPDATE, req)
        }

        /**
         * Refresh right now: from the app, a widget tap, or after signing in.
         * With [replace] false, a refresh that is already running is left alone (used by the minute timer).
         */
        fun refreshNow(context: Context, replace: Boolean = true) {
            val req = OneTimeWorkRequestBuilder<RefreshWorker>()
                .setExpedited(OutOfQuotaPolicy.RUN_AS_NON_EXPEDITED_WORK_REQUEST)
                .setConstraints(online)
                .build()
            WorkManager.getInstance(context).enqueueUniqueWork("now", if (replace) ExistingWorkPolicy.REPLACE else ExistingWorkPolicy.KEEP, req)
        }
    }
}
