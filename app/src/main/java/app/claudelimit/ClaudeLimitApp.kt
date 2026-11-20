package app.claudelimit

import android.app.Application
import app.claudelimit.work.RefreshWorker
import app.claudelimit.work.Ticker

class ClaudeLimitApp : Application() {
    override fun onCreate() {
        super.onCreate()
        Store.init(this)
        Tx.ar = Store.settings.value.lang == "AR"
        Ticker.apply(this, Store.settings.value.refreshMinutes)
    }
}
