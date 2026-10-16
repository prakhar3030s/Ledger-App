package com.example.ledger.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ledger.data.Transaction
import com.example.ledger.ui.theme.AccentYellow
import com.example.ledger.ui.theme.SlateGrey
import com.example.ledger.ui.theme.StarkWhite
import java.text.NumberFormat
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.util.Locale
import kotlinx.coroutines.delay
import kotlin.math.abs

/**
 * Data structure holding the aggregated spend per tag.
 */
data class TagSpend(
    val tag: String,
    val totalAmount: Double,
    val percentage: Float
)

/**
 * TypographyInsightsView — A brutalist, text-only data visualization engine.
 *
 * Replaces traditional charts (pies, lines) with "Relational Typography".
 * Aggregates transactions by tag and renders hardware-accelerated text-bars.
 *
 * @param transactions The full ledger history.
 * @param modifier     Optional layout modifier.
 */
@Composable
fun TypographyInsightsView(
    transactions: List<Transaction>,
    monthlyBudget: Double,
    currencySymbol: String = "$",
    modifier: Modifier = Modifier
) {
    // ── Data Aggregation ─────────────────────────────────────────
    val aggregatedData = remember(transactions) {
        // We only care about expenses for insights.
        val expenses = transactions.filter { it.amount < 0 }
        val totalSpend = expenses.sumOf { abs(it.amount) }

        val spendByTag = mutableMapOf<String, Double>()

        expenses.forEach { txn ->
            if (txn.tags.isNotEmpty()) {
                // Split the transaction cost equally among its tags
                val splitAmount = abs(txn.amount) / txn.tags.size
                txn.tags.forEach { tag ->
                    spendByTag[tag] = spendByTag.getOrDefault(tag, 0.0) + splitAmount
                }
            } else {
                spendByTag["MISCELLANEOUS"] = spendByTag.getOrDefault("MISCELLANEOUS", 0.0) + abs(txn.amount)
            }
        }

        // Convert to percentage and sort highest to lowest
        spendByTag.map { (tag, amount) ->
            TagSpend(
                tag = tag,
                totalAmount = amount,
                percentage = if (totalSpend > 0) (amount / totalSpend).toFloat() else 0f
            )
        }.sortedByDescending { it.percentage }
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp, vertical = 32.dp),
        verticalArrangement = Arrangement.spacedBy(28.dp)
    ) {
        RunwayMetric(
            transactions = transactions,
            monthlyBudget = monthlyBudget,
            currencySymbol = currencySymbol,
            modifier = Modifier.fillMaxWidth()
        )

        ApexDrainMetric(
            apexTag = aggregatedData.firstOrNull(),
            currencySymbol = currencySymbol,
            modifier = Modifier.fillMaxWidth()
        )

        aggregatedData.forEachIndexed { index, tagSpend ->
            InsightRow(
                tag = tagSpend.tag,
                percentage = tagSpend.percentage,
                isHighest = index == 0, // Top spender gets AccentYellow
                index = index
            )
        }
    }
}

/**
 * InsightRow — A single bar in the Typography Insights chart.
 *
 * ## The "Static String" Trick:
 * To avoid the horrific layout churn and 10fps drops caused by re-rendering
 * string concatenations (e.g., `"|".repeat(progress)`), we pre-render a massive
 * string of pipes once.
 * We then place it in a Box that smoothly expands its [Modifier.fillMaxWidth]
 * bound using [clipToBounds]. The text is statically drawn, but the clipping window
 * slides open at a locked 120fps on the GPU.
 */
@Composable
private fun InsightRow(
    tag: String,
    percentage: Float,
    isHighest: Boolean,
    index: Int,
    modifier: Modifier = Modifier
) {
    // ── Animation Engine ─────────────────────────────────────────
    var animationTrigger by remember { mutableStateOf(false) }

    // CRITICAL FIX 7: Added a 250ms buffer to wait for the screen slide transition to finish,
    // plus a 50ms stagger per row to create a mechanical cascading effect.
    LaunchedEffect(Unit) {
        delay(250L + (index * 50L))
        animationTrigger = true
    }

    val fillProgress by animateFloatAsState(
        targetValue = if (animationTrigger) percentage else 0f,
        animationSpec = spring(dampingRatio = 0.7f, stiffness = 100f),
        label = "fillProgress"
    )

    Column(modifier = modifier.fillMaxWidth()) {
        // ── Top Metadata Row ─────────────────────────────────────
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.Bottom
        ) {
            Text(
                text = tag.uppercase(),
                style = MaterialTheme.typography.labelSmall,
                color = StarkWhite
            )

            // Percentage using the brutalist JetBrains Mono font
            Text(
                text = "${(percentage * 100).toInt()}%",
                style = MaterialTheme.typography.bodyMedium.copy(
                    fontFamily = MaterialTheme.typography.displayLarge.fontFamily
                ),
                color = StarkWhite
            )
        }

        Spacer(modifier = Modifier.height(6.dp))

        // ── The Text-Bar ─────────────────────────────────────────
        val barColor = if (isHighest) AccentYellow else SlateGrey
        // 120 pipes is more than enough to cover the widest screen without wrapping.
        val maxPipes = "||||||||||||||||||||||||||||||||||||||||||||||||||||||||||||||||||||||||||||||||||||||||||||||||||||||||||||||||||||||||||||||||||||||||||||"

        Box(
            modifier = Modifier
                // fillMaxWidth crashes if fraction is exactly 0.0f. Coerce to prevent layout crash.
                .fillMaxWidth(fillProgress.coerceIn(0.001f, 1f))
                .clipToBounds()
        ) {
            Text(
                text = maxPipes,
                style = MaterialTheme.typography.bodyMedium.copy(
                    fontFamily = MaterialTheme.typography.displayLarge.fontFamily
                ),
                color = barColor,
                maxLines = 1,
                softWrap = false,
                letterSpacing = 1.sp
            )
        }
    }
}


