package com.example.ledger.ui.screens

import android.util.Log
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableDoubleStateOf
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ledger.data.Transaction
import com.example.ledger.ui.components.DailyBudgetBar
import com.example.ledger.ui.components.OdometerBalance
import com.example.ledger.ui.components.TerminalInputSheet
import com.example.ledger.ui.components.TransactionLedger
import com.example.ledger.ui.theme.LedgerTheme
import com.example.ledger.ui.theme.OledBlack
import com.example.ledger.ui.theme.SlateGrey
import com.example.ledger.ui.theme.StarkWhite
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.abs

// ── Constants ────────────────────────────────────────────────────
private const val TERMINAL_THRESHOLD_PX = 150f
private const val SCALE_MAX = 1.0f
private const val SCALE_MIN = 0.93f

/**
 * LedgerDashboard — The main screen of the Ledger app.
 *
 * Brutalist layout: no cards, no charts, no elevation, no borders.
 * The hierarchy is:
 *   1. Editorial date metadata (top)
 *   2. Giant odometer balance
 *   3. Ultra-thin daily budget bar
 *   4. Transaction ledger (scrollable LazyColumn)
 *
 * ## Pull-to-Terminal Gesture
 * The user pulls the entire dashboard downward to reveal the input terminal.
 * As the finger drags, the dashboard continuously scales down (1.0 → 0.93)
 * via [graphicsLayer], keeping the animation entirely in the draw phase.
 */
@Composable
fun LedgerDashboard(
    transactions: List<Transaction>,
    currentBalance: Double,
    monthlyBudget: Double = 5000.0,
    currencySymbol: String = "$",
    onTriggerTerminal: () -> Unit,
    onSwipeToTag: (String) -> Unit = {},
    onRenameTransaction: (String) -> Unit = {},
    onUpdateTransaction: (Transaction) -> Unit = {},
    onViewReceipt: (String) -> Unit = {},
    onDeleteTransaction: (String) -> Unit = {}
) {
    // Compute spend percentage from actual transaction data
    val spendPercentage = remember(transactions, monthlyBudget) {
        if (monthlyBudget <= 0.0) 0f
        else {
            val totalSpend = transactions.filter { it.amount < 0 }.sumOf { abs(it.amount) }
            (totalSpend / monthlyBudget).toFloat().coerceIn(0f, 1f)
        }
    }

    // ── Search & Filter State ────────────────────────────────────
    var searchQuery by remember { mutableStateOf("") }
    var isSearchVisible by remember { mutableStateOf(false) }
    val focusRequester = remember { FocusRequester() }

    LaunchedEffect(isSearchVisible) {
        if (isSearchVisible) {
            focusRequester.requestFocus()
        }
    }

    val filteredTransactions = remember(transactions, searchQuery) {
        if (searchQuery.isBlank()) transactions
        else {
            val query = searchQuery.lowercase()
            val dateFormatter = DateTimeFormatter.ofPattern("MMM d, yyyy h:mm a", Locale.ENGLISH)
            transactions.filter { tx ->
                val amountStr = tx.amount.toString()
                val noteMatch = tx.note.lowercase().contains(query)
                val tagsMatch = tx.tags.any { it.lowercase().contains(query) }
                val timeStr = Instant.ofEpochMilli(tx.timestamp).atZone(ZoneId.systemDefault()).format(dateFormatter).lowercase()
                amountStr.contains(query) || noteMatch || tagsMatch || timeStr.contains(query)
            }
        }
    }

    // ── Gesture state ────────────────────────────────────────────
    var dragOffset by remember { mutableFloatStateOf(0f) }
    var isDragging by remember { mutableStateOf(false) }
    var hasTriggeredHaptic by remember { mutableStateOf(false) }

    val haptic = LocalHapticFeedback.current

    // ── Spring-back animation ────────────────────────────────────
    val animatedOffset by animateFloatAsState(
        targetValue = if (isDragging) dragOffset else 0f,
        animationSpec = spring(
            dampingRatio = 0.6f,
            stiffness = 200f
        ),
        label = "pullSpringBack"
    )

    // ── Scale mapping math ───────────────────────────────────────
    //   progress = clamp(offset / threshold, 0, 1)
    //   scale    = 1.0 - progress × 0.07
    val activeOffset = if (isDragging) dragOffset else animatedOffset
    val progress = (activeOffset / TERMINAL_THRESHOLD_PX).coerceIn(0f, 1f)
    val scale = SCALE_MAX - progress * (SCALE_MAX - SCALE_MIN)

    // ── Gesture container ────────────────────────────────────────
    Box(
        modifier = Modifier
            .fillMaxSize()
            .pointerInput(Unit) {
                detectVerticalDragGestures(
                    onDragStart = {
                        isDragging = true
                        hasTriggeredHaptic = false
                    },
                    onVerticalDrag = { change, dragAmount ->
                        change.consume()
                        dragOffset = (dragOffset + dragAmount).coerceAtLeast(0f)

                        if (dragOffset >= TERMINAL_THRESHOLD_PX && !hasTriggeredHaptic) {
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            hasTriggeredHaptic = true
                        }
                    },
                    onDragEnd = {
                        if (dragOffset >= TERMINAL_THRESHOLD_PX) {
                            onTriggerTerminal()
                        }
                        isDragging = false
                        dragOffset = 0f
                    },
                    onDragCancel = {
                        isDragging = false
                        dragOffset = 0f
                    }
                )
            }
    ) {
        // ── Scaling container ────────────────────────────────────
        // graphicsLayer scaleX/Y → draw-phase only, zero layout cost.
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 24.dp)
                .graphicsLayer {
                    scaleX = scale
                    scaleY = scale
                },
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // ── Top Metadata ─────────────────────────────────────
            Spacer(modifier = Modifier.height(64.dp))

            MetadataDateHeader(
                isSearchVisible = isSearchVisible,
                onSearchToggle = {
                    isSearchVisible = !isSearchVisible
                    if (!isSearchVisible) {
                        searchQuery = "" // clear search when closing
                    }
                }
            )

            // ── Centerpiece: Odometer Balance ────────────────────
            Spacer(modifier = Modifier.height(48.dp))

            OdometerBalance(
                targetBalance = currentBalance,
                modifier = Modifier.align(Alignment.CenterHorizontally)
            )

            // ── Daily Budget Bar ─────────────────────────────────
            Spacer(modifier = Modifier.height(32.dp))

            DailyBudgetBar(
                spendPercentage = spendPercentage,
                modifier = Modifier.fillMaxWidth()
            )

            // ── Search Field ─────────────────────────────────────
            AnimatedVisibility(
                visible = isSearchVisible,
                enter = expandVertically(animationSpec = spring(dampingRatio = 0.7f, stiffness = 200f)) + fadeIn(),
                exit = shrinkVertically(animationSpec = spring(dampingRatio = 0.7f, stiffness = 200f)) + fadeOut(),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column {
                    Spacer(modifier = Modifier.height(32.dp))
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(OledBlack)
                            .border(1.dp, SlateGrey)
                            .padding(16.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = "> SEARCH: ",
                                style = MaterialTheme.typography.bodyLarge.copy(fontFamily = FontFamily.Monospace),
                                color = SlateGrey
                            )
                            BasicTextField(
                                value = searchQuery,
                                onValueChange = { searchQuery = it },
                                textStyle = MaterialTheme.typography.bodyLarge.copy(
                                    color = StarkWhite,
                                    fontFamily = FontFamily.Monospace
                                ),
                                cursorBrush = SolidColor(StarkWhite),
                                singleLine = true,
                                modifier = Modifier
                                    .weight(1f)
                                    .focusRequester(focusRequester)
                            )
                        }
                    }
                }
            }

            // ── Transaction Ledger ───────────────────────────────
            // 40dp breathing room above the list, then the scrollable
            // LazyColumn fills the remaining vertical space.
            Spacer(modifier = Modifier.height(40.dp))

            TransactionLedger(
                transactions = filteredTransactions,
                currencySymbol = currencySymbol,
                onSwipeToTag = onSwipeToTag,
                onRenameClick = onRenameTransaction,
                onUpdateTransaction = onUpdateTransaction,
                onViewReceipt = onViewReceipt,
                onDeleteTransaction = onDeleteTransaction,
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
            )
        }
    }
}

