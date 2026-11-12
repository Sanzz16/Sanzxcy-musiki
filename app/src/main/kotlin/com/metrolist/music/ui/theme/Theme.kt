/**
 * Metrolist Project (C) 2026
 * Licensed under GPL-3.0 | See git history for contributors
 */

package com.metrolist.music.ui.theme

import android.graphics.Bitmap
import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.foundation.LocalIndication
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.metrolist.music.ui.component.IosIndication
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import com.metrolist.music.ui.component.LiquidGlassEnabledKey
import com.metrolist.music.ui.component.LocalGlassBackdrop
import com.metrolist.music.ui.component.rememberGlassBackdrop
import com.metrolist.music.ui.component.SanzxcyVisualStyleKey
import com.metrolist.music.ui.component.VISUAL_STYLE_EDITORIAL
import com.metrolist.music.ui.component.VISUAL_STYLE_LIQUID_GLASS
import com.metrolist.music.utils.rememberPreference
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.SaverScope
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.palette.graphics.Palette
import com.materialkolor.PaletteStyle
import com.materialkolor.dynamiccolor.ColorSpec
import com.materialkolor.rememberDynamicColorScheme
import com.materialkolor.score.Score

val DefaultThemeColor = Color(0xFF72D6BC)

/** True bila efek Liquid Glass aktif (saklar ON). Komponen yang mendukung memakai permukaan translusen. */
val LocalLiquidGlass = compositionLocalOf { false }

/** Warna permukaan translusen saat efek kaca aktif; jika OFF tetap solid dan ringan. */
fun Color.glassIf(active: Boolean, alpha: Float = 0.72f): Color = if (active) copy(alpha = alpha) else this

private val SanzxcyShapes =
    Shapes(
        extraSmall = RoundedCornerShape(10.dp),
        small = RoundedCornerShape(14.dp),
        medium = RoundedCornerShape(18.dp),
        large = RoundedCornerShape(24.dp),
        extraLarge = RoundedCornerShape(32.dp),
    )

/** Tipografi Editorial: judul bergaya majalah (serif), isi tetap sans-serif agar mudah dibaca. */
private fun editorialTypography(): Typography {
    val d = Typography()
    val serif = FontFamily.Default
    return d.copy(
        displayLarge = d.displayLarge.copy(fontFamily = serif),
        displayMedium = d.displayMedium.copy(fontFamily = serif),
        displaySmall = d.displaySmall.copy(fontFamily = serif),
        headlineLarge = d.headlineLarge.copy(fontFamily = serif),
        headlineMedium = d.headlineMedium.copy(fontFamily = serif),
        headlineSmall = d.headlineSmall.copy(fontFamily = serif),
        titleLarge = d.titleLarge.copy(fontFamily = serif),
    )
}

/** Palet Editorial: ivory/kertas premium (terang) dan tinta hangat (gelap). */
private fun ColorScheme.editorial(dark: Boolean, overridePrimary: Boolean): ColorScheme {
    val base =
        if (dark) {
            copy(
                background = Color(0xFF14120F), onBackground = Color(0xFFEDE6D6),
                surface = Color(0xFF14120F), onSurface = Color(0xFFEDE6D6),
                surfaceVariant = Color(0xFF2A2621), onSurfaceVariant = Color(0xFFC9C1AE),
                surfaceDim = Color(0xFF14120F), surfaceBright = Color(0xFF38332C),
                surfaceContainerLowest = Color(0xFF0F0E0B), surfaceContainerLow = Color(0xFF1A1814),
                surfaceContainer = Color(0xFF1E1B17), surfaceContainerHigh = Color(0xFF26231E),
                surfaceContainerHighest = Color(0xFF302C26),
                outline = Color(0xFF8F8878), outlineVariant = Color(0xFF4A453B),
            )
        } else {
            copy(
                background = Color(0xFFF8F4EA), onBackground = Color(0xFF1C1B18),
                surface = Color(0xFFF8F4EA), onSurface = Color(0xFF1C1B18),
                surfaceVariant = Color(0xFFE6DFCF), onSurfaceVariant = Color(0xFF55514A),
                surfaceDim = Color(0xFFE3DBC9), surfaceBright = Color(0xFFFBF8F0),
                surfaceContainerLowest = Color(0xFFFFFDF8), surfaceContainerLow = Color(0xFFF4EFE3),
                surfaceContainer = Color(0xFFEFE9DB), surfaceContainerHigh = Color(0xFFE9E2D2),
                surfaceContainerHighest = Color(0xFFE3DBC9),
                outline = Color(0xFF8A8478), outlineVariant = Color(0xFFD6CFBE),
            )
        }
    if (!overridePrimary) return base
    return if (dark) {
        base.copy(
            primary = Color(0xFFE8D9B5), onPrimary = Color(0xFF2B2518),
            primaryContainer = Color(0xFF4A3F26), onPrimaryContainer = Color(0xFFF3E6C4),
            secondaryContainer = Color(0xFF3A3427), onSecondaryContainer = Color(0xFFEDE0C0),
        )
    } else {
        base.copy(
            primary = Color(0xFF2B2A27), onPrimary = Color(0xFFF8F4EA),
            primaryContainer = Color(0xFFEADFC8), onPrimaryContainer = Color(0xFF2B2518),
            secondaryContainer = Color(0xFFE4D9BF), onSecondaryContainer = Color(0xFF2B2518),
        )
    }
}

