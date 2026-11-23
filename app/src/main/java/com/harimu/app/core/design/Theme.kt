package com.harimu.app.core.design

import android.os.Build
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.harimu.app.R

enum class ThemeMode(val labelRes: Int) {
    SYSTEM(R.string.theme_system),
    LIGHT(R.string.theme_light),
    DARK(R.string.theme_dark),
    AMOLED(R.string.theme_amoled)
}

@Immutable
data class HariMuSpacingTokens(
    val xxs: Dp = 4.dp,
    val xs: Dp = 8.dp,
    val sm: Dp = 12.dp,
    val md: Dp = 16.dp,
    val lg: Dp = 24.dp,
    val xl: Dp = 32.dp
)

val LocalHariMuSpacing = staticCompositionLocalOf { HariMuSpacingTokens() }

object HariMuThemeTokens {
    val spacing: HariMuSpacingTokens
        @Composable get() = LocalHariMuSpacing.current
}

object HariMuColors {
    val ElectricOrange = Color(0xFFFF6B2C)
    val ElectricOrangeDark = Color(0xFFE55315)
    val TealEmerald = Color(0xFF00BFA5)
    val DeepObsidian = Color(0xFF11141C)
    val SlateSurface = Color(0xFF1A1F2B)
    val SlateElevated = Color(0xFF232938)
    val PureBlack = Color(0xFF000000)
    val AmoledCard = Color(0xFF0D1017)

    val AlabasterBg = Color(0xFFF6F7FB)
    val CrispWhite = Color(0xFFFFFFFF)
    val LightSurfaceVariant = Color(0xFFE9ECF4)

    val GroupCore = Color(0xFFFF6B2C)
    val GroupVideo = Color(0xFFF43F5E)
    val GroupAudio = Color(0xFF8B5CF6)
    val GroupImage = Color(0xFF06B6D4)
    val GroupPdf = Color(0xFFEF4444)
    val GroupConverter = Color(0xFF10B981)
}

val PlusJakartaSans = FontFamily(
    Font(R.font.plus_jakarta_sans, FontWeight.Normal),
    Font(R.font.plus_jakarta_sans, FontWeight.Medium),
    Font(R.font.plus_jakarta_sans, FontWeight.SemiBold),
    Font(R.font.plus_jakarta_sans, FontWeight.Bold)
)

val JetBrainsMono = FontFamily(
    Font(R.font.jetbrains_mono, FontWeight.Normal),
    Font(R.font.jetbrains_mono, FontWeight.Medium),
    Font(R.font.jetbrains_mono, FontWeight.Bold)
)

val MonoTextStyle = TextStyle(
    fontFamily = JetBrainsMono,
    fontWeight = FontWeight.Medium,
    fontSize = 12.sp,
    lineHeight = 16.sp,
    letterSpacing = 0.2.sp
)

val AppTypography = Typography(
    displaySmall = TextStyle(
        fontFamily = PlusJakartaSans,
        fontWeight = FontWeight.Bold,
        fontSize = 28.sp,
        lineHeight = 34.sp,
        letterSpacing = (-0.5).sp
    ),
    headlineMedium = TextStyle(
        fontFamily = PlusJakartaSans,
        fontWeight = FontWeight.Bold,
        fontSize = 24.sp,
        lineHeight = 30.sp
    ),
    headlineSmall = TextStyle(
        fontFamily = PlusJakartaSans,
        fontWeight = FontWeight.SemiBold,
        fontSize = 20.sp,
        lineHeight = 26.sp
    ),
    titleLarge = TextStyle(
        fontFamily = PlusJakartaSans,
        fontWeight = FontWeight.Bold,
        fontSize = 18.sp,
        lineHeight = 24.sp
    ),
    titleMedium = TextStyle(
        fontFamily = PlusJakartaSans,
        fontWeight = FontWeight.SemiBold,
        fontSize = 15.sp,
        lineHeight = 21.sp
    ),
    titleSmall = TextStyle(
        fontFamily = PlusJakartaSans,
        fontWeight = FontWeight.SemiBold,
        fontSize = 13.sp,
        lineHeight = 18.sp
    ),
    bodyLarge = TextStyle(
        fontFamily = PlusJakartaSans,
        fontWeight = FontWeight.Normal,
        fontSize = 15.sp,
        lineHeight = 22.sp
    ),
    bodyMedium = TextStyle(
        fontFamily = PlusJakartaSans,
        fontWeight = FontWeight.Normal,
        fontSize = 13.sp,
        lineHeight = 19.sp
    ),
    bodySmall = TextStyle(
        fontFamily = PlusJakartaSans,
        fontWeight = FontWeight.Normal,
        fontSize = 11.5.sp,
        lineHeight = 16.sp
    ),
    labelMedium = TextStyle(
        fontFamily = PlusJakartaSans,
        fontWeight = FontWeight.SemiBold,
        fontSize = 12.sp,
        lineHeight = 16.sp
    ),
    labelSmall = TextStyle(
        fontFamily = JetBrainsMono,
        fontWeight = FontWeight.Medium,
        fontSize = 10.5.sp,
        lineHeight = 14.sp
    )
)

