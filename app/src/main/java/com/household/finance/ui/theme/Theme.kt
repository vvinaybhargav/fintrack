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

// Violet-tinted near-black ground (matches the FinTrack design import's phone-frame tokens),
// a saturated violet primary, and a teal secondary/positive accent.
val InkBase = Color(0xFF120F18)
val InkRaised = Color(0xFF1C1826)
val InkSurface2 = Color(0xFF2A2436)
val Violet = Color(0xFF9B6BFF)  // primary accent
val Cyan = Color(0xFF3FDFC0)    // secondary accent / positive
val GlassStroke = Color(0x1EFFFFFF)
val GlassFillTop = Color(0x16FFFFFF)
val GlassFillBottom = Color(0x08FFFFFF)
val TextPrimary = Color(0xFFF5F3FA)
val TextSecondary = Color(0xFFACA6BD)
val Positive = Color(0xFF3FDFC0)
val Warning = Color(0xFFF2795D)
val Danger = Color(0xFFF2795D)

private val HouseholdDarkScheme = darkColorScheme(
    primary = Violet,
    onPrimary = Color(0xFFFFFFFF),
    secondary = Cyan,
    onSecondary = Color(0xFF04211C),
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
