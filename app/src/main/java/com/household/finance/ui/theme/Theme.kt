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

// A richer near-black ground with a single mint primary and a soft red-orange alert —
// violet survives only as a faint gradient detail, never as a competing accent.
val InkBase = Color(0xFF0A0A0F)
val InkRaised = Color(0xFF14141C)
val Violet = Color(0xFF8B7CF6) // gradient-detail only, kept faint via low alpha where used
val Cyan = Color(0xFF2FE0C0)   // primary accent (mint)
val GlassStroke = Color(0x14FFFFFF) // ~8% white
val GlassFillTop = Color(0x14FFFFFF)
val GlassFillBottom = Color(0x08FFFFFF)
val TextPrimary = Color(0xFFF5F6F8)
val TextSecondary = Color(0xFFA7ACBD)
val Positive = Color(0xFF2FE0C0)
val Warning = Color(0xFFF2795D)
val Danger = Color(0xFFF2795D)

// Mint is the single primary accent (drives default Button/FilterChip fills); violet is demoted
// to secondary so it only ever shows up as a faint gradient detail, never a competing accent.
private val HouseholdDarkScheme = darkColorScheme(
    primary = Cyan,
    onPrimary = Color(0xFF04211C),
    secondary = Violet,
    onSecondary = Color(0xFF120E2A),
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
    cornerRadius: Int = 20,
    contentPadding: Int = 18,
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