val AppShapes = Shapes(
    small = RoundedCornerShape(10.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(22.dp),
    extraLarge = RoundedCornerShape(28.dp)
)

private val LightColors = lightColorScheme(
    primary = HariMuColors.ElectricOrange,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFFFE5DA),
    onPrimaryContainer = Color(0xFF5D1900),
    secondary = HariMuColors.TealEmerald,
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFD0F7F0),
    onSecondaryContainer = Color(0xFF00382F),
    background = HariMuColors.AlabasterBg,
    onBackground = Color(0xFF12151E),
    surface = HariMuColors.CrispWhite,
    onSurface = Color(0xFF12151E),
    surfaceVariant = HariMuColors.LightSurfaceVariant,
    onSurfaceVariant = Color(0xFF4C556A),
    outline = Color(0xFFD2D7E5),
    error = Color(0xFFDC2626),
    onError = Color.White
)

private val DarkColors = darkColorScheme(
    primary = HariMuColors.ElectricOrange,
    onPrimary = Color.White,
    primaryContainer = Color(0xFF4B1E0B),
    onPrimaryContainer = Color(0xFFFFDBCC),
    secondary = HariMuColors.TealEmerald,
    onSecondary = Color(0xFF00251F),
    secondaryContainer = Color(0xFF004D42),
    onSecondaryContainer = Color(0xFFA7F3E7),
    background = HariMuColors.DeepObsidian,
    onBackground = Color(0xFFF1F5F9),
    surface = HariMuColors.SlateSurface,
    onSurface = Color(0xFFF1F5F9),
    surfaceVariant = HariMuColors.SlateElevated,
    onSurfaceVariant = Color(0xFFA3AEC4),
    outline = Color(0xFF2E374B),
    error = Color(0xFFF87171),
    onError = Color(0xFF450A0A)
)

private val AmoledColors = darkColorScheme(
    primary = HariMuColors.ElectricOrange,
    onPrimary = Color.White,
    primaryContainer = Color(0xFF3E1807),
    onPrimaryContainer = Color(0xFFFFDBCC),
    secondary = HariMuColors.TealEmerald,
    onSecondary = Color(0xFF00251F),
    secondaryContainer = Color(0xFF003D34),
    onSecondaryContainer = Color(0xFFA7F3E7),
    background = HariMuColors.PureBlack,
    onBackground = Color(0xFFF8FAFC),
    surface = HariMuColors.AmoledCard,
    onSurface = Color(0xFFF8FAFC),
    surfaceVariant = Color(0xFF161B26),
    onSurfaceVariant = Color(0xFF94A3B8),
    outline = Color(0xFF262E3F),
    error = Color(0xFFF87171),
    onError = Color(0xFF450A0A)
)

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

    val colorScheme = when {
        supportsDynamic && themeMode == ThemeMode.AMOLED ->
            dynamicDarkColorScheme(context).copy(
                background = HariMuColors.PureBlack,
                surface = HariMuColors.AmoledCard
            )
        supportsDynamic && useDark -> dynamicDarkColorScheme(context)
        supportsDynamic && !useDark -> dynamicLightColorScheme(context)
        themeMode == ThemeMode.AMOLED -> AmoledColors
        useDark -> DarkColors
        else -> LightColors
    }

    CompositionLocalProvider(LocalHariMuSpacing provides HariMuSpacingTokens()) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = AppTypography,
            shapes = AppShapes,
            content = content
        )
    }
}
