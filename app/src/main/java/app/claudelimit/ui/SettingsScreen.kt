package app.claudelimit.ui

import android.webkit.CookieManager
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import app.claudelimit.BuildConfig
import app.claudelimit.Settings
import app.claudelimit.Store
import app.claudelimit.Updater
import app.claudelimit.tt
import app.claudelimit.widget.Widgets
import app.claudelimit.work.RefreshWorker
import kotlinx.coroutines.launch

@Composable
fun SettingsScreen(s: Settings, signedIn: Boolean, onBack: () -> Unit, onLogin: () -> Unit, onUpdateFound: (Updater.Release) -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var updateStatus by remember { mutableStateOf("") }

    /** Saves a choice and redraws the widgets so the change shows at once. */
    fun change(t: (Settings) -> Settings) {
        Store.update(context, t)
        scope.launch { Widgets.updateAll(context) }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(tt("الإعدادات", "Settings")) },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = tt("رجوع", "Back")) }
                },
            )
        },
    ) { pad ->
        Column(
            Modifier.fillMaxSize().padding(pad).padding(16.dp).verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            SectionCard {
                Label(tt("اللي يظهر", "What shows"))
                Text(
                    tt(
                        "ده بيأثر على الشاشة الرئيسية للتطبيق وعلى ويدجت \"الكل في ويدجت\". والويدجتس المنفصلة تقدر تضيف منها اللي تحتاجه بس.",
                        "This applies to the app's home screen and the \"All in one\" widget. The separate widgets can be added one by one.",
                    ),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Toggle(tt("الخمس ساعات", "5-hour limit"), s.showSession) { v -> change { it.copy(showSession = v) } }
                Toggle(tt("الأسبوع", "Weekly limit"), s.showWeek) { v -> change { it.copy(showWeek = v) } }
                Toggle(
                    tt("الخطة الأسبوعية", "Weekly plan"), s.showPace,
                    tt("المفروض تكون خلصت قد إيه النهارده", "How much you should have used by today"),
                ) { v -> change { it.copy(showPace = v) } }
                Toggle(tt("وقت التصفير", "Time until reset"), s.showReset) { v -> change { it.copy(showReset = v) } }
                Toggle(tt("آخر تحديث", "Last updated"), s.showUpdated) { v -> change { it.copy(showUpdated = v) } }
                Toggle(
                    tt("اعرض المستخدم بدل الفاضل", "Show used instead of left"), s.showUsed,
                    tt("الافتراضي إنه يعرض الفاضل", "By default it shows what is left"),
                ) { v -> change { it.copy(showUsed = v) } }
            }

            SectionCard {
                Label(tt("التحديث", "Refresh"))
                Text(
                    tt("كل قد إيه التطبيق يقرا الأرقام في الخلفية. أندرويد مبيسمحش بأقل من 15 دقيقة.", "How often the app reads the numbers in the background. Android allows 15 minutes at the fastest."),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Gap(8)
                Choices(
                    listOf(15, 30, 60), s.refreshMinutes,
                    { m -> if (m == 60) tt("ساعة", "1 hour") else tt("$m دقيقة", "$m min") },
                ) { m ->
                    change { it.copy(refreshMinutes = m) }
                    RefreshWorker.schedule(context, m)
                }
            }

            SectionCard {
                Label(tt("الشكل", "Look"))
                Gap(4)
                Text(tt("اللغة", "Language"))
                Gap(4)
                Choices(listOf("AR", "EN"), s.lang, { if (it == "AR") "العربية" else "English" }) { v -> change { it.copy(lang = v) } }
                Gap(10)
                Text(tt("الثيم", "Theme"))
                Gap(4)
                Choices(
                    listOf("AUTO", "LIGHT", "DARK"), s.theme,
                    { t -> when (t) { "LIGHT" -> tt("فاتح", "Light"); "DARK" -> tt("غامق", "Dark"); else -> tt("زي الموبايل", "Phone default") } },
                ) { v -> change { it.copy(theme = v) } }
            }

            SectionCard {
                Label(tt("الويدجتس", "Widgets"))
                Gap(4)
                Text(
                    tt(
                        "اضغط مطوّل على الشاشة الرئيسية ← الويدجتس ← ليميت كلود، واختار اللي تحتاجه: الخمس ساعات، الأسبوع، الخطة، أو الكل في ويدجت. الزرار ↻ في ركن الويدجت بيحدّثها.",
                        "Long-press the home screen, open Widgets, find Claude Limit and pick what you need: 5 hours, Weekly, Plan, or All in one. The ↻ in a widget's corner refreshes it.",
                    )
                )
            }

            SectionCard {
                Label(tt("الحساب", "Account"))
                Gap(8)
                if (signedIn) {
                    OutlinedButton(onClick = {
                        CookieManager.getInstance().removeAllCookies(null)
                        CookieManager.getInstance().flush()
                        Store.signOut(context)
                        scope.launch { Widgets.updateAll(context) }
                    }) { Text(tt("سجّل خروج وامسح الأرقام", "Sign out and clear the numbers")) }
                } else {
                    Button(onClick = onLogin) { Text(tt("سجّل دخولك", "Sign in")) }
                }
            }

            SectionCard {
                Label(tt("عن التطبيق", "About"))
                Gap(4)
                Text(tt("النسخة ${BuildConfig.VERSION_NAME}", "Version ${BuildConfig.VERSION_NAME}"))
                Gap(4)
                Text(
                    tt(
                        "غير رسمي ومش تابع لـ Anthropic. بيقرا من واجهة غير موثّقة في claude.ai، فممكن يقف لو اتغيّرت.",
                        "Unofficial and not affiliated with Anthropic. It reads an undocumented claude.ai interface, so it may stop if that changes.",
                    ),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Gap(10)
                OutlinedButton(onClick = {
                    updateStatus = tt("بدوّر...", "Checking...")
                    scope.launch {
                        val r = Updater.check()
                        if (r != null) {
                            updateStatus = ""
                            onUpdateFound(r)
                        } else {
                            updateStatus = tt("انت على آخر نسخة.", "You have the latest version.")
                        }
                    }
                }) { Text(tt("شوف لو في تحديث", "Check for updates")) }
                if (updateStatus.isNotEmpty()) {
                    Gap(6)
                    Text(updateStatus, style = MaterialTheme.typography.bodySmall)
                }
            }
        }
    }
}
