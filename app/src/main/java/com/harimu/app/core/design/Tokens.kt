package com.harimu.app.core.design

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.harimu.app.R

@Immutable
data class HariMuSpacingTokens(
    val xxs: Dp = 4.dp,
    val xs: Dp = 8.dp,
    val sm: Dp = 12.dp,
    val md: Dp = 16.dp,
    val lg: Dp = 24.dp,
    val xl: Dp = 32.dp,
    val xxl: Dp = 40.dp
)

@Immutable
data class HariMuLayoutTokens(
    val formMaxWidth: Dp = 640.dp,
    val catalogMaxWidth: Dp = 760.dp,
    val dashboardMaxWidth: Dp = 1040.dp,
    val compactScreenPadding: Dp = 16.dp,
    val mediumScreenPadding: Dp = 24.dp,
    val cardBorderAlpha: Float = 0.55f,
    val dividerAlpha: Float = 0.25f
)

@Immutable
data class HariMuElevationTokens(
    val none: Dp = 0.dp,
    val card: Dp = 0.dp,
    val subtle: Dp = 1.dp,
    val bar: Dp = 2.dp,
    val dialog: Dp = 6.dp,
    val sheet: Dp = 6.dp
)

@Immutable
data class HariMuBorderTokens(
    val hairline: Dp = 1.dp,
    val focused: Dp = 1.5.dp,
    val selected: Dp = 2.dp,
    val subtleAlpha: Float = 0.42f,
    val defaultAlpha: Float = 0.62f,
    val strongAlpha: Float = 0.85f
)

@Immutable
data class HariMuIconTokens(
    val xs: Dp = 16.dp,
    val sm: Dp = 18.dp,
    val md: Dp = 20.dp,
    val lg: Dp = 24.dp,
    val xl: Dp = 28.dp,
    val tile: Dp = 44.dp,
    val emptyContainer: Dp = 56.dp
)

@Immutable
data class HariMuShapeTokens(
    val badge: Shape = RoundedCornerShape(6.dp),
    val chip: Shape = RoundedCornerShape(10.dp),
    val iconTile: Shape = RoundedCornerShape(12.dp),
    val control: Shape = RoundedCornerShape(12.dp),
    val card: Shape = RoundedCornerShape(16.dp),
    val container: Shape = RoundedCornerShape(20.dp),
    val dialog: Shape = RoundedCornerShape(24.dp),
    val sheet: Shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp)
)

@Immutable
data class HariMuControlTokens(
    val minTouchTarget: Dp = 48.dp,
    val buttonMinHeight: Dp = 48.dp,
    val compactButtonMinHeight: Dp = 40.dp,
    val inputMinHeight: Dp = 52.dp,
    val searchBarHeight: Dp = 52.dp,
    val topBarHeight: Dp = 64.dp
)

@Immutable
data class HariMuMotionTokens(
    val fastMs: Int = 150,
    val standardMs: Int = 220,
    val emphasizedMs: Int = 280
)

@Immutable
data class HariMuSemanticColors(
    val isDark: Boolean = false,
    val isAmoled: Boolean = false,
    val success: Color = Color(0xFF0E8F6B),
    val onSuccess: Color = Color.White,
    val successContainer: Color = Color(0xFFD7F5EC),
    val onSuccessContainer: Color = Color(0xFF053829),
    val successBorder: Color = Color(0xFF0E8F6B),
    val warning: Color = Color(0xFFB45309),
    val onWarning: Color = Color.White,
    val warningContainer: Color = Color(0xFFFEF3C7),
    val onWarningContainer: Color = Color(0xFF451A03),
    val warningBorder: Color = Color(0xFFD97706),
    val info: Color = Color(0xFF1D4ED8),
    val onInfo: Color = Color.White,
    val infoContainer: Color = Color(0xFFDBEAFE),
    val onInfoContainer: Color = Color(0xFF1E3A8A),
    val infoBorder: Color = Color(0xFF3B82F6),
    val favoriteAccent: Color = HariMuColors.ElectricOrange
) {
    /**
     * Adjusts saturated tool-group accent colors so small text and icons retain
     * legible contrast over tinted badges in Light mode without muting Dark/AMOLED hues.
     */
    fun contrastSafeAccent(accent: Color): Color =
        if (isDark) accent else lerp(accent, Color(0xFF11141C), 0.34f)
}

val LocalHariMuSpacing = staticCompositionLocalOf { HariMuSpacingTokens() }
val LocalHariMuLayout = staticCompositionLocalOf { HariMuLayoutTokens() }
val LocalHariMuElevation = staticCompositionLocalOf { HariMuElevationTokens() }
val LocalHariMuBorders = staticCompositionLocalOf { HariMuBorderTokens() }
val LocalHariMuIcons = staticCompositionLocalOf { HariMuIconTokens() }
val LocalHariMuShapes = staticCompositionLocalOf { HariMuShapeTokens() }
val LocalHariMuControls = staticCompositionLocalOf { HariMuControlTokens() }
val LocalHariMuMotion = staticCompositionLocalOf { HariMuMotionTokens() }
val LocalHariMuSemanticColors = staticCompositionLocalOf { HariMuSemanticColors() }

object HariMuThemeTokens {
    val spacing: HariMuSpacingTokens
        @Composable get() = LocalHariMuSpacing.current

    val layout: HariMuLayoutTokens
        @Composable get() = LocalHariMuLayout.current

