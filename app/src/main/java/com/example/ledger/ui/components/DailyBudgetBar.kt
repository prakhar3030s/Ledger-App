package com.example.ledger.ui.components

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.example.ledger.ui.theme.AccentYellow
import com.example.ledger.ui.theme.SlateGrey

/**
 * DailyBudgetBar — A brutalist, ultra-thin progress bar.
 *
 * Renders a 2dp-tall bar with a faint [SlateGrey] track and an [AccentYellow]
 * fill that animates to [spendPercentage] on load using spring physics.
 *
 * No Material [LinearProgressIndicator] is used. This is a raw [Canvas] draw
 * for maximum control over geometry, color, and animation.
 *
 * @param spendPercentage 0f..1f — the fraction of daily budget consumed.
 * @param modifier Optional layout modifier.
 */
@Composable
fun DailyBudgetBar(
    spendPercentage: Float,
    modifier: Modifier = Modifier
) {
    // Start from 0 and animate to the target on first composition.
    // This creates the satisfying "fill in" motion when the screen loads.
    var targetFraction by remember { mutableFloatStateOf(0f) }

    LaunchedEffect(spendPercentage) {
        targetFraction = spendPercentage.coerceIn(0f, 1f)
    }

    // Spring-driven fill animation.
    // StiffnessLow → slow, deliberate sweep. DampingRatioNoBouncy → no overshoot,
    // the bar glides to its detent and stops dead — matching the brutalist aesthetic.
    val animatedFraction by animateFloatAsState(
        targetValue = targetFraction,
        animationSpec = spring(
            stiffness = Spring.StiffnessLow,
            dampingRatio = Spring.DampingRatioNoBouncy
        ),
        label = "budgetBarFill"
    )

    // Track: SlateGrey at 20% opacity. Fill: AccentYellow, full opacity.
    val trackColor = SlateGrey.copy(alpha = 0.20f)
    val fillColor = AccentYellow

    Canvas(
        modifier = modifier
            .fillMaxWidth()
            .height(2.dp)
    ) {
        val barWidth = size.width
        val barHeight = size.height
        val cornerRadius = CornerRadius(barHeight / 2f, barHeight / 2f)

        // Draw the track (full width, faint).
        drawRoundRect(
            color = trackColor,
            topLeft = Offset.Zero,
            size = Size(barWidth, barHeight),
            cornerRadius = cornerRadius
        )

        // Draw the fill (animated width).
        val fillWidth = barWidth * animatedFraction
        if (fillWidth > 0f) {
            drawRoundRect(
                color = fillColor,
                topLeft = Offset.Zero,
                size = Size(fillWidth, barHeight),
                cornerRadius = cornerRadius
            )
        }
    }
}
