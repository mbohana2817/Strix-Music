/**
 * Blazify Project (C) 2026
 * Metrolist Project (C) 2026
 * Licensed under GPL-3.0 | See NOTICE for contributors
 */

package com.blazify.music.ui.theme

import android.graphics.Bitmap
import android.os.Build
import android.graphics.RenderEffect
import android.graphics.Shader
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.SaverScope
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.palette.graphics.Palette
import com.materialkolor.PaletteStyle
import com.materialkolor.dynamiccolor.ColorSpec
import com.materialkolor.rememberDynamicColorScheme
import com.materialkolor.score.Score
import com.blazify.music.constants.AppBackgroundStyle
import com.blazify.music.constants.GlassThemeKey
import com.blazify.music.utils.rememberPreference

val DefaultThemeColor = Color(0xFFED5564)

// Strix Music brand colors (burgundy -> rose gradient)
val BlazeThemeColor = Color(0xFF9B3156)
val BlazeGradientEnd = Color(0xFFE66A91)

/**
 * Cards and sheets in pure-black dark.
 *
 * The iPhone uses #0A0A0A here, but it reaches that colour from a #000000 page
 * only when someone has deliberately turned pure black on — its own dark mode
 * is a tonal one that starts the page at 4% and the card at 9%, so the card is
 * more than twice the brightness of what it sits on. Copying the iPhone's
 * pure-black number onto a page that is genuinely #000000 left a 4% card that
 * did not read as a card at all. These keep the true-black page and restore
 * the step up to it.
 */
val BlazeBlackSurface = Color(0xFF141414)

/** A step above a card: search fields, chips, anything resting on one. */
val BlazeBlackSurfaceHigh = Color(0xFF1F1F1F)

@Composable
fun BlazifyTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    pureBlack: Boolean = false,
    themeColor: Color = DefaultThemeColor,
    backgroundStyle: AppBackgroundStyle = AppBackgroundStyle.STRIX_BURGUNDY,
    auroraEnabled: Boolean = true,
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

    // Apply pureBlack modification if needed, similar to original logic
    val glassEnabled by rememberPreference(GlassThemeKey, defaultValue = true)

    val colorScheme = remember(baseColorScheme, pureBlack, darkTheme, backgroundStyle, glassEnabled) {
        if (darkTheme && pureBlack) {
            baseColorScheme.pureBlack(true)
        } else {
            val surfaces = when (backgroundStyle) {
                AppBackgroundStyle.STRIX_BURGUNDY -> if (darkTheme) {
                    listOf(Color(0xFF10070C), Color(0xFF190B12), Color(0xFF24111A), Color(0xFF2D1721))
                } else {
                    listOf(Color(0xFFFFF7F9), Color(0xFFFFF0F4), Color(0xFFFFE8EF), Color(0xFFF8DDE6))
                }
                AppBackgroundStyle.MIDNIGHT_PLUM -> if (darkTheme) {
                    listOf(Color(0xFF0C0A12), Color(0xFF15111F), Color(0xFF211A2D), Color(0xFF2B2339))
                } else {
                    listOf(Color(0xFFF8F6FC), Color(0xFFF0ECF8), Color(0xFFE9E2F3), Color(0xFFDDD4EB))
                }
                AppBackgroundStyle.DEEP_OCEAN -> if (darkTheme) {
                    listOf(Color(0xFF071015), Color(0xFF0D1A21), Color(0xFF142630), Color(0xFF1C3440))
                } else {
                    listOf(Color(0xFFF4FAFC), Color(0xFFEAF5F8), Color(0xFFDDEEF3), Color(0xFFCEE4EB))
                }
                AppBackgroundStyle.FOREST -> if (darkTheme) {
                    listOf(Color(0xFF09110D), Color(0xFF111D16), Color(0xFF1A2A20), Color(0xFF24392B))
                } else {
                    listOf(Color(0xFFF5FAF6), Color(0xFFEBF5ED), Color(0xFFDDEDE1), Color(0xFFCEE3D4))
                }
                AppBackgroundStyle.AMOLED -> if (darkTheme) {
                    listOf(Color.Black, Color(0xFF080808), Color(0xFF121212), Color(0xFF1B1B1B))
                } else {
                    listOf(Color(0xFFFFFFFF), Color(0xFFF8F8F8), Color(0xFFF0F0F0), Color(0xFFE8E8E8))
                }
            }
            if (!glassEnabled) {
                baseColorScheme.copy(
                    background = surfaces[0],
                    surface = surfaces[0],
                    surfaceDim = surfaces[0],
                    surfaceContainerLowest = surfaces[0],
                    surfaceContainerLow = surfaces[1],
                    surfaceContainer = surfaces[2],
                    surfaceContainerHigh = surfaces[3],
                    surfaceContainerHighest = surfaces[3],
                )
            } else {
                // iOS-style glass mode: let the animated Aurora remain visible
                // through the page while Material 3 containers become translucent.
                // This is intentionally app-wide, not only the bottom navbar.
                val glass = if (darkTheme) Color.White.copy(alpha = 0.075f) else Color.White.copy(alpha = 0.52f)
                val glassLow = if (darkTheme) Color.White.copy(alpha = 0.045f) else Color.White.copy(alpha = 0.34f)
                val glassHigh = if (darkTheme) Color.White.copy(alpha = 0.105f) else Color.White.copy(alpha = 0.64f)
                baseColorScheme.copy(
                    background = Color.Transparent,
                    surface = Color.Transparent,
                    surfaceDim = Color.Transparent,
                    surfaceContainerLowest = Color.Transparent,
                    surfaceContainerLow = glassLow,
                    surfaceContainer = glass,
                    surfaceContainerHigh = glassHigh,
                    surfaceContainerHighest = glassHigh,
                )
            }
        }
    }

    // Use standard MaterialTheme instead of MaterialExpressiveTheme
    MaterialTheme(
        colorScheme = colorScheme,
        typography = AppTypography,
    ) {
        if (auroraEnabled && !pureBlack) {
            AuroraBackdrop(
                style = backgroundStyle,
                darkTheme = darkTheme,
                content = content,
            )
        } else {
            content()
        }
    }
}