/** Palet premium original Sanzxcy: tinta gelap, surface bertingkat, dan aksen mint yang lembut. */
private fun ColorScheme.liquidGlass(dark: Boolean, overridePrimary: Boolean): ColorScheme {
    val base =
        if (dark) {
            copy(
                background = Color(0xFF000000), onBackground = Color(0xFFFFFFFF),
                surface = Color(0xFF000000), onSurface = Color(0xFFFFFFFF),
                surfaceVariant = Color(0xFF172320), onSurfaceVariant = Color(0xFFB5C9C3),
                surfaceDim = Color(0xFF000000), surfaceBright = Color(0xFF273B36),
                surfaceContainerLowest = Color(0xFF000000), surfaceContainerLow = Color(0xFF0A1211),
                surfaceContainer = Color(0xFF101B19), surfaceContainerHigh = Color(0xFF172521),
                surfaceContainerHighest = Color(0xFF22332E),
                outline = Color(0xFF718B83), outlineVariant = Color(0xFF2B403A),
            )
        } else {
            copy(
                background = Color(0xFFF2F2F7), onBackground = Color(0xFF000000),
                surface = Color(0xFFF2F2F7), onSurface = Color(0xFF000000),
                surfaceVariant = Color(0xFFE5E5EA), onSurfaceVariant = Color(0xFF6C6C70),
                surfaceDim = Color(0xFFD1D1D6), surfaceBright = Color(0xFFFFFFFF),
                surfaceContainerLowest = Color(0xFFFFFFFF), surfaceContainerLow = Color(0xFFF9F9FB),
                surfaceContainer = Color(0xFFFFFFFF), surfaceContainerHigh = Color(0xFFE5E5EA),
                surfaceContainerHighest = Color(0xFFD1D1D6),
                outline = Color(0xFF8E8E93), outlineVariant = Color(0xFFC6C6C8),
            )
        }
    if (!overridePrimary) return base
    return if (dark) {
        base.copy(
                primary = Color(0xFF9DE4D1), onPrimary = Color(0xFF062F27),
                primaryContainer = Color(0xFF174D42), onPrimaryContainer = Color(0xFFC4F5E7),
                secondaryContainer = Color(0xFF24483F), onSecondaryContainer = Color(0xFFD2F1E8),
                tertiary = Color(0xFF68E08B),
        )
    } else {
        base.copy(
                primary = Color(0xFF187E6D), onPrimary = Color(0xFFFFFFFF),
                primaryContainer = Color(0xFFC5F1E4), onPrimaryContainer = Color(0xFF00251E),
                secondaryContainer = Color(0xFFD7EEE7), onSecondaryContainer = Color(0xFF00251E),
                tertiary = Color(0xFF2DBE72),
        )
    }
}

