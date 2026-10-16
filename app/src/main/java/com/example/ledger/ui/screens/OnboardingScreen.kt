package com.example.ledger.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ledger.ui.theme.StarkWhite
import java.text.NumberFormat
import java.util.Locale

@Composable
fun OnboardingScreen(
    onOnboardingComplete: (String) -> Unit
) {
    val oledBlack = Color(0xFF000000)
    val starkWhite = Color(0xFFFFFFFF)
    val haptic = LocalHapticFeedback.current

    var budgetAmount by remember { mutableStateOf("") }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(oledBlack)
            .statusBarsPadding()
            .navigationBarsPadding(),
        horizontalAlignment = Alignment.Start
    ) {
        Column(modifier = Modifier.padding(horizontal = 24.dp)) {
            Spacer(modifier = Modifier.height(48.dp))

            Text(
                text = "Set Monthly Budget",
                style = MaterialTheme.typography.displayLarge.copy(
                    fontSize = 32.sp,
                    fontFamily = MaterialTheme.typography.displayLarge.fontFamily
                ),
                color = starkWhite
            )
        }

        Spacer(modifier = Modifier.weight(1f))

        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.fillMaxWidth()
        ) {
            val parsed = budgetAmount.toDoubleOrNull() ?: 0.0
            val formatted = if (budgetAmount.isEmpty()) {
                "$0"
            } else {
                NumberFormat.getCurrencyInstance(Locale.US).apply {
                    maximumFractionDigits = if (budgetAmount.contains(".")) 2 else 0
                }.format(parsed)
            }

            Text(
                text = formatted,
                color = starkWhite,
                fontFamily = FontFamily.Monospace,
                fontSize = 48.sp,
                modifier = Modifier.padding(bottom = 32.dp)
            )

            BrutalistNumpad(
                onKeyPress = { key ->
                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                    if (key == "⌫") {
                        if (budgetAmount.isNotEmpty()) {
                            budgetAmount = budgetAmount.dropLast(1)
                        }
                    } else {
                        if (budgetAmount.length < 10) {
                            budgetAmount += key
                        }
                    }
                },
                onConfirm = {
                    val finalParsed = budgetAmount.toDoubleOrNull() ?: 0.0
                    if (finalParsed > 0) {
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        onOnboardingComplete(budgetAmount)
                    }
                },
                isConfirmEnabled = (budgetAmount.toDoubleOrNull() ?: 0.0) > 0
            )
        }
    }
}

@Composable
private fun BrutalistNumpad(
    onKeyPress: (String) -> Unit,
    onConfirm: () -> Unit,
    isConfirmEnabled: Boolean
) {
    val darkGrey = Color(0xFF1A1A1A)
    val accentYellow = Color(0xFFD4FF00)
    val starkWhite = Color(0xFFFFFFFF)
    val oledBlack = Color(0xFF000000)
    
    val keys = listOf(
        listOf("1", "2", "3"),
        listOf("4", "5", "6"),
        listOf("7", "8", "9"),
        listOf(".", "0", "⌫")
    )

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(oledBlack)
    ) {
        for (row in keys) {
            Row(modifier = Modifier.fillMaxWidth()) {
                for (key in row) {
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .height(72.dp)
                            .background(darkGrey)
                            .border(0.5.dp, oledBlack)
                            .clickable { onKeyPress(key) },
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = key,
                            color = starkWhite,
                            fontFamily = FontFamily.Monospace,
                            fontSize = 24.sp
                        )
                    }
                }
            }
        }

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(64.dp)
                .background(if (isConfirmEnabled) accentYellow else darkGrey)
                .clickable(enabled = isConfirmEnabled) { onConfirm() },
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = "[ CONFIRM BUDGET ]",
                color = if (isConfirmEnabled) oledBlack else Color.Gray,
                fontFamily = FontFamily.Monospace,
                fontSize = 16.sp,
                letterSpacing = 2.sp
            )
        }
    }
}