    val elevation: HariMuElevationTokens
        @Composable get() = LocalHariMuElevation.current

    val borders: HariMuBorderTokens
        @Composable get() = LocalHariMuBorders.current

    val icons: HariMuIconTokens
        @Composable get() = LocalHariMuIcons.current

    val shapes: HariMuShapeTokens
        @Composable get() = LocalHariMuShapes.current

    val controls: HariMuControlTokens
        @Composable get() = LocalHariMuControls.current

    val motion: HariMuMotionTokens
        @Composable get() = LocalHariMuMotion.current

    val semantic: HariMuSemanticColors
        @Composable get() = LocalHariMuSemanticColors.current
}

object HariMuColors {
    val ElectricOrange = Color(0xFFFF6B2C)
    val ElectricOrangeDark = Color(0xFFC8430C)
    val TealEmerald = Color(0xFF00BFA5)
    val TealEmeraldDark = Color(0xFF007A6A)
    val DeepObsidian = Color(0xFF11141C)
    val SlateSurface = Color(0xFF171C26)
    val SlateElevated = Color(0xFF212736)
    val SlateHighest = Color(0xFF2A3143)
    val PureBlack = Color(0xFF000000)
    val AmoledCard = Color(0xFF0B0E14)
    val AmoledElevated = Color(0xFF141924)

    val AlabasterBg = Color(0xFFF6F7FB)
    val CrispWhite = Color(0xFFFFFFFF)
    val LightSurfaceLow = Color(0xFFF0F2F8)
    val LightSurfaceContainer = Color(0xFFEAEDF5)
    val LightSurfaceVariant = Color(0xFFE3E7F1)

    val HeroScrimStart = Color(0xEE0E1119)
    val HeroScrimMid = Color(0xCC0E1119)
    val HeroScrimEnd = Color(0x660E1119)
    val HeroBadgeText = Color(0xFFFFB596)
    val HeroSubtitleText = Color(0xFFD1D5DB)
    val CanvasHandleDark = Color(0xFF0F1219)

    val GroupCore = Color(0xFFFF6B2C)
    val GroupVideo = Color(0xFFF43F5E)
    val GroupAudio = Color(0xFF8B5CF6)
    val GroupImage = Color(0xFF06B6D4)
    val GroupPdf = Color(0xFFEF4444)
    val GroupConverter = Color(0xFF10B981)
    val GroupNusantara = Color(0xFFF97316)
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
    letterSpacing = 0.2.sp,
    fontFeatureSettings = "tnum"
)

val AppTypography = Typography(
    displayLarge = TextStyle(
        fontFamily = PlusJakartaSans,
        fontWeight = FontWeight.Bold,
        fontSize = 34.sp,
        lineHeight = 40.sp,
        letterSpacing = (-0.6).sp
    ),
    displayMedium = TextStyle(
        fontFamily = PlusJakartaSans,
        fontWeight = FontWeight.Bold,
        fontSize = 30.sp,
        lineHeight = 36.sp,
        letterSpacing = (-0.5).sp
    ),
    displaySmall = TextStyle(
        fontFamily = PlusJakartaSans,
        fontWeight = FontWeight.Bold,
        fontSize = 26.sp,
        lineHeight = 32.sp,
        letterSpacing = (-0.4).sp
    ),
    headlineLarge = TextStyle(
        fontFamily = PlusJakartaSans,
        fontWeight = FontWeight.Bold,
        fontSize = 24.sp,
        lineHeight = 30.sp,
        letterSpacing = (-0.25).sp
    ),
    headlineMedium = TextStyle(
        fontFamily = PlusJakartaSans,
        fontWeight = FontWeight.Bold,
        fontSize = 22.sp,
        lineHeight = 28.sp,
        letterSpacing = (-0.2).sp
    ),
    headlineSmall = TextStyle(
        fontFamily = PlusJakartaSans,
        fontWeight = FontWeight.SemiBold,
        fontSize = 19.sp,
        lineHeight = 25.sp
    ),
    titleLarge = TextStyle(
        fontFamily = PlusJakartaSans,
        fontWeight = FontWeight.Bold,
        fontSize = 17.sp,
        lineHeight = 23.sp
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
        fontSize = 13.5.sp,
        lineHeight = 19.5.sp
    ),
    bodySmall = TextStyle(
        fontFamily = PlusJakartaSans,
        fontWeight = FontWeight.Normal,
        fontSize = 12.sp,
        lineHeight = 16.5.sp
    ),
    labelLarge = TextStyle(
        fontFamily = PlusJakartaSans,
        fontWeight = FontWeight.SemiBold,
        fontSize = 14.sp,
        lineHeight = 20.sp,
        letterSpacing = 0.1.sp
    ),
    labelMedium = TextStyle(
        fontFamily = PlusJakartaSans,
        fontWeight = FontWeight.SemiBold,
        fontSize = 12.sp,
        lineHeight = 16.sp,
        letterSpacing = 0.15.sp
    ),
    labelSmall = TextStyle(
        fontFamily = JetBrainsMono,
        fontWeight = FontWeight.Medium,
        fontSize = 10.5.sp,
        lineHeight = 14.sp,
        letterSpacing = 0.2.sp,
        fontFeatureSettings = "tnum"
    )
)

val AppShapes = Shapes(
    extraSmall = RoundedCornerShape(6.dp),
    small = RoundedCornerShape(10.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(20.dp),
    extraLarge = RoundedCornerShape(28.dp)
)
