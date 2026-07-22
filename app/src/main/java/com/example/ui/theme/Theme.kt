package com.example.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import com.example.model.AppThemeMode

// Extra tactile tokens
data class TactileColors(
    val cardBackground: Color,
    val indentedTrack: Color,
    val shadowColor: Color,
    val gridHairline: Color,
    val graphCanvasBackground: Color
)

val LocalTactileColors = staticCompositionLocalOf {
    TactileColors(
        cardBackground = PureWhiteLightCard,
        indentedTrack = Color(0xFFE5E2D8),
        shadowColor = Color.Black.copy(alpha = 0.08f),
        gridHairline = DeepCharcoalLightText.copy(alpha = 0.08f),
        graphCanvasBackground = PureWhiteLightCard
    )
}

@Composable
fun StudioEqTheme(
    themeMode: AppThemeMode = AppThemeMode.DARK,
    pureBlackOled: Boolean = false,
    customAccentHex: String = "#FF5D00",
    content: @Composable () -> Unit
) {
    val darkTheme = when (themeMode) {
        AppThemeMode.LIGHT -> false
        AppThemeMode.DARK -> true
        AppThemeMode.SYSTEM -> isSystemInDarkTheme()
    }

    val primaryAccent = try {
        Color(android.graphics.Color.parseColor(customAccentHex))
    } catch (e: Exception) {
        NeonOrangeHeroAccent
    }

    val darkColorScheme = darkColorScheme(
        primary = primaryAccent,
        onPrimary = Color.White,
        secondary = primaryAccent,
        onSecondary = Color.White,
        background = if (pureBlackOled) PureBlackOledBackground else PitchBlackBackground,
        onBackground = PureWhitePrimaryText,
        surface = DarkSlateCard,
        onSurface = PureWhitePrimaryText,
        surfaceVariant = IndentedTrackDark,
        onSurfaceVariant = DimSlateGreyText,
        outline = DarkSlateBorder
    )

    val lightColorScheme = lightColorScheme(
        primary = primaryAccent,
        onPrimary = Color.White,
        secondary = primaryAccent,
        onSecondary = Color.White,
        background = WarmLightBackground,
        onBackground = DeepCharcoalLightText,
        surface = PureWhiteLightCard,
        onSurface = DeepCharcoalLightText,
        surfaceVariant = Color(0xFFEAE7E0),
        onSurfaceVariant = DimLightText,
        outline = LightBorderSubtle
    )

    val colorScheme = if (darkTheme) darkColorScheme else lightColorScheme

    val tactileColors = if (darkTheme) {
        TactileColors(
            cardBackground = DarkSlateCard,
            indentedTrack = IndentedTrackDark,
            shadowColor = Color.Black.copy(alpha = 0.45f),
            gridHairline = PureWhitePrimaryText.copy(alpha = 0.08f),
            graphCanvasBackground = DarkSlateCard
        )
    } else {
        TactileColors(
            cardBackground = PureWhiteLightCard,
            indentedTrack = Color(0xFFEAE7E0),
            shadowColor = Color.Black.copy(alpha = 0.08f),
            gridHairline = DeepCharcoalLightText.copy(alpha = 0.08f),
            graphCanvasBackground = PureWhiteLightCard
        )
    }

    CompositionLocalProvider(LocalTactileColors provides tactileColors) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = Typography,
            content = content
        )
    }
}

