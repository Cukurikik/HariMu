package com.harimu.app.core.design

import android.os.Build
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import com.harimu.app.R

enum class ThemeMode(val labelRes: Int) {
    SYSTEM(R.string.theme_system),
    LIGHT(R.string.theme_light),
    DARK(R.string.theme_dark),
    AMOLED(R.string.theme_amoled)
}

private val LightColors = lightColorScheme(
    primary = HariMuColors.ElectricOrangeDark,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFFFE5DA),
    onPrimaryContainer = Color(0xFF5D1900),
    secondary = HariMuColors.TealEmeraldDark,
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFD0F7F0),
    onSecondaryContainer = Color(0xFF00382F),
    tertiary = Color(0xFF4F46E5),
    onTertiary = Color.White,
    tertiaryContainer = Color(0xFFE6E8FF),
    onTertiaryContainer = Color(0xFF1A1563),
    background = HariMuColors.AlabasterBg,
    onBackground = Color(0xFF12151E),
    surface = HariMuColors.CrispWhite,
    onSurface = Color(0xFF12151E),
    surfaceVariant = HariMuColors.LightSurfaceVariant,
    onSurfaceVariant = Color(0xFF475066),
    surfaceContainerLowest = HariMuColors.CrispWhite,
    surfaceContainerLow = HariMuColors.LightSurfaceLow,
    surfaceContainer = HariMuColors.LightSurfaceContainer,
    surfaceContainerHigh = HariMuColors.LightSurfaceVariant,
    surfaceContainerHighest = Color(0xFFD8DDEA),
    outline = Color(0xFFC5CCE0),
    outlineVariant = Color(0xFFE0E4F0),
    error = Color(0xFFDC2626),
    onError = Color.White,
    errorContainer = Color(0xFFFEE2E2),
    onErrorContainer = Color(0xFF7F1D1D)
)

private val DarkColors = darkColorScheme(
    primary = HariMuColors.ElectricOrange,
    onPrimary = Color(0xFF280B00),
    primaryContainer = Color(0xFF4B1E0B),
    onPrimaryContainer = Color(0xFFFFDBCC),
    secondary = HariMuColors.TealEmerald,
    onSecondary = Color(0xFF00251F),
    secondaryContainer = Color(0xFF004D42),
    onSecondaryContainer = Color(0xFFA7F3E7),
    tertiary = Color(0xFFA5B4FC),
    onTertiary = Color(0xFF1E1B4B),
    tertiaryContainer = Color(0xFF312E81),
    onTertiaryContainer = Color(0xFFE0E7FF),
    background = HariMuColors.DeepObsidian,
    onBackground = Color(0xFFF1F5F9),
    surface = HariMuColors.SlateSurface,
    onSurface = Color(0xFFF1F5F9),
    surfaceVariant = HariMuColors.SlateElevated,
    onSurfaceVariant = Color(0xFFA3AEC4),
    surfaceContainerLowest = Color(0xFF0D1017),
    surfaceContainerLow = Color(0xFF151923),
    surfaceContainer = HariMuColors.SlateSurface,
    surfaceContainerHigh = HariMuColors.SlateElevated,
    surfaceContainerHighest = HariMuColors.SlateHighest,
    outline = Color(0xFF333D52),
    outlineVariant = Color(0xFF252C3D),
    error = Color(0xFFF87171),
    onError = Color(0xFF450A0A),
    errorContainer = Color(0xFF4C1116),
    onErrorContainer = Color(0xFFFECACA)
)

private val AmoledColors = darkColorScheme(
    primary = HariMuColors.ElectricOrange,
    onPrimary = Color(0xFF280B00),
    primaryContainer = Color(0xFF3E1807),
    onPrimaryContainer = Color(0xFFFFDBCC),
    secondary = HariMuColors.TealEmerald,
    onSecondary = Color(0xFF00251F),
    secondaryContainer = Color(0xFF003D34),
    onSecondaryContainer = Color(0xFFA7F3E7),
    tertiary = Color(0xFFA5B4FC),
    onTertiary = Color(0xFF1E1B4B),
    tertiaryContainer = Color(0xFF262369),
    onTertiaryContainer = Color(0xFFE0E7FF),
    background = HariMuColors.PureBlack,
    onBackground = Color(0xFFF8FAFC),
    surface = HariMuColors.AmoledCard,
    onSurface = Color(0xFFF8FAFC),
    surfaceVariant = HariMuColors.AmoledElevated,
    onSurfaceVariant = Color(0xFF94A3B8),
    surfaceContainerLowest = HariMuColors.PureBlack,
    surfaceContainerLow = Color(0xFF080B10),
    surfaceContainer = HariMuColors.AmoledCard,
    surfaceContainerHigh = HariMuColors.AmoledElevated,
    surfaceContainerHighest = Color(0xFF1D2433),
    outline = Color(0xFF2A3346),
    outlineVariant = Color(0xFF1B2230),
    error = Color(0xFFF87171),
    onError = Color(0xFF450A0A),
    errorContainer = Color(0xFF3F0D12),
    onErrorContainer = Color(0xFFFECACA)
)

