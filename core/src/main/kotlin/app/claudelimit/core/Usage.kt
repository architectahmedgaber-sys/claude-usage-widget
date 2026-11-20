package app.claudelimit.core

import org.json.JSONObject
import java.time.OffsetDateTime

/** One limit window: how much is used (0..100) and when it resets. */
data class Limit(val usedPercent: Double, val resetsAtMs: Long?) {
    val remainingPercent: Double get() = (100.0 - usedPercent).coerceIn(0.0, 100.0)
}

/** What claude.ai reports for the account: the 5-hour window and the weekly window. */
data class Usage(
    val session: Limit?,
    val weekly: Limit?,
    val plan: String,
    val fetchedAtMs: Long,
) {
    /** True when the plan reports any percentage at all (Free accounts report none). */
    val hasData: Boolean get() = session != null || weekly != null

    /**
     * The 5-hour window as it looks at [nowMs]. claude.ai leaves it out when no window is running,
     * and once its reset time has passed a new window starts empty.
     */
    fun sessionAt(nowMs: Long): Limit? {
        val s = session ?: return if (weekly != null) Limit(0.0, null) else null
        val r = s.resetsAtMs
        return if (r != null && r <= nowMs) Limit(0.0, null) else s
    }
}

object UsageParser {
    /** Reads the body of GET /api/organizations/{id}/usage. Understands the newer "limits" list and the older fixed keys. */
    fun parse(body: String, fetchedAtMs: Long, plan: String = ""): Usage {
        val o = JSONObject(body)
        var session: Limit? = null
        var weekly: Limit? = null
        val arr = o.optJSONArray("limits")
        if (arr != null) {
            for (i in 0 until arr.length()) {
                val e = arr.optJSONObject(i) ?: continue
                // Per-model limits carry a scope; only the account-wide ones matter here.
                if (!e.isNull("scope") && e.optString("scope").isNotEmpty()) continue
                val lim = limit(e.optDouble("percent", Double.NaN), e.optString("resets_at", "")) ?: continue
                when (e.optString("kind")) {
                    "session" -> if (session == null) session = lim
                    "weekly_all" -> if (weekly == null) weekly = lim
                }
            }
        }
        if (session == null) session = legacy(o.optJSONObject("five_hour"))
        if (weekly == null) weekly = legacy(o.optJSONObject("seven_day"))
        return Usage(session, weekly, plan, fetchedAtMs)
    }

    private fun legacy(o: JSONObject?): Limit? {
        if (o == null) return null
        return limit(o.optDouble("utilization", Double.NaN), o.optString("resets_at", ""))
    }

    private fun limit(percent: Double, resets: String): Limit? {
        if (percent.isNaN()) return null
        return Limit(percent.coerceIn(0.0, 100.0), parseTime(resets))
    }

    fun parseTime(s: String): Long? {
        if (s.isBlank()) return null
        return runCatching { OffsetDateTime.parse(s).toInstant().toEpochMilli() }.getOrNull()
    }
}
