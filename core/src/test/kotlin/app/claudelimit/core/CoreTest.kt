package app.claudelimit.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CoreTest {
    private val newFormat = """
    {
      "five_hour": {"utilization": 38.0, "resets_at": "2026-11-04T20:49:59.750291+00:00"},
      "seven_day": {"utilization": 59.0, "resets_at": "2026-11-07T03:59:59.750312+00:00"},
      "seven_day_opus": null,
      "limits": [
        {"kind": "session", "percent": 40, "resets_at": "2026-11-04T20:49:59.750291+00:00", "scope": null},
        {"kind": "weekly_all", "percent": 61, "resets_at": "2026-11-07T03:59:59.750312+00:00", "scope": null},
        {"kind": "weekly_opus", "percent": 99, "resets_at": "2026-11-07T03:59:59.750312+00:00", "scope": "opus"}
      ]
    }""".trimIndent()

    @Test fun readsLimitsList() {
        val u = UsageParser.parse(newFormat, 0L)
        assertEquals(40.0, u.session!!.usedPercent, 0.001)
        assertEquals(61.0, u.weekly!!.usedPercent, 0.001)
        assertEquals(39.0, u.weekly!!.remainingPercent, 0.001)
        assertNotNull(u.weekly!!.resetsAtMs)
        assertTrue(u.hasData)
    }

    @Test fun readsLegacyKeys() {
        val u = UsageParser.parse("""{"five_hour":{"utilization":10.0,"resets_at":"2026-11-04T20:49:59+00:00"},"seven_day":{"utilization":20.5,"resets_at":null}}""", 0L)
        assertEquals(10.0, u.session!!.usedPercent, 0.001)
        assertEquals(20.5, u.weekly!!.usedPercent, 0.001)
        assertNull(u.weekly!!.resetsAtMs)
    }

    @Test fun freePlanHasNoData() {
        val u = UsageParser.parse("""{"five_hour":null,"seven_day":null,"limits":[]}""", 0L)
        assertFalse(u.hasData)
        assertNull(u.sessionAt(0L))
    }

    @Test fun missingSessionMeansEmptyWindow() {
        val u = UsageParser.parse("""{"five_hour":null,"seven_day":{"utilization":5.0,"resets_at":"2026-11-07T03:59:59+00:00"}}""", 0L)
        assertEquals(0.0, u.sessionAt(0L)!!.usedPercent, 0.001)
    }

    @Test fun passedResetStartsEmptyWindow() {
        val reset = UsageParser.parseTime("2026-11-04T20:49:59+00:00")!!
        val u = Usage(Limit(80.0, reset), Limit(10.0, null), "", 0L)
        assertEquals(80.0, u.sessionAt(reset - 1)!!.usedPercent, 0.001)
        assertEquals(0.0, u.sessionAt(reset + 1)!!.usedPercent, 0.001)
    }

    @Test fun paceHalfwayThroughWeek() {
        val now = 1_000_000_000_000L
        val reset = now + PaceCalc.WEEK_MS / 2
        val p = PaceCalc.compute(Limit(60.0, reset), now)!!
        assertEquals(50.0, p.expectedUsed, 0.001)
        assertEquals(10.0, p.diff, 0.001)
        assertEquals(PaceStatus.OVER, p.status)
        assertEquals(4, p.dayNumber)
        assertEquals(40.0 / 3.5, p.safePerDay, 0.001)
    }

    @Test fun paceUnderAndOnTrack() {
        val now = 1_000_000_000_000L
        val reset = now + PaceCalc.WEEK_MS / 2
        assertEquals(PaceStatus.UNDER, PaceCalc.compute(Limit(30.0, reset), now)!!.status)
        assertEquals(PaceStatus.ON_TRACK, PaceCalc.compute(Limit(51.0, reset), now)!!.status)
    }

    @Test fun paceNeedsFutureReset() {
        val now = 1_000_000_000_000L
        assertNull(PaceCalc.compute(Limit(10.0, null), now))
        assertNull(PaceCalc.compute(Limit(10.0, now - 1), now))
    }

    @Test fun paceFirstAndLastDay() {
        val now = 1_000_000_000_000L
        assertEquals(1, PaceCalc.compute(Limit(0.0, now + PaceCalc.WEEK_MS - 1000), now)!!.dayNumber)
        assertEquals(7, PaceCalc.compute(Limit(0.0, now + 1000), now)!!.dayNumber)
    }

    @Test fun timeText() {
        assertEquals("3 س 20 د", TimeText.left((3 * 60 + 20) * 60_000L, true))
        assertEquals("2d 4h", TimeText.left((2 * 1440 + 4 * 60) * 60_000L, false))
        assertEquals("12m", TimeText.left(12 * 60_000L, false))
        assertEquals("just now", TimeText.ago(10_000L, false))
        assertEquals("قبل 5 د", TimeText.ago(5 * 60_000L, true))
    }
}
