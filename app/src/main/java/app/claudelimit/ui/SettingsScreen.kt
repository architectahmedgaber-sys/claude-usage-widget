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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import app.claudelimit.BuildConfig
import app.claudelimit.Settings
import app.claudelimit.Store
import app.claudelimit.StoreState
import app.claudelimit.Tx
import app.claudelimit.Updater
import app.claudelimit.tt
import app.claudelimit.Palettes
import app.claudelimit.WidgetStyles
import app.claudelimit.widget.Kind
import app.claudelimit.widget.Widgets
import app.claudelimit.work.Ticker
import kotlinx.coroutines.launch

@Composable
fun SettingsScreen(s: Settings, state: StoreState, signedIn: Boolean, onBack: () -> Unit, onLogin: () -> Unit, onUpdateFound: (Updater.Release) -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var updateStatus by remember { mutableStateOf("") }

    /** Saves a choice and redraws the widgets so the change shows at once. */
    fun change(t: (Settings) -> Settings) {
        Store.update(context, t)
        scope.launch { Widgets.updateAll(context) }
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
    ) { pad ->
        Column(
            Modifier.fillMaxSize().padding(pad).padding(16.dp).verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = tt("رجوع", "Back")) }
                Text(tt("الإعدادات", "Settings"), fontSize = 20.sp, fontWeight = FontWeight.Bold)
            }
            WidgetLookCard(s, state, ::change)

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
                    tt(
                        "كل قد إيه التطبيق يقرا الأرقام في الخلفية.",
                        "How often the app reads the numbers in the background.",
                    ),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Gap(8)
                Choices(
                    listOf(1, 2, 5, 10, 15, 30, 60), s.refreshMinutes,
                    { m -> if (m == 60) tt("ساعة", "1 hour") else tt("$m دقيقة", "$m min") },
                ) { m ->
                    change { it.copy(refreshMinutes = m) }
                    Ticker.apply(context, m)
                }
                Gap(8)
                Text(
                    if (s.refreshMinutes < 15) {
                        tt(
                            "أقل من 15 دقيقة بيشتغل بمنبه خفيف لما الموبايل يكون صاحي، ومبيصحّيش موبايل نايم. بيستهلك بطارية أكتر، وكل ما قلّت المدة بنكلّم claude.ai أكتر. الأفضل 5 دقايق.",
                            "Under 15 minutes runs on a light timer while the phone is awake; it does not wake a sleeping phone. It uses more battery and asks claude.ai more often. 5 minutes is a good choice.",
                        )
                    } else {
                        tt("15 دقيقة فأكتر بيشتغل بنظام أندرويد العادي وبيوفّر البطارية.", "15 minutes or more uses Android's normal scheduler and saves battery.")
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            SectionCard {
                Label(tt("اللغة", "Language"))
                Gap(8)
                Choices(listOf("AR", "EN"), s.lang, { if (it == "AR") "العربية" else "English" }) { v -> change { it.copy(lang = v) } }
            }

            SectionCard {
                Label(tt("الويدجتس", "Widgets"))
                Gap(4)
                Text(
                    tt(
                        "اضغط مطوّل على الشاشة الرئيسية ← الويدجتس ← ليميت كلود، واختار اللي تحتاجه: الخمس ساعات، الأسبوع، الخطة، أو الكل في ويدجت. صغّر الويدجت أو كبّرها براحتك. الشريط اللي فوق في الويدجت الكبيرة بيحدّثها.",
                        "Long-press the home screen, open Widgets, find Claude Limit and pick what you need: 5 hours, Weekly, Plan, or All in one. Resize them freely. The top strip of a larger widget refreshes it.",
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
                    updateStatus = Tx.t("بدوّر...", "Checking...")
                    scope.launch {
                        val r = Updater.check()
                        if (r != null) {
                            updateStatus = ""
                            onUpdateFound(r)
                        } else {
                            updateStatus = Tx.t("انت على آخر نسخة.", "You have the latest version.")
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


@Composable
private fun styleName(n: String): String = when (n) {
    "RINGS" -> tt("دواير", "Rings")
    "BATTERIES" -> tt("بطاريات", "Batteries")
    "COLUMNS" -> tt("أعمدة", "Columns")
    "BARS" -> tt("شرائط", "Bars")
    "GAUGES" -> tt("عدادات", "Gauges")
    else -> tt("خانات", "Cells")
}

@Composable
private fun paletteName(n: String): String = when (n) {
    "AMBER" -> tt("كهرماني", "Amber")
    "TEAL" -> tt("فيروزي", "Teal")
    "VIOLET" -> tt("بنفسجي", "Violet")
    else -> tt("ورقي", "Paper")
}

/** The live preview and the choices for how the widgets look. */
@Composable
private fun WidgetLookCard(s: Settings, state: StoreState, change: ((Settings) -> Settings) -> Unit) {
    SectionCard {
        Label(tt("شكل الويدجتس", "Widget look"))
        Gap(4)
        Text(
            tt(
                "العلامة البيضا الصغيرة هي المفروض تكون فين النهارده حسب الخطة الأسبوعية.",
                "The small white tick marks where you should be by today, following the weekly plan.",
            ),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Gap(10)
        WidgetPreview(Kind.ALL, s, state, 320f, 140f, Modifier.fillMaxWidth())
        Gap(8)
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            WidgetPreview(Kind.SESSION, s, state, 110f, 110f, Modifier.weight(1f))
            WidgetPreview(Kind.WEEK, s, state, 110f, 110f, Modifier.weight(1f))
            WidgetPreview(Kind.PACE, s, state, 110f, 110f, Modifier.weight(1f))
        }
        Gap(14)
        Text(tt("الخمس ساعات", "5 hours"))
        Gap(6)
        Choices(WidgetStyles.names, s.styleSession, { styleName(it) }) { v -> change { it.copy(styleSession = v) } }
        Gap(12)
        Text(tt("الأسبوع", "Weekly"))
        Gap(6)
        Choices(WidgetStyles.names, s.styleWeek, { styleName(it) }) { v -> change { it.copy(styleWeek = v) } }
        Gap(12)
        Text(tt("الخطة", "Plan"))
        Gap(6)
        Choices(WidgetStyles.names, s.stylePace, { styleName(it) }) { v -> change { it.copy(stylePace = v) } }
        Gap(12)
        Text(tt("الكل في ويدجت", "All in one"))
        Gap(6)
        Choices(WidgetStyles.names, s.styleAll, { styleName(it) }) { v -> change { it.copy(styleAll = v) } }
        Gap(14)
        Text(tt("الألوان", "Colours"))
        Gap(6)
        Choices(Palettes.names, s.palette, { paletteName(it) }) { v -> change { it.copy(palette = v) } }
        Gap(12)
        Text(tt("خلفية الويدجت", "Widget background"))
        Gap(6)
        Choices(listOf(100, 75, 50, 25, 0), s.bgAlpha, { if (it == 0) tt("شفاف", "Clear") else "$it%" }) { v -> change { it.copy(bgAlpha = v) } }
    }
}
