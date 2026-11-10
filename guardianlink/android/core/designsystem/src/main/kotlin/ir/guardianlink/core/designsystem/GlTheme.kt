package ir.guardianlink.core.designsystem

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.LayoutDirection

private val Primary = Color(0xFF5B6CF9)
private val PrimaryDark = Color(0xFFB4BFFF)
private val Ok = Color(0xFF2E7D32)
private val OkDark = Color(0xFF6DD58C)
private val Warn = Color(0xFFE65100)
private val WarnDark = Color(0xFFFFB066)

val LightColors = lightColorScheme(primary = Primary, secondary = Primary, tertiary = Primary)
val DarkColors = darkColorScheme(primary = PrimaryDark, secondary = PrimaryDark, tertiary = PrimaryDark)

object GlColor {
    val ok: Color @Composable get() = if (isSystemInDarkTheme()) OkDark else Ok
    val warn: Color @Composable get() = if (isSystemInDarkTheme()) WarnDark else Warn
    val err: Color @Composable get() = MaterialTheme.colorScheme.error
}

/** تم Material 3 + RTL خودکار برای فارسی. */
@Composable
fun GlTheme(content: @Composable () -> Unit) {
    val dark = isSystemInDarkTheme()
    val lang = LocalConfiguration.current.locales.get(0)?.language ?: "en"
    val rtl = lang == "fa"
    CompositionLocalProvider(LocalLayoutDirection provides if (rtl) LayoutDirection.Rtl else LayoutDirection.Ltr) {
        MaterialTheme(
            colorScheme = if (dark) DarkColors else LightColors,
            content = content,
        )
    }
}

/** ارقام فارسی برای نمایش. */
fun faNum(input: String): String =
    input.map { if (it in '0'..'9') ('۰' + (it - '0')) else it }.joinToString("")

fun faNum(input: Int): String = faNum(input.toString())