/** Tipografi premium: display yang tegas, body yang lega, dan tracking rapat untuk judul. */
private fun premiumTypography(): Typography {
    val d = Typography()
    fun TextStyle.ios(size: Int, weight: FontWeight, spacing: Float = 0f, lh: Int = (size * 1.22f).toInt()) =
        copy(fontSize = size.sp, fontWeight = weight, letterSpacing = spacing.sp, lineHeight = lh.sp)
    return d.copy(
        displayLarge = d.displayLarge.copy(fontFamily = bbhBartle).ios(40, FontWeight.Bold, -0.6f),
        displayMedium = d.displayMedium.copy(fontFamily = bbhBartle).ios(34, FontWeight.Bold, -0.5f),
        displaySmall = d.displaySmall.ios(28, FontWeight.Bold, -0.4f),
        headlineLarge = d.headlineLarge.copy(fontFamily = bbhBartle).ios(34, FontWeight.Bold, -0.5f),
        headlineMedium = d.headlineMedium.ios(28, FontWeight.Bold, -0.4f),
        headlineSmall = d.headlineSmall.ios(22, FontWeight.Bold, -0.3f),
        titleLarge = d.titleLarge.copy(fontFamily = bbhBartle).ios(20, FontWeight.SemiBold, -0.2f),
        titleMedium = d.titleMedium.ios(17, FontWeight.SemiBold, -0.2f),
        titleSmall = d.titleSmall.ios(15, FontWeight.SemiBold, -0.1f),
        bodyLarge = d.bodyLarge.ios(17, FontWeight.Normal, -0.2f, 22),
        bodyMedium = d.bodyMedium.ios(15, FontWeight.Normal, -0.1f, 20),
        bodySmall = d.bodySmall.ios(13, FontWeight.Normal, 0f, 18),
        labelLarge = d.labelLarge.ios(15, FontWeight.Medium, -0.1f),
        labelMedium = d.labelMedium.ios(13, FontWeight.Medium, 0f),
        labelSmall = d.labelSmall.ios(11, FontWeight.Medium, 0.1f),
    )
}

@Composable
fun MetrolistTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    pureBlack: Boolean = false,
    themeColor: Color = DefaultThemeColor,
    content: @Composable () -> Unit,
) {
    val context = LocalContext.current
    // Determine if system dynamic colors should be used (Android S+ and default theme color)
    val useSystemDynamicColor = (themeColor == DefaultThemeColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S)

    // Select the appropriate color scheme generation method
    val baseColorScheme = if (useSystemDynamicColor) {
        // Use standard Material 3 dynamic color functions for system wallpaper colors
        if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
    } else {
        // Use materialKolor only when a specific seed color is provided
        rememberDynamicColorScheme(
            seedColor = themeColor, // themeColor is guaranteed non-default here
            isDark = darkTheme,
            specVersion = ColorSpec.SpecVersion.SPEC_2025,
            style = PaletteStyle.TonalSpot // Keep existing style
        )
    }

    // Sanzxcy: gaya visual (Editorial / Liquid Glass) dan saklar efek kaca
    val (visualStyle) = rememberPreference(SanzxcyVisualStyleKey, defaultValue = VISUAL_STYLE_LIQUID_GLASS)
    val (liquidGlass) = rememberPreference(LiquidGlassEnabledKey, defaultValue = true)
    val isEditorial = visualStyle == VISUAL_STYLE_EDITORIAL
    // Warna tema pilihan pengguna dihormati: aksen gaya hanya menimpa warna utama default.
    val overridePrimary = themeColor == DefaultThemeColor

    val colorScheme = remember(baseColorScheme, pureBlack, darkTheme, isEditorial, overridePrimary) {
        val styled =
            if (isEditorial) {
                baseColorScheme.editorial(darkTheme, overridePrimary)
            } else {
                baseColorScheme.liquidGlass(darkTheme, overridePrimary)
            }
        if (darkTheme && pureBlack) styled.pureBlack(true) else styled
    }
    val typography = remember(isEditorial) { if (isEditorial) editorialTypography() else premiumTypography() }

    val glassBackdrop = rememberGlassBackdrop()
    CompositionLocalProvider(
        LocalLiquidGlass provides (liquidGlass && !isEditorial),
        LocalGlassBackdrop provides glassBackdrop,
    ) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = typography,
            shapes = SanzxcyShapes,
        ) {
            CompositionLocalProvider(LocalIndication provides IosIndication) { content() }
        }
    }
}

fun Bitmap.extractThemeColor(): Color = Color(
    Palette.from(this)
        .maximumColorCount(8)
        .generate()
        .rankedColors(1, DefaultThemeColor.toArgb())
        .first()
)

internal fun Palette.rankedColors(
    desiredColorCount: Int,
    fallbackColor: Int,
): List<Int> = Score.score(
    swatches.associate { it.rgb to it.population },
    desiredColorCount,
    fallbackColor,
    true,
)

fun ColorScheme.pureBlack(apply: Boolean) =
    if (apply) copy(
        surface = Color.Black,
        background = Color.Black
    ) else this

val ColorSaver = object : Saver<Color, Int> {
    override fun restore(value: Int): Color = Color(value)
    override fun SaverScope.save(value: Color): Int = value.toArgb()
}
