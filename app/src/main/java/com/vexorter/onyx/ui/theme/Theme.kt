package com.vexorter.onyx.ui.theme

import android.app.Activity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat
import com.vexorter.onyx.data.prefs.AccentColor
import com.vexorter.onyx.data.prefs.ThemeMode

private fun darkColors(accent: AccentColor) = darkColorScheme(
    primary = accent.dark,
    onPrimary = accent.darkOn,
    primaryContainer = accent.darkContainer,
    onPrimaryContainer = accent.dark,
    secondary = Amber,
    onSecondary = AmberDeep,
    secondaryContainer = AmberDeep,
    onSecondaryContainer = Amber,
    tertiary = Violet,
    onTertiary = VioletDeep,
    tertiaryContainer = VioletDeep,
    onTertiaryContainer = Violet,
    error = Coral,
    onError = CoralDeep,
    errorContainer = CoralDeep,
    onErrorContainer = Coral,
    background = DarkBackground,
    onBackground = DarkOnSurface,
    surface = DarkBackground,
    onSurface = DarkOnSurface,
    surfaceVariant = DarkSurfaceHigh,
    onSurfaceVariant = DarkOnSurfaceVariant,
    surfaceContainer = DarkSurface,
    surfaceContainerHigh = DarkSurfaceHigh,
    surfaceContainerHighest = DarkSurfaceHigh,
    outline = DarkOutline,
    outlineVariant = DarkOutlineVariant,
)

private fun lightColors(accent: AccentColor) = lightColorScheme(
    primary = accent.light,
    onPrimary = Color.White,
    primaryContainer = accent.lightContainer,
    onPrimaryContainer = accent.darkOn,
    secondary = AmberPressed,
    onSecondary = Color.White,
    secondaryContainer = AmberLight,
    onSecondaryContainer = AmberDeep,
    tertiary = VioletPressed,
    onTertiary = Color.White,
    tertiaryContainer = VioletLight,
    onTertiaryContainer = VioletDeep,
    error = CoralPressed,
    onError = Color.White,
    errorContainer = CoralLight,
    onErrorContainer = CoralDeep,
    background = LightBackground,
    onBackground = LightOnSurface,
    surface = LightBackground,
    onSurface = LightOnSurface,
    surfaceVariant = LightSurfaceHigh,
    onSurfaceVariant = LightOnSurfaceVariant,
    surfaceContainer = LightSurface,
    surfaceContainerHigh = LightSurfaceHigh,
    surfaceContainerHighest = LightSurfaceHigh,
    outline = LightOutline,
    outlineVariant = LightOutlineVariant,
)

/** Цвета видов занятий — одна точка правды для карточек и легенды. */
data class LessonPalette(
    val lecture: Color,
    val practice: Color,
    val lab: Color,
    val exam: Color,
    val other: Color,
)

val LocalLessonPalette = staticCompositionLocalOf {
    LessonPalette(Mint, Amber, Violet, Coral, DarkOutline)
}

private val AccentColor.dark: Color
    get() = when (this) {
        AccentColor.MINT -> Mint
        AccentColor.AMBER -> Amber
        AccentColor.VIOLET -> Violet
        AccentColor.CORAL -> Coral
    }

private val AccentColor.darkOn: Color
    get() = when (this) {
        AccentColor.MINT -> MintDeep
        AccentColor.AMBER -> AmberDeep
        AccentColor.VIOLET -> VioletDeep
        AccentColor.CORAL -> CoralDeep
    }

private val AccentColor.darkContainer: Color
    get() = when (this) {
        AccentColor.MINT -> MintContainer
        AccentColor.AMBER -> AmberDeep
        AccentColor.VIOLET -> VioletDeep
        AccentColor.CORAL -> CoralDeep
    }

private val AccentColor.light: Color
    get() = when (this) {
        AccentColor.MINT -> MintPressed
        AccentColor.AMBER -> AmberPressed
        AccentColor.VIOLET -> VioletPressed
        AccentColor.CORAL -> CoralPressed
    }

private val AccentColor.lightContainer: Color
    get() = when (this) {
        AccentColor.MINT -> MintLight
        AccentColor.AMBER -> AmberLight
        AccentColor.VIOLET -> VioletLight
        AccentColor.CORAL -> CoralLight
    }

@Composable
fun RucScheduleTheme(
    themeMode: ThemeMode,
    accent: AccentColor = AccentColor.MINT,
    amoled: Boolean = false,
    content: @Composable () -> Unit,
) {
    val darkTheme = when (themeMode) {
        ThemeMode.SYSTEM -> isSystemInDarkTheme()
        ThemeMode.LIGHT -> false
        ThemeMode.DARK -> true
    }

    val colorScheme = when {
        darkTheme && amoled -> darkColors(accent).copy(
            background = Color.Black,
            surface = Color.Black,
            surfaceContainer = Color(0xFF0B0D0F),
            surfaceContainerHigh = Color(0xFF141719),
            surfaceContainerHighest = Color(0xFF141719),
        )

        darkTheme -> darkColors(accent)
        else -> lightColors(accent)
    }

    val lessonPalette = if (darkTheme) {
        LessonPalette(Mint, Amber, Violet, Coral, DarkOnSurfaceVariant)
    } else {
        LessonPalette(MintPressed, AmberPressed, VioletPressed, CoralPressed, LightOnSurfaceVariant)
    }

    val view = LocalView.current
    if (!LocalInspectionMode.current) {
        SideEffect {
            val window = (view.context as? Activity)?.window ?: return@SideEffect
            WindowCompat.getInsetsController(window, view).apply {
                isAppearanceLightStatusBars = !darkTheme
                isAppearanceLightNavigationBars = !darkTheme
            }
        }
    }

    CompositionLocalProvider(LocalLessonPalette provides lessonPalette) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = AppTypography,
            shapes = AppShapes,
            content = content,
        )
    }
}