private fun semanticColorsFor(
    useDark: Boolean,
    isAmoled: Boolean,
    colorScheme: ColorScheme,
    dynamicColor: Boolean
): HariMuSemanticColors = if (useDark) {
    HariMuSemanticColors(
        isDark = true,
        isAmoled = isAmoled,
        success = Color(0xFF34D399),
        onSuccess = Color(0xFF00291D),
        successContainer = if (isAmoled) Color(0xFF052E23) else Color(0xFF0A3B2E),
        onSuccessContainer = Color(0xFFA7F3D0),
        successBorder = Color(0xFF10B981),
        warning = Color(0xFFFBBF24),
        onWarning = Color(0xFF332000),
        warningContainer = if (isAmoled) Color(0xFF2E1E04) else Color(0xFF3B2707),
        onWarningContainer = Color(0xFFFDE68A),
        warningBorder = Color(0xFFF59E0B),
        info = Color(0xFF60A5FA),
        onInfo = Color(0xFF0B2545),
        infoContainer = if (isAmoled) Color(0xFF0D213B) else Color(0xFF142C4F),
        onInfoContainer = Color(0xFFBFDBFE),
        infoBorder = Color(0xFF3B82F6),
        favoriteAccent = if (dynamicColor) colorScheme.primary else HariMuColors.ElectricOrange
    )
} else {
    HariMuSemanticColors(
        isDark = false,
        isAmoled = false,
        success = HariMuColors.TealEmeraldDark,
        onSuccess = Color.White,
        successContainer = Color(0xFFD7F5EC),
        onSuccessContainer = Color(0xFF053829),
        successBorder = Color(0xFF0E8F6B),
        warning = Color(0xFFB45309),
        onWarning = Color.White,
        warningContainer = Color(0xFFFEF3C7),
        onWarningContainer = Color(0xFF451A03),
        warningBorder = Color(0xFFD97706),
        info = Color(0xFF1D4ED8),
        onInfo = Color.White,
        infoContainer = Color(0xFFDBEAFE),
        onInfoContainer = Color(0xFF1E3A8A),
        infoBorder = Color(0xFF3B82F6),
        favoriteAccent = if (dynamicColor) colorScheme.primary else HariMuColors.ElectricOrangeDark
    )
}

@Composable
fun HariMuTheme(
    themeMode: ThemeMode,
    isSystemDark: Boolean,
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit
) {
    val context = LocalContext.current
    val supportsDynamic = dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S
    val useDark = when (themeMode) {
        ThemeMode.LIGHT -> false
        ThemeMode.DARK, ThemeMode.AMOLED -> true
        ThemeMode.SYSTEM -> isSystemDark
    }
    val isAmoled = themeMode == ThemeMode.AMOLED

    val colorScheme = when {
        supportsDynamic && isAmoled ->
            dynamicDarkColorScheme(context).copy(
                background = HariMuColors.PureBlack,
                surface = HariMuColors.AmoledCard,
                surfaceContainerLowest = HariMuColors.PureBlack,
                surfaceContainerLow = Color(0xFF080B10),
                surfaceContainer = HariMuColors.AmoledCard,
                surfaceContainerHigh = HariMuColors.AmoledElevated
            )
        supportsDynamic && useDark -> dynamicDarkColorScheme(context)
        supportsDynamic && !useDark -> dynamicLightColorScheme(context)
        isAmoled -> AmoledColors
        useDark -> DarkColors
        else -> LightColors
    }

    val semanticColors = semanticColorsFor(
        useDark = useDark,
        isAmoled = isAmoled,
        colorScheme = colorScheme,
        dynamicColor = supportsDynamic
    )

    CompositionLocalProvider(
        LocalHariMuSpacing provides HariMuSpacingTokens(),
        LocalHariMuLayout provides HariMuLayoutTokens(),
        LocalHariMuElevation provides HariMuElevationTokens(),
        LocalHariMuBorders provides HariMuBorderTokens(),
        LocalHariMuIcons provides HariMuIconTokens(),
        LocalHariMuShapes provides HariMuShapeTokens(),
        LocalHariMuControls provides HariMuControlTokens(),
        LocalHariMuMotion provides HariMuMotionTokens(),
        LocalHariMuSemanticColors provides semanticColors
    ) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = AppTypography,
            shapes = AppShapes,
            content = content
        )
    }
}