/**
 * MetadataDateHeader — Editorial date stamp.
 *
 * Renders the current date in uppercase abbreviated format (e.g., "WED, JUN 17")
 * with wide letter-spacing for a magazine masthead feel.
 * Uses [MaterialTheme.typography.labelSmall] in [SlateGrey].
 */
@Composable
private fun MetadataDateHeader(
    isSearchVisible: Boolean,
    onSearchToggle: () -> Unit
) {
    val today = remember {
        val formatter = DateTimeFormatter.ofPattern("EEE, MMM d", Locale.ENGLISH)
        LocalDate.now().format(formatter).uppercase(Locale.ENGLISH)
    }

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = today,
            style = MaterialTheme.typography.labelSmall.copy(
                letterSpacing = 3.sp
            ),
            color = SlateGrey
        )

        Text(
            text = if (isSearchVisible) "CLOSE" else "SEARCH",
            style = MaterialTheme.typography.labelSmall.copy(
                letterSpacing = 2.sp
            ),
            color = if (isSearchVisible) StarkWhite else SlateGrey,
            modifier = Modifier.clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onSearchToggle
            )
        )
    }
}

// ── Preview ──────────────────────────────────────────────────────
@Preview(
    showBackground = true,
    backgroundColor = 0xFF000000,
    widthDp = 412,
    heightDp = 892,
    name = "Ledger Dashboard"
)
@Composable
private fun LedgerDashboardPreview() {
    LedgerTheme {
        LedgerDashboard(
            transactions = emptyList(),
            currentBalance = 5000.0,
            monthlyBudget = 5000.0,
            onTriggerTerminal = {},
            onSwipeToTag = {}
        )
    }
}