@Composable
private fun AuroraBackdrop(
    style: AppBackgroundStyle,
    darkTheme: Boolean,
    content: @Composable () -> Unit,
) {
    val transition = rememberInfiniteTransition(label = "strix-aurora")
    val shift by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(9000),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "strix-aurora-shift",
    )
    val base = when (style) {
        AppBackgroundStyle.STRIX_BURGUNDY -> listOf(Color(0xFF12080E), Color(0xFF3B1224), Color(0xFF180B15))
        AppBackgroundStyle.MIDNIGHT_PLUM -> listOf(Color(0xFF0C0A13), Color(0xFF302044), Color(0xFF15101F))
        AppBackgroundStyle.DEEP_OCEAN -> listOf(Color(0xFF071116), Color(0xFF143847), Color(0xFF0B1820))
        AppBackgroundStyle.FOREST -> listOf(Color(0xFF08110C), Color(0xFF173624), Color(0xFF0D1C14))
        AppBackgroundStyle.AMOLED -> listOf(Color.Black, Color.Black, Color(0xFF080808))
    }
    val a = base[0].copy(alpha = if (darkTheme) 0.94f else 0.62f)
    val b = base[1].copy(alpha = if (darkTheme) 0.54f else 0.36f)
    val c = base[2].copy(alpha = if (darkTheme) 0.78f else 0.42f)
    val x = 0.18f + shift * 0.22f
    Box(modifier = Modifier) {
        // Keep the moving aurora as a separate layer so the app content can
        // remain translucent above it. On Android 12+ the backdrop itself is
        // softly blurred, matching the depth used by iOS glass surfaces.
        Box(
            modifier = Modifier
                .matchParentSize()
                .graphicsLayer {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                        renderEffect = RenderEffect.createBlurEffect(28f, 28f, Shader.TileMode.CLAMP)
                    }
                }
                .background(
                    Brush.linearGradient(
                        colors = listOf(a, b, c, a),
                        start = androidx.compose.ui.geometry.Offset(x * 900f, 0f),
                        end = androidx.compose.ui.geometry.Offset((1f - x) * 900f, 1400f),
                    )
                ),
        )
        content()
    }
}

fun Bitmap.extractThemeColor(): Color {
    val colorsToPopulation = Palette.from(this)
        .maximumColorCount(8)
        .generate()
        .swatches
        .associate { it.rgb to it.population }
    val rankedColors = Score.score(colorsToPopulation)
    return Color(rankedColors.first())
}

fun Bitmap.extractGradientColors(): List<Color> {
    val extractedColors = Palette.from(this)
        .maximumColorCount(64)
        .generate()
        .swatches
        .associate { it.rgb to it.population }

    val orderedColors = Score.score(extractedColors, 2, 0xff4285f4.toInt(), true)
        .sortedByDescending { Color(it).luminance() }

    return if (orderedColors.size >= 2)
        listOf(Color(orderedColors[0]), Color(orderedColors[1]))
    else
        listOf(Color(0xFF595959), Color(0xFF0D0D0D))
}

/**
 * The pure-black dark scheme, matched to the iPhone build.
 *
 * Material generates every surface tone from the seed colour, so with an amber
 * seed a "black" theme still drew its cards in a warm brown-grey, and album-art
 * theming moved them about as the artwork changed. Blacking out `surface` and
 * `background` alone left every container role behind, which is most of what a
 * settings page is made of.
 *
 * These are the three steps the iPhone uses — black page, #0A0A0A card,
 * #121212 for anything sitting on a card — applied across the whole ramp so a
 * card is the same colour whatever is playing.
 */
fun ColorScheme.pureBlack(apply: Boolean) =
    if (apply) copy(
        background = Color.Black,
        surface = Color.Black,
        surfaceDim = Color.Black,
        surfaceContainerLowest = Color.Black,
        surfaceContainerLow = BlazeBlackSurface,
        surfaceContainer = BlazeBlackSurface,
        surfaceContainerHigh = BlazeBlackSurfaceHigh,
        surfaceContainerHighest = BlazeBlackSurfaceHigh,
        surfaceBright = BlazeBlackSurfaceHigh,
        surfaceVariant = BlazeBlackSurfaceHigh,
    ) else this

val ColorSaver = object : Saver<Color, Int> {
    override fun restore(value: Int): Color = Color(value)
    override fun SaverScope.save(value: Color): Int = value.toArgb()
}
