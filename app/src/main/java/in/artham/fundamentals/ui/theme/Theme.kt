package `in`.artham.fundamentals.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** A quiet, high-contrast palette built for dense numbers rather than decoration. */
object ArthamColors {
    val Ink = Color(0xFF0B0F14)
    val Surface = Color(0xFF121821)
    val SurfaceHigh = Color(0xFF18202B)
    val Outline = Color(0xFF243040)
    val Mint = Color(0xFF2FE3A8)
    val MintDim = Color(0xFF1E9E77)
    val Amber = Color(0xFFFFC46B)
    val Rose = Color(0xFFFF6B6B)
    val Sky = Color(0xFF6BB8FF)
    val TextPrimary = Color(0xFFEAF0F7)
    val TextSecondary = Color(0xFF98A5B8)
    val TextTertiary = Color(0xFF6B7889)
    val Positive = Color(0xFF2FE3A8)
    val Negative = Color(0xFFFF6B6B)

    val PaperBg = Color(0xFFF7F9FC)
    val PaperSurface = Color(0xFFFFFFFF)
    val PaperOutline = Color(0xFFE1E7EF)
    val PaperText = Color(0xFF101822)
    val PaperSecondary = Color(0xFF5B6675)
}

private val DarkScheme = darkColorScheme(
    primary = ArthamColors.Mint,
    onPrimary = ArthamColors.Ink,
    primaryContainer = ArthamColors.MintDim,
    onPrimaryContainer = ArthamColors.Ink,
    secondary = ArthamColors.Sky,
    onSecondary = ArthamColors.Ink,
    tertiary = ArthamColors.Amber,
    background = ArthamColors.Ink,
    onBackground = ArthamColors.TextPrimary,
    surface = ArthamColors.Surface,
    onSurface = ArthamColors.TextPrimary,
    surfaceVariant = ArthamColors.SurfaceHigh,
    onSurfaceVariant = ArthamColors.TextSecondary,
    outline = ArthamColors.Outline,
    outlineVariant = ArthamColors.Outline,
    error = ArthamColors.Rose,
    onError = ArthamColors.Ink
)

private val LightScheme = lightColorScheme(
    primary = Color(0xFF0E7C5A),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFBFF3E0),
    onPrimaryContainer = Color(0xFF04261B),
    secondary = Color(0xFF2C6BA8),
    onSecondary = Color.White,
    background = ArthamColors.PaperBg,
    onBackground = ArthamColors.PaperText,
    surface = ArthamColors.PaperSurface,
    onSurface = ArthamColors.PaperText,
    surfaceVariant = Color(0xFFEFF3F8),
    onSurfaceVariant = ArthamColors.PaperSecondary,
    outline = ArthamColors.PaperOutline,
    error = Color(0xFFC0322F)
)

private val base = Typography()

private val ArthamTypography = Typography(
    displaySmall = base.displaySmall.copy(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.SemiBold, letterSpacing = (-0.5).sp),
    headlineMedium = base.headlineMedium.copy(fontWeight = FontWeight.SemiBold, letterSpacing = (-0.3).sp),
    headlineSmall = base.headlineSmall.copy(fontWeight = FontWeight.SemiBold),
    titleLarge = base.titleLarge.copy(fontWeight = FontWeight.SemiBold),
    titleMedium = base.titleMedium.copy(fontWeight = FontWeight.Medium),
    titleSmall = base.titleSmall.copy(fontWeight = FontWeight.Medium, letterSpacing = 0.2.sp),
    bodyLarge = base.bodyLarge.copy(lineHeight = 24.sp),
    bodyMedium = base.bodyMedium.copy(lineHeight = 21.sp),
    bodySmall = base.bodySmall.copy(lineHeight = 18.sp),
    labelLarge = base.labelLarge.copy(fontWeight = FontWeight.Medium),
    labelMedium = base.labelMedium.copy(letterSpacing = 0.4.sp),
    labelSmall = TextStyle(fontSize = 11.sp, lineHeight = 14.sp, letterSpacing = 0.6.sp, fontWeight = FontWeight.Medium)
)

@Composable
fun ArthamTheme(dark: Boolean = isSystemInDarkTheme(), content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = if (dark) DarkScheme else LightScheme,
        typography = ArthamTypography,
        shapes = MaterialTheme.shapes.copy(
            extraSmall = androidx.compose.foundation.shape.RoundedCornerShape(6.dp),
            small = androidx.compose.foundation.shape.RoundedCornerShape(10.dp),
            medium = androidx.compose.foundation.shape.RoundedCornerShape(14.dp),
            large = androidx.compose.foundation.shape.RoundedCornerShape(20.dp)
        ),
        content = content
    )
}
