package app.claudelimit

import android.content.Context
import app.claudelimit.core.Usage
import app.claudelimit.core.UsageParser
import kotlinx.coroutines.flow.MutableStateFlow

/** What the person chose to see. Everything is on by default except "used instead of left". */
data class Settings(
    val lang: String = "AR",
    /** One of Palettes.names. The app and the widgets both follow it. */
    val palette: String = "AMBER",
    /** How opaque the widget background is: 100 solid, 0 see-through. */
    val bgAlpha: Int = 100,
    /** One of WidgetStyles.names, chosen per widget. */
    val styleSession: String = "RINGS",
    val styleWeek: String = "BATTERIES",
    val stylePace: String = "BARS",
    val styleAll: String = "BARS",
    val showSession: Boolean = true,
    val showWeek: Boolean = true,
    val showPace: Boolean = true,
    val showReset: Boolean = true,
    val showUpdated: Boolean = true,
    val showUsed: Boolean = false,
    val refreshMinutes: Int = 15,
)

/** The last numbers read from claude.ai, and how the last attempt went. */
data class StoreState(
    val body: String? = null,
    val plan: String = "",
    val fetchedAtMs: Long = 0L,
    /** null, "SESSION", "NETWORK" or "OTHER:details" */
    val error: String? = null,
    val signedIn: Boolean = false,
    val lastAttemptMs: Long = 0L,
    val org: String? = null,
) {
    fun usage(): Usage? = body?.let { runCatching { UsageParser.parse(it, fetchedAtMs, plan) }.getOrNull() }
}

/** Settings and the last snapshot, kept in app-private storage (never backed up). */
object Store {
    private const val FILE = "claude_limit"
    private var inited = false

    val settings = MutableStateFlow(Settings())
    val state = MutableStateFlow(StoreState())

    private fun sp(c: Context) = c.applicationContext.getSharedPreferences(FILE, Context.MODE_PRIVATE)

    @Synchronized
    fun init(c: Context) {
        if (inited) return
        inited = true
        reload(c)
    }

    fun reload(c: Context) {
        val p = sp(c)
        val d = Settings()
        settings.value = Settings(
            lang = p.getString("lang", d.lang) ?: d.lang,
            palette = p.getString("palette", d.palette) ?: d.palette,
            bgAlpha = p.getInt("bgAlpha", d.bgAlpha),
            styleSession = p.getString("styleSession", d.styleSession) ?: d.styleSession,
            styleWeek = p.getString("styleWeek", d.styleWeek) ?: d.styleWeek,
            stylePace = p.getString("stylePace", d.stylePace) ?: d.stylePace,
            styleAll = p.getString("styleAll", d.styleAll) ?: d.styleAll,
            showSession = p.getBoolean("showSession", d.showSession),
            showWeek = p.getBoolean("showWeek", d.showWeek),
            showPace = p.getBoolean("showPace", d.showPace),
            showReset = p.getBoolean("showReset", d.showReset),
            showUpdated = p.getBoolean("showUpdated", d.showUpdated),
            showUsed = p.getBoolean("showUsed", d.showUsed),
            refreshMinutes = p.getInt("refreshMinutes", d.refreshMinutes),
        )
        state.value = StoreState(
            body = p.getString("body", null),
            plan = p.getString("plan", "") ?: "",
            fetchedAtMs = p.getLong("fetchedAt", 0L),
            error = p.getString("error", null),
            signedIn = p.getBoolean("signedIn", false),
            lastAttemptMs = p.getLong("lastAttempt", 0L),
            org = p.getString("org", null),
        )
    }

    fun update(c: Context, change: (Settings) -> Settings) {
        val s = change(settings.value)
        settings.value = s
        sp(c).edit()
            .putString("lang", s.lang)
            .putString("palette", s.palette)
            .putInt("bgAlpha", s.bgAlpha)
            .putString("styleSession", s.styleSession)
            .putString("styleWeek", s.styleWeek)
            .putString("stylePace", s.stylePace)
            .putString("styleAll", s.styleAll)
            .putBoolean("showSession", s.showSession)
            .putBoolean("showWeek", s.showWeek)
            .putBoolean("showPace", s.showPace)
            .putBoolean("showReset", s.showReset)
            .putBoolean("showUpdated", s.showUpdated)
            .putBoolean("showUsed", s.showUsed)
            .putInt("refreshMinutes", s.refreshMinutes)
            .apply()
    }

    fun saveUsage(c: Context, body: String, plan: String, org: String?) {
        val now = System.currentTimeMillis()
        val e = sp(c).edit()
            .putString("body", body)
            .putString("plan", plan)
            .putLong("fetchedAt", now)
            .putLong("lastAttempt", now)
            .remove("error")
        if (org != null) e.putString("org", org)
        e.apply()
        state.value = state.value.copy(
            body = body, plan = plan, fetchedAtMs = now, lastAttemptMs = now, error = null,
            org = org ?: state.value.org,
        )
    }

    fun saveError(c: Context, error: String) {
        val now = System.currentTimeMillis()
        sp(c).edit().putString("error", error).putLong("lastAttempt", now).apply()
        state.value = state.value.copy(error = error, lastAttemptMs = now)
    }

    fun setSignedIn(c: Context, value: Boolean) {
        sp(c).edit().putBoolean("signedIn", value).apply()
        state.value = state.value.copy(signedIn = value, error = if (value) null else state.value.error)
        if (value) sp(c).edit().remove("error").apply()
    }

    /** Forgets the numbers and the account. Settings stay. */
    fun signOut(c: Context) {
        sp(c).edit().remove("body").remove("plan").remove("fetchedAt").remove("error")
            .remove("lastAttempt").remove("org").putBoolean("signedIn", false).apply()
        state.value = StoreState()
    }

    /** "Pro", "Max 5x"... guessed from the account's capabilities; empty when unknown. */
    fun planLabel(raw: String): String {
        val s = raw.lowercase()
        return when {
            "max_20x" in s -> "Max 20x"
            "max_5x" in s -> "Max 5x"
            "max" in s -> "Max"
            "enterprise" in s -> "Enterprise"
            "team" in s -> "Team"
            "pro" in s -> "Pro"
            else -> ""
        }
    }
}
