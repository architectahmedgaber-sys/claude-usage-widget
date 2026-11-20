package app.claudelimit.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import app.claudelimit.Settings
import app.claudelimit.Store
import app.claudelimit.StoreState
import app.claudelimit.Tx
import app.claudelimit.core.Limit
import app.claudelimit.core.Pace
import app.claudelimit.core.PaceCalc
import app.claudelimit.core.PaceStatus
import app.claudelimit.core.TimeText
import app.claudelimit.tt
import kotlinx.coroutines.delay
import kotlin.math.abs
import kotlin.math.roundToInt

@Composable
fun HomeScreen(
    state: StoreState,
    s: Settings,
    refreshing: Boolean,
    onRefresh: () -> Unit,
    onLogin: () -> Unit,
    onSettings: () -> Unit,
) {
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) {
        while (true) {
            delay(30_000)
            now = System.currentTimeMillis()
        }
    }
    val usage = remember(state.body, state.fetchedAtMs, state.plan) { state.usage() }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(tt("ليميت كلود", "Claude Limit")) },
                actions = { TextButton(onClick = onSettings) { Text(tt("الإعدادات", "Settings")) } },
            )
        },
    ) { pad ->
        Column(
            Modifier.fillMaxSize().padding(pad).padding(16.dp).verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            if (!state.signedIn && usage == null) {
                WelcomeCard(onLogin)
            } else {
                if (!state.signedIn) SessionExpiredCard(onLogin)
                if (usage == null) {
                    SectionCard { Text(tt("بجيب الأرقام من Claude...", "Reading your numbers from Claude...")) }
                } else if (!usage.hasData) {
                    SectionCard {
                        Label(tt("مفيش نسب", "No figures"))
                        Gap(6)
                        Text(
                            tt(
                                "الخطة بتاعتك مبتعرضش نسب استخدام. النسب بتظهر لمشتركي Pro وMax.",
                                "Your plan shows no usage percentages. They appear for Pro and Max subscribers.",
                            )
                        )
                    }
                } else {
                    val session = usage.sessionAt(now)
                    val weekly = usage.weekly
                    val pace = weekly?.let { PaceCalc.compute(it, now) }
                    if (s.showSession && session != null) LimitCard(tt("الخمس ساعات", "5-hour limit"), session, now, s)
                    if (s.showWeek && weekly != null) LimitCard(tt("الأسبوع", "Weekly limit"), weekly, now, s)
                    if (s.showPace && pace != null) PaceCard(pace)
                    if (!s.showSession && !s.showWeek && !s.showPace) {
                        SectionCard {
                            Text(tt("مفيش حاجة مختارة تظهر. اختار من الإعدادات.", "Nothing is chosen to show. Pick from Settings."))
                        }
                    }
                }
                FooterCard(state, s, now, refreshing, onRefresh, onLogin)
            }
        }
    }
}

