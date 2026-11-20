package app.claudelimit

import android.app.Application
import app.claudelimit.work.RefreshWorker

class ClaudeLimitApp : Application() {
    override fun onCreate() {
        super.onCreate()
        Store.init(this)
        Tx.ar = Store.settings.value.lang == "AR"
        RefreshWorker.schedule(this, Store.settings.value.refreshMinutes)
    }
}
