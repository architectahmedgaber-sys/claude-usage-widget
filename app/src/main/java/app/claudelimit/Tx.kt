package app.claudelimit

import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf

/** Arabic first, English when the language is switched in Settings. Texts live next to where they are used. */
object Tx {
    @Volatile
    var ar: Boolean = true

    fun t(arabic: String, english: String): String = if (ar) arabic else english
}

val LocalAr = compositionLocalOf { true }

@Composable
fun tt(arabic: String, english: String): String = if (LocalAr.current) arabic else english