@Composable
private fun WelcomeCard(onLogin: () -> Unit) {
    SectionCard {
        Label(tt("ابدأ من هنا", "Get started"))
        Gap(8)
        Text(
            tt(
                "سجّل دخولك مرة واحدة بحساب Claude، وبعدها التطبيق يقولك فاضل قد إيه في الخمس ساعات والأسبوع، وتقدر تحط ويدجت على الشاشة.",
                "Sign in once with your Claude account. The app then shows what is left of your 5-hour and weekly limits, and you can add widgets to your home screen.",
            )
        )
        Gap(12)
        Button(onClick = onLogin, modifier = Modifier.fillMaxWidth()) { Text(tt("سجّل دخولك على Claude", "Sign in to Claude")) }
        Gap(8)
        Text(
            tt(
                "التطبيق بيقرا من صفحة الاستخدام بتاعتك على claude.ai بس، ومبيبعتش حاجة لأي مكان تاني.",
                "It only reads your usage page on claude.ai and sends nothing anywhere else.",
            ),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun SessionExpiredCard(onLogin: () -> Unit) {
    SectionCard {
        Label(tt("الجلسة خلصت", "Signed out"))
        Gap(6)
        Text(tt("سجّل دخولك تاني عشان الأرقام تتحدّث.", "Sign in again so the numbers update."))
        Gap(10)
        Button(onClick = onLogin) { Text(tt("سجّل دخول", "Sign in")) }
    }
}

@Composable
private fun LimitCard(title: String, limit: Limit, now: Long, s: Settings) {
    val shown = if (s.showUsed) limit.usedPercent else limit.remainingPercent
    val color = levelColor(limit.usedPercent)
    SectionCard {
        Label(title)
        Gap(4)
        Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Big("${shown.roundToInt()}%", color)
            Text(
                if (s.showUsed) tt("مستخدم", "used") else tt("فاضل", "left"),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(bottom = 6.dp),
            )
        }
        Gap(8)
        Bar((shown / 100.0).toFloat(), color)
        val r = limit.resetsAtMs
        if (s.showReset && r != null) {
            Gap(8)
            Text(
                tt("بتتصفّر بعد ${TimeText.left(r - now, true)}", "Resets in ${TimeText.left(r - now, false)}"),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun PaceCard(p: Pace) {
    val color = when {
        p.status != PaceStatus.OVER -> levelColor(0.0)
        p.diff > 15.0 -> levelColor(90.0)
        else -> levelColor(60.0)
    }
    val n = abs(p.diff).roundToInt()
    val status = when (p.status) {
        PaceStatus.OVER -> tt("أعلى من المفروض بـ $n%", "$n% ahead of the plan")
        PaceStatus.UNDER -> tt("أقل من المفروض بـ $n%", "$n% under the plan")
        PaceStatus.ON_TRACK -> tt("ماشي مظبوط", "Right on plan")
    }
    SectionCard {
        Label(tt("الخطة الأسبوعية · يوم ${p.dayNumber} من 7", "Weekly plan · day ${p.dayNumber} of 7"))
        Gap(8)
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Column(Modifier.weight(1f)) {
                Text(tt("المفروض تكون خلصت", "You should have used"), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Big("${p.expectedUsed.roundToInt()}%", size = 28)
            }
            Column(Modifier.weight(1f)) {
                Text(tt("انت خلصت", "You have used"), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Big("${p.actualUsed.roundToInt()}%", color, 28)
            }
        }
        Gap(10)
        PaceBar((p.actualUsed / 100.0).toFloat(), (p.expectedUsed / 100.0).toFloat(), color)
        Gap(10)
        Pill(status, color)
        Gap(8)
        Text(
            tt(
                "تقدر تستهلك ${p.safePerDay.roundToInt()}% في اليوم لحد ما الأسبوع يتصفّر.",
                "You can use ${p.safePerDay.roundToInt()}% a day until the week resets.",
            ),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun errorText(e: String): String = when {
    e == "SESSION" -> tt("الجلسة انتهت، سجّل دخولك تاني.", "Your session ended. Sign in again.")
    e == "NETWORK" -> tt("مفيش اتصال، أو الموقع مش بيرد.", "No connection, or the site is not answering.")
    else -> tt("حصلت مشكلة في القراءة (${e.removePrefix("OTHER:")}).", "Could not read the numbers (${e.removePrefix("OTHER:")}).")
}

@Composable
private fun FooterCard(state: StoreState, s: Settings, now: Long, refreshing: Boolean, onRefresh: () -> Unit, onLogin: () -> Unit) {
    SectionCard {
        val plan = Store.planLabel(state.plan)
        if (s.showUpdated && state.fetchedAtMs > 0L) {
            Text(
                tt("آخر تحديث: ", "Last updated: ") + TimeText.ago(now - state.fetchedAtMs, Tx.ar) + if (plan.isNotEmpty()) "  ·  $plan" else "",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        } else if (plan.isNotEmpty()) {
            Text(plan, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        val e = state.error
        if (e != null) {
            Gap(6)
            Text(errorText(e), color = levelColor(90.0), style = MaterialTheme.typography.bodyMedium)
        }
        Gap(10)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = onRefresh, enabled = !refreshing) {
                Text(if (refreshing) tt("بحدّث...", "Updating...") else tt("حدّث دلوقتي", "Refresh now"))
            }
            if (e == "SESSION" || !state.signedIn) {
                OutlinedButton(onClick = onLogin) { Text(tt("سجّل دخول", "Sign in")) }
            }
        }
    }
}