@Composable
fun RunwayMetric(
    transactions: List<Transaction>,
    monthlyBudget: Double,
    modifier: Modifier = Modifier,
    currencySymbol: String = "$"
) {
    val now = LocalDate.now()
    val daysInMonth = now.lengthOfMonth()
    val currentDay = now.dayOfMonth
    val daysRemaining = daysInMonth - currentDay

    val currentMonthValue = now.monthValue
    val currentYear = now.year

    val currentMonthExpenses = remember(transactions) {
        transactions.filter { tx ->
            if (tx.amount < 0) {
                val txDate = Instant.ofEpochMilli(tx.timestamp).atZone(ZoneId.systemDefault()).toLocalDate()
                txDate.monthValue == currentMonthValue && txDate.year == currentYear
            } else false
        }.sumOf { abs(it.amount) }
    }

    val remainingBudget = monthlyBudget - currentMonthExpenses
    val absBudget = abs(remainingBudget)
    val formattedNum = String.format(Locale.US, "%,.0f", absBudget)
    val formattedBudget = if (remainingBudget >= 0) "$currencySymbol$formattedNum" else "-$currencySymbol$formattedNum"

    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.Top
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = "RUNWAY",
                style = MaterialTheme.typography.labelSmall.copy(letterSpacing = 2.sp),
                color = SlateGrey
            )
            Spacer(modifier = Modifier.height(4.dp))
            AutoScalingMonospaceText(
                text = formattedBudget,
                color = StarkWhite
            )
        }
        
        Spacer(modifier = Modifier.width(24.dp))

        Column(horizontalAlignment = Alignment.End) {
            Text(
                text = "TIME TO RESET",
                style = MaterialTheme.typography.labelSmall.copy(letterSpacing = 2.sp),
                color = SlateGrey
            )
            Spacer(modifier = Modifier.height(4.dp))
            AutoScalingMonospaceText(
                text = "${daysRemaining} DAYS",
                color = AccentYellow
            )
        }
    }
}

@Composable
fun AutoScalingMonospaceText(
    text: String,
    color: Color,
    modifier: Modifier = Modifier
) {
    var multiplier by remember { mutableStateOf(1f) }

    Text(
        text = text,
        style = MaterialTheme.typography.displayLarge.copy(
            fontFamily = FontFamily.Monospace,
            fontSize = MaterialTheme.typography.displayLarge.fontSize * multiplier
        ),
        color = color,
        maxLines = 1,
        softWrap = false,
        onTextLayout = { textLayoutResult ->
            if (textLayoutResult.hasVisualOverflow) {
                multiplier *= 0.9f
            }
        },
        modifier = modifier
    )
}

@Composable
fun ApexDrainMetric(
    apexTag: TagSpend?,
    modifier: Modifier = Modifier,
    currencySymbol: String = "$"
) {
    if (apexTag == null || apexTag.totalAmount <= 0) return

    val formattedAmount = "$currencySymbol${String.format(Locale.US, "%.2f", apexTag.totalAmount)}"

    Box(
        modifier = modifier
            .background(
                color = AccentYellow.copy(alpha = 0.05f),
                shape = androidx.compose.foundation.shape.RoundedCornerShape(4.dp)
            )
            .border(
                width = 1.dp,
                color = AccentYellow,
                shape = androidx.compose.foundation.shape.RoundedCornerShape(4.dp)
            )
            .padding(16.dp)
    ) {
        Column {
            Text(
                text = "PRIMARY OUTFLOW DETECTED",
                style = MaterialTheme.typography.labelSmall.copy(letterSpacing = 2.sp),
                color = SlateGrey
            )
            Spacer(modifier = Modifier.height(12.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Bottom
            ) {
                val displayTag = if (apexTag.tag.startsWith("#")) apexTag.tag.uppercase() else "#${apexTag.tag.uppercase()}"
                Text(
                    text = displayTag,
                    style = MaterialTheme.typography.headlineMedium.copy(fontFamily = FontFamily.Monospace),
                    color = StarkWhite,
                    maxLines = 1,
                    modifier = Modifier.weight(1f)
                )
                Spacer(modifier = Modifier.width(16.dp))
                Text(
                    text = formattedAmount,
                    style = MaterialTheme.typography.headlineMedium.copy(fontFamily = FontFamily.Monospace),
                    color = AccentYellow,
                    maxLines = 1
                )
            }
        }
    }
}