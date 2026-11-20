package app.claudelimit

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import app.claudelimit.ui.ClaudeLimitTheme
import app.claudelimit.ui.Gap
import app.claudelimit.ui.HomeScreen
import app.claudelimit.ui.Bar
import app.claudelimit.ui.SettingsScreen
import app.claudelimit.work.RefreshWorker
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        Store.init(this)
        setContent {
            val s by Store.settings.collectAsState()
            val state by Store.state.collectAsState()
            ClaudeLimitTheme(s) {
                Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                    val ctx = LocalContext.current
                    var screen by rememberSaveable { mutableStateOf("home") }
                    var refreshing by remember { mutableStateOf(false) }
                    var release by remember { mutableStateOf<Updater.Release?>(null) }
                    var dismissed by rememberSaveable { mutableStateOf("") }

                    // The spinner stops when the attempt finishes (or after a minute, whatever happens).
                    LaunchedEffect(state.lastAttemptMs) { refreshing = false }
                    LaunchedEffect(refreshing) {
                        if (refreshing) {
                            delay(60_000)
                            refreshing = false
                        }
                    }
                    LaunchedEffect(Unit) { release = Updater.check() }
                    BackHandler(enabled = screen != "home") { screen = "home" }

                    val login = { startActivity(Intent(ctx, LoginActivity::class.java)) }

                    if (screen == "settings") {
                        SettingsScreen(
                            s = s,
                            state = state,
                            signedIn = state.signedIn,
                            onBack = { screen = "home" },
                            onLogin = login,
                            onUpdateFound = { r ->
                                dismissed = ""
                                release = r
                                screen = "home"
                            },
                        )
                    } else {
                        HomeScreen(
                            state = state,
                            s = s,
                            refreshing = refreshing,
                            onRefresh = {
                                refreshing = true
                                RefreshWorker.refreshNow(ctx)
                            },
                            onLogin = login,
                            onSettings = { screen = "settings" },
                        )
                    }

                    val r = release
                    if (r != null && dismissed != r.version) {
                        UpdateDialog(r, onDismiss = { dismissed = r.version })
                    }
                }
            }
        }
    }

    /** Opening the app (or coming back to it) reads fresh numbers if the last try was a while ago. */
    override fun onStart() {
        super.onStart()
        val st = Store.state.value
        if (st.signedIn && System.currentTimeMillis() - st.lastAttemptMs > 2 * 60_000L) RefreshWorker.refreshNow(this)
    }
}

@Composable
private fun UpdateDialog(r: Updater.Release, onDismiss: () -> Unit) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var progress by remember { mutableStateOf<Float?>(null) }
    var status by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = { if (progress == null) onDismiss() },
        title = { Text(tt("نسخة ${r.version} جاهزة", "Version ${r.version} is ready")) },
        text = {
            Column {
                Text(tt("في نسخة أحدث من التطبيق. أرقامك وإعداداتك بتفضل زي ما هي.", "A newer version is available. Your settings stay as they are."))
                val p = progress
                if (p != null) {
                    Gap(10)
                    Bar(p, MaterialTheme.colorScheme.primary)
                    Text(tt("بنزّل ${(p * 100).toInt()}%", "Downloading ${(p * 100).toInt()}%"))
                }
                if (status.isNotEmpty()) {
                    Gap(8)
                    Text(status)
                }
            }
        },
        confirmButton = {
            TextButton(enabled = progress == null, onClick = {
                if (Updater.canInstall(ctx)) {
                    progress = 0f
                    status = ""
                    scope.launch {
                        val ok = Updater.downloadAndInstall(ctx, r) { progress = it }
                        progress = null
                        if (!ok) status = Tx.t("التنزيل فشل. جرّب تاني.", "Download failed. Try again.")
                    }
                } else {
                    status = Tx.t("اسمح للتطبيق يثبّت التحديثات، وبعدها اضغط تحديث تاني.", "Allow the app to install updates, then tap Update again.")
                }
            }) { Text(tt("حدّث", "Update")) }
        },
        dismissButton = { TextButton(enabled = progress == null, onClick = onDismiss) { Text(tt("بعدين", "Later")) } },
    )
}
