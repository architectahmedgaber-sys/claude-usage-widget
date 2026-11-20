package app.claudelimit.core

/** UNDER = using less than the even plan, OVER = using more than it. */
enum class PaceStatus { UNDER, ON_TRACK, OVER }

data class Pace(
    /** How much of the week you should have used by now if you spread it evenly (0..100). */
    val expectedUsed: Double,
    val actualUsed: Double,
    /** actual minus expected: positive means you are ahead of the plan (burning faster). */
    val diff: Double,
    val status: PaceStatus,
    /** 1..7: which day of the weekly window it is. */
    val dayNumber: Int,
    val daysLeft: Double,
    /** What you can still use per remaining day to finish exactly at the reset. */
    val safePerDay: Double,
)

object PaceCalc {
    const val DAY_MS = 24L * 60 * 60 * 1000
    const val WEEK_MS = 7 * DAY_MS

    /** The week starts 7 days before the weekly reset. Returns null when the reset time is unknown or already passed. */
    fun compute(weekly: Limit, nowMs: Long, tolerance: Double = 3.0): Pace? {
        val reset = weekly.resetsAtMs ?: return null
        if (reset <= nowMs) return null
        val start = reset - WEEK_MS
        val elapsed = ((nowMs - start).toDouble() / WEEK_MS).coerceIn(0.0, 1.0)
        val expected = elapsed * 100.0
        val diff = weekly.usedPercent - expected
        val status = when {
            diff > tolerance -> PaceStatus.OVER
            diff < -tolerance -> PaceStatus.UNDER
            else -> PaceStatus.ON_TRACK
        }
        val day = (elapsed * 7).toInt().coerceAtMost(6) + 1
        val daysLeft = (reset - nowMs).toDouble() / DAY_MS
        val perDay = if (daysLeft > 0) weekly.remainingPercent / daysLeft else 0.0
        return Pace(expected, weekly.usedPercent, diff, status, day, daysLeft, perDay)
    }
}

object TimeText {
    /** "2 يوم 4 س", "3 س 20 د", "12 د" (or 2d 4h, 3h 20m, 12m). */
    fun left(ms: Long, ar: Boolean): String {
        val mins = (if (ms < 0) 0L else ms) / 60_000
        val d = mins / 1440
        val h = (mins % 1440) / 60
        val m = mins % 60
        return if (ar) when {
            d > 0 -> "$d يوم $h س"
            h > 0 -> "$h س $m د"
            else -> "$m د"
        } else when {
            d > 0 -> "${d}d ${h}h"
            h > 0 -> "${h}h ${m}m"
            else -> "${m}m"
        }
    }

    /** "دلوقتي", "قبل 5 د", "قبل 3 س" (or now, 5m ago, 3h ago). */
    fun ago(ms: Long, ar: Boolean): String {
        val mins = (if (ms < 0) 0L else ms) / 60_000
        return when {
            mins < 1 -> if (ar) "دلوقتي" else "just now"
            mins < 60 -> if (ar) "قبل $mins د" else "${mins}m ago"
            mins < 1440 -> if (ar) "قبل ${mins / 60} س" else "${mins / 60}h ago"
            else -> if (ar) "قبل ${mins / 1440} يوم" else "${mins / 1440}d ago"
        }
    }
}
