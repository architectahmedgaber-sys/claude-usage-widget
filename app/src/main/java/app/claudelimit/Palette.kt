package app.claudelimit

/** One colour set, used by both the app and the widgets so they always match. Colours are ARGB ints. */
class Pal(
    val name: String,
    val dark: Boolean,
    val bg: Int,
    val ink: Int,
    val sub: Int,
    val track: Int,
    val good: Int,
    val warn: Int,
    val bad: Int,
    /** Text colour on top of a filled shape. */
    val onFill: Int,
    /** The little tick that shows where you should be by now. */
    val marker: Int,
)

object Palettes {
    val names = listOf("AMBER", "TEAL", "VIOLET", "PAPER")

    private val amber = Pal(
        "AMBER", true,
        bg = 0xFF17191D.toInt(), ink = 0xFFEDE9E0.toInt(), sub = 0xFF8B8F98.toInt(), track = 0xFF2C2F36.toInt(),
        good = 0xFFFFB224.toInt(), warn = 0xFFFF8A3D.toInt(), bad = 0xFFFF4D4D.toInt(),
        onFill = 0xFF1A1200.toInt(), marker = 0xFFFFFFFF.toInt(),
    )
    private val teal = Pal(
        "TEAL", true,
        bg = 0xFF0F1B1E.toInt(), ink = 0xFFE6F4F1.toInt(), sub = 0xFF8AA7A6.toInt(), track = 0xFF22373A.toInt(),
        good = 0xFF2DD4BF.toInt(), warn = 0xFFFBBF24.toInt(), bad = 0xFFFB7185.toInt(),
        onFill = 0xFF04211D.toInt(), marker = 0xFFFFFFFF.toInt(),
    )
    private val violet = Pal(
        "VIOLET", true,
        bg = 0xFF1A1330.toInt(), ink = 0xFFF1EBFF.toInt(), sub = 0xFFA79BC9.toInt(), track = 0xFF2E2450.toInt(),
        good = 0xFFA78BFA.toInt(), warn = 0xFFF472B6.toInt(), bad = 0xFFFB7185.toInt(),
        onFill = 0xFF1B0F44.toInt(), marker = 0xFFFFFFFF.toInt(),
    )
    private val paper = Pal(
        "PAPER", false,
        bg = 0xFFF4EEE1.toInt(), ink = 0xFF1B1A17.toInt(), sub = 0xFF6B665A.toInt(), track = 0xFFD8D0BC.toInt(),
        good = 0xFF1B1A17.toInt(), warn = 0xFFC77700.toInt(), bad = 0xFFD6342C.toInt(),
        onFill = 0xFFF4EEE1.toInt(), marker = 0xFFD6342C.toInt(),
    )

    fun get(name: String): Pal = when (name) {
        "TEAL" -> teal
        "VIOLET" -> violet
        "PAPER" -> paper
        else -> amber
    }
}

object WidgetStyles {
    val names = listOf("RINGS", "BATTERIES", "COLUMNS", "BARS", "GAUGES", "CELLS")
}

/** 0 = fine (under 50% used), 1 = careful (under 80%), 2 = nearly out. */
fun levelOf(usedPercent: Double): Int = when {
    usedPercent >= 80.0 -> 2
    usedPercent >= 50.0 -> 1
    else -> 0
}

fun Pal.levelColor(level: Int): Int = when (level) {
    2 -> bad
    1 -> warn
    else -> good
}
