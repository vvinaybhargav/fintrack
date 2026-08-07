package com.household.finance.ui.theme

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

// A deep, slightly blue-black ground with an electric violet/cyan accent pair —
// chosen so the accent reads as "premium fintech" rather than generic neon.
val InkBase = Color(0xFF0A0C12)
val InkRaised = Color(0xFF12141C)
val Violet = Color(0xFF8B7CF6)
val Cyan = Color(0xFF52E1D0)
val GlassStroke = Color(0x33FFFFFF)
val GlassFillTop = Color(0x1FFFFFFF)
val GlassFillBottom = Color(0x0AFFFFFF)
val TextPrimary = Color(0xFFF3F3F7)
val TextSecondary = Color(0xFFA5A8B8)
val Positive = Color(0xFF52E1D0)
val Warning = Color(0xFFF6B35C)
val Danger = Color(0xFFF16C6C)

private val HouseholdDarkScheme = darkColorScheme(
    primary = Violet,
    onPrimary = Color(0xFF120E2A),
    secondary = Cyan,
    background = InkBase,
    onBackground = TextPrimary,
    surface = InkRaised,
    onSurface = TextPrimary,
    error = Danger,
    onSurfaceVariant = TextSecondary
)

@Composable
fun HouseholdFinanceTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = HouseholdDarkScheme,
        typography = HouseholdTypography,
        content = content
    )
}

/** A frosted, translucent card — the app's signature surface. */
@Composable
fun GlassSurface(
    modifier: Modifier = Modifier,
    cornerRadius: Int = 24,
    contentPadding: Int = 16,
    content: @Composable () -> Unit
) {
    Surface(
        modifier = modifier
            .clip(RoundedCornerShape(cornerRadius.dp))
            .border(1.dp, GlassStroke, RoundedCornerShape(cornerRadius.dp))
            .background(
                Brush.verticalGradient(listOf(GlassFillTop, GlassFillBottom))
            ),
        color = Color.Transparent,
        contentColor = TextPrimary
    ) {
        androidx.compose.foundation.layout.Box(Modifier.padding(contentPadding.dp)) {
            content()
        }
    }
}

/** App background: near-black with two soft accent glows, painted once behind all screens. */
@Composable
fun GlassBackdrop(content: @Composable () -> Unit) {
    androidx.compose.foundation.layout.Box(
        modifier = Modifier
            .fillMaxSize()
            .background(InkBase)
            .background(
                Brush.radialGradient(
                    colors = listOf(Violet.copy(alpha = 0.16f), Color.Transparent),
                    center = androidx.compose.ui.geometry.Offset(120f, 60f),
                    radius = 900f
                )
            )
            .background(
                Brush.radialGradient(
                    colors = listOf(Cyan.copy(alpha = 0.10f), Color.Transparent),
                    center = androidx.compose.ui.geometry.Offset(900f, 1600f),
                    radius = 1000f
                )
            )
    ) {
        content()
    }
}
