package com.example.ledger.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.spring
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.abs

@Composable
fun OdometerBalance(
    targetBalance: Double,
    modifier: Modifier = Modifier
) {
    val color = MaterialTheme.colorScheme.onBackground

    // Split balance into whole and decimal parts
    val wholePart = remember(targetBalance) {
        abs(targetBalance).toLong().toString()
    }
    val decimalPart = remember(targetBalance) {
        val dec = String.format("%02d", ((abs(targetBalance) * 100).toLong() % 100))
        dec
    }
    val isNegative = targetBalance < 0

    // Large style for whole number digits
    val largeStyle = MaterialTheme.typography.displayLarge
    val largeDigitWidth = 40.dp
    val largeDigitHeight = 60.dp

    // Small style for decimal digits
    val smallStyle = MaterialTheme.typography.displayLarge.copy(
        fontSize = 24.sp,
        fontWeight = FontWeight.Normal
    )
    val smallDigitWidth = 18.dp
    val smallDigitHeight = 32.dp

    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.End
    ) {
        // ── Whole number row ─────────────────────────────────────
        Row(
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Negative sign if needed
            if (isNegative) {
                Box(
                    modifier = Modifier.height(largeDigitHeight),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "−",
                        style = largeStyle,
                        color = color
                    )
                }
            }

            wholePart.forEach { char ->
                OdometerDigitStrip(
                    targetDigit = char.digitToInt(),
                    style = largeStyle,
                    color = color,
                    digitWidth = largeDigitWidth,
                    digitHeight = largeDigitHeight
                )
            }
        }

        // ── Decimal row (smaller, right-aligned) ─────────────────
        Row(
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Decimal point
            Box(
                modifier = Modifier.height(smallDigitHeight),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = ".",
                    style = smallStyle,
                    color = color
                )
            }

            decimalPart.forEach { char ->
                OdometerDigitStrip(
                    targetDigit = char.digitToInt(),
                    style = smallStyle,
                    color = color,
                    digitWidth = smallDigitWidth,
                    digitHeight = smallDigitHeight
                )
            }
        }
    }
}

@Composable
private fun OdometerDigitStrip(
    targetDigit: Int,
    style: TextStyle,
    color: Color,
    digitWidth: Dp = 48.dp,
    digitHeight: Dp = 68.dp
) {
    val density = LocalDensity.current
    val digitHeightPx = with(density) { digitHeight.toPx() }

    val listState = rememberLazyListState()

    // SLOW ROLL: Manually animate scroll offset with custom spring
    val targetScrollPx = targetDigit * digitHeightPx
    val scrollAnim = remember(targetDigit) { Animatable(0f) }

    LaunchedEffect(targetDigit) {
        scrollAnim.animateTo(
            targetValue = targetScrollPx,
            animationSpec = spring(dampingRatio = 0.65f, stiffness = 80f)
        )
    }

    // Drive LazyColumn scroll from the Animatable
    LaunchedEffect(scrollAnim.value) {
        listState.scrollToItem(
            index = (scrollAnim.value / digitHeightPx).toInt(),
            scrollOffset = (scrollAnim.value % digitHeightPx).toInt()
        )
    }

    LazyColumn(
        state = listState,
        modifier = Modifier
            .size(width = digitWidth, height = digitHeight)
            .clipToBounds(),
        userScrollEnabled = false,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        items(10) { digit ->
            Box(
                modifier = Modifier.size(width = digitWidth, height = digitHeight),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = digit.toString(),
                    style = style,
                    color = color
                )
            }
        }
    }
}