package io.github.vkardin.tukk.core.ui

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.core.view.WindowCompat
import io.github.vkardin.tukk.core.model.AppSettings
import io.github.vkardin.tukk.core.model.ThemeMode

/** Brand palette from the approved design. */
object TukkColors {
    val Ash = Color(0xFF121010)
    val Soot = Color(0xFF1B1715)
    val Heat = Color(0xFFFF7A35)
    val Spark = Color(0xFFFFD9A0)
    val Smolder = Color(0xFFB8441F)
    val Cinder = Color(0xFFF7F1EB)
}

private val DarkColors = darkColorScheme(
    primary = Color(0xFFFF9A66), onPrimary = Color(0xFF351709),
    background = TukkColors.Ash, surface = TukkColors.Soot,
    surfaceContainer = Color(0xFF231D19), onSurface = TukkColors.Cinder,
    onBackground = TukkColors.Cinder, onSurfaceVariant = Color(0xFFC7B8AE),
    secondaryContainer = Color(0xFF513221), onSecondaryContainer = TukkColors.Spark,
    outlineVariant = Color(0xFF3E342D),
)

private val LightColors = lightColorScheme(
    primary = Color(0xFF9D3C0B), onPrimary = Color.White,
    background = TukkColors.Cinder, surface = Color(0xFFFFFBF7),
    surfaceContainer = Color(0xFFF0E6DD),
    secondaryContainer = Color(0xFFFFDBC1), onSecondaryContainer = Color(0xFF512100),
)

private val Headings = FontFamily(Font(R.font.sora_bold, FontWeight.Bold))
private val Body = FontFamily(Font(R.font.inter_regular))

private val TukkTypography = Typography().let { base ->
    Typography(
        headlineLarge = base.headlineLarge.copy(fontFamily = Headings),
        headlineMedium = base.headlineMedium.copy(fontFamily = Headings),
        titleLarge = base.titleLarge.copy(fontFamily = Headings),
        titleMedium = base.titleMedium.copy(fontFamily = Headings),
        bodyLarge = base.bodyLarge.copy(fontFamily = Body),
        bodyMedium = base.bodyMedium.copy(fontFamily = Body),
        bodySmall = base.bodySmall.copy(fontFamily = Body),
        labelLarge = base.labelLarge.copy(fontFamily = Body),
    )
}

@Composable
fun AppSettings.isDark(): Boolean = when (theme) {
    ThemeMode.DARK -> true
    ThemeMode.LIGHT -> false
    ThemeMode.SYSTEM -> isSystemInDarkTheme()
}

@Composable
fun TukkTheme(settings: AppSettings, content: @Composable () -> Unit) {
    val context = LocalContext.current
    val dark = settings.isDark()
    val colors = when {
        settings.dynamicColor && Build.VERSION.SDK_INT >= 31 ->
            if (dark) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        dark -> DarkColors
        else -> LightColors
    }
    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = context.findActivity()?.window ?: return@SideEffect
            // The app theme can differ from the system theme, so system bar icons follow the app.
            window.setBackgroundDrawable(android.graphics.drawable.ColorDrawable(colors.background.toArgb()))
            WindowCompat.getInsetsController(window, view).apply {
                isAppearanceLightStatusBars = !dark
                isAppearanceLightNavigationBars = !dark
            }
        }
    }
    MaterialTheme(colorScheme = colors, typography = TukkTypography, content = content)
}

private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}
