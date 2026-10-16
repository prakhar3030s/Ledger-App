package com.example.ledger.widget

import android.content.Context
import android.content.Intent
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.appwidget.action.actionStartActivity
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.provideContent
import androidx.glance.background
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxHeight
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.width
import androidx.glance.text.FontFamily
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider
import com.example.ledger.MainActivity
import com.example.ledger.data.LedgerPreferences
import com.example.ledger.data.TransactionStorage
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import java.time.LocalDate
import java.time.ZoneId
import java.util.Locale
import kotlin.math.abs
class BudgetWidget : GlanceAppWidget() {
    companion object {
        suspend fun forceUpdate(context: Context) {
            val manager = androidx.glance.appwidget.GlanceAppWidgetManager(context)
            manager.getGlanceIds(BudgetWidget::class.java).forEach { id ->
                androidx.glance.appwidget.state.updateAppWidgetState(context, id) { prefs ->
                    prefs[androidx.datastore.preferences.core.longPreferencesKey("update_trigger")] = System.currentTimeMillis()
                }
                BudgetWidget().update(context, id)
            }
        }
    }

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        provideContent {
            // Read state to force recomposition when update_trigger changes
            val statePrefs = androidx.glance.currentState<androidx.datastore.preferences.core.Preferences>()
            val updateTrigger = statePrefs[androidx.datastore.preferences.core.longPreferencesKey("update_trigger")] ?: 0L

            val ctx = androidx.glance.LocalContext.current
            val prefs = remember { LedgerPreferences(ctx) }
            val storage = remember { TransactionStorage(ctx) }
            
            val transactionsState = androidx.compose.runtime.produceState(initialValue = emptyList<com.example.ledger.data.Transaction>(), key1 = updateTrigger) {
                value = storage.loadAll()
            }
            val transactions = transactionsState.value
            val monthlyBudget = prefs.monthlyBudget
            
            val now = LocalDate.now()
            val monthStartMs = now.withDayOfMonth(1)
                .atStartOfDay(ZoneId.systemDefault())
                .toInstant()
                .toEpochMilli()

            val currentMonthExpenses = transactions
                .filter { it.timestamp >= monthStartMs && it.amount < 0 }
                .sumOf { abs(it.amount) }

            val balance = monthlyBudget - currentMonthExpenses
            
            val symbol = prefs.currencySymbol
            val absBalance = abs(balance)
            val formattedNum = String.format(Locale.US, "%,.0f", absBalance)
            val formattedRunway = if (balance >= 0.0) "$symbol$formattedNum" else "-$symbol$formattedNum"
            val asciiProgress = generateAsciiProgressBar(balance, monthlyBudget, 14)
            
            val cameraIntent = Intent(ctx, MainActivity::class.java).apply {
                action = "com.example.ledger.OPEN_CAMERA"
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            }
            
            val logIntent = Intent(ctx, MainActivity::class.java).apply {
                action = "com.example.ledger.OPEN_TERMINAL"
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            }

            Row(
                modifier = GlanceModifier
                    .fillMaxSize()
                    .background(Color(0xFF000000))
                    .padding(16.dp)
            ) {
                // 1. Left Column
                Column(
                    modifier = GlanceModifier.defaultWeight().fillMaxHeight(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "> CURRENT RUNWAY",
                        style = TextStyle(
                            color = ColorProvider(Color(0xFFAAAAAA)), // Slate Grey
                            fontFamily = FontFamily.Monospace,
                            fontSize = 12.sp
                        )
                    )
                    Spacer(modifier = GlanceModifier.height(8.dp))
                    Text(
                        text = formattedRunway,
                        style = TextStyle(
                            color = ColorProvider(Color(0xFFFFFFFF)), // White
                            fontFamily = FontFamily.Monospace,
                            fontSize = 32.sp,
                            fontWeight = FontWeight.Bold
                        )
                    )
                    Spacer(modifier = GlanceModifier.height(8.dp))
                    Text(
                        text = asciiProgress,
                        style = TextStyle(
                            color = ColorProvider(Color(0xFFD4FF00)), // Accent Yellow
                            fontFamily = FontFamily.Monospace,
                            fontSize = 12.sp
                        )
                    )
                }
                
                // 2. The Center Divider
                Box(
                    modifier = GlanceModifier
                        .fillMaxHeight()
                        .padding(horizontal = 16.dp)
                ) {
                    Spacer(
                        modifier = GlanceModifier
                            .width(1.dp)
                            .fillMaxHeight()
                            .background(Color(0xFF333333)) // Dark Grey
                    )
                }
                
                // 3. Right Column
                Column(
                    modifier = GlanceModifier.defaultWeight().fillMaxHeight(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Spacer(modifier = GlanceModifier.defaultWeight())
                    
                    // Action Button: INITIATE SCAN
                    Box(
                        modifier = GlanceModifier
                            .fillMaxWidth()
                            .clickable(actionStartActivity(cameraIntent))
                            .padding(vertical = 8.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "[ \uD83D\uDCF7 INITIATE SCAN ]",
                            style = TextStyle(
                                color = ColorProvider(Color(0xFFD4FF00)),
                                fontFamily = FontFamily.Monospace,
                                fontSize = 10.sp
                            )
                        )
                    }
                    
                    Spacer(modifier = GlanceModifier.height(8.dp))
                    
                    // Action Button: LOG
                    Box(
                        modifier = GlanceModifier
                            .fillMaxWidth()
                            .clickable(actionStartActivity(logIntent))
                            .padding(vertical = 8.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "[ \u2328 LOG ]", // ⌨
                            style = TextStyle(
                                color = ColorProvider(Color(0xFFD4FF00)),
                                fontFamily = FontFamily.Monospace,
                                fontSize = 10.sp
                            )
                        )
                    }
                    
                    Spacer(modifier = GlanceModifier.defaultWeight())
                }
            }
        }
    }
    
    private fun generateAsciiProgressBar(current: Double, total: Double, width: Int = 14): String {
        val ratio = if (total > 0) (current / total).coerceIn(0.0, 1.0) else 0.0
        val filledCount = (ratio * width).toInt()
        val emptyCount = width - filledCount
        val filledStr = "|".repeat(filledCount)
        val emptyStr = ".".repeat(emptyCount)
        return "[$filledStr$emptyStr]"
    }
}
