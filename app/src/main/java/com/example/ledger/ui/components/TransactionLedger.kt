package com.example.ledger.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ledger.data.Transaction
import com.example.ledger.ui.theme.SlateGrey

@Composable
fun TransactionLedger(
    transactions: List<Transaction>,
    modifier: Modifier = Modifier,
    currencySymbol: String = "$",
    onSwipeToTag: (String) -> Unit = {},
    onRenameClick: (String) -> Unit = {},
    onUpdateTransaction: (Transaction) -> Unit = {},
    onViewReceipt: (String) -> Unit = {},
    onDeleteTransaction: (String) -> Unit = {}
) {
    if (transactions.isEmpty()) {
        Box(
            modifier = modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = "Swipe down to log transaction",
                style = MaterialTheme.typography.bodyMedium.copy(
                    letterSpacing = 1.sp,
                    color = SlateGrey
                ),
                textAlign = TextAlign.Center
            )
        }
    } else {
        LazyColumn(
            modifier = modifier,
            verticalArrangement = Arrangement.spacedBy(32.dp),
            contentPadding = PaddingValues(top = 32.dp, bottom = 120.dp)
        ) {
            items(
                items = transactions,
                key = { it.id }
            ) { transaction ->
                TransactionRow(
                    transaction = transaction,
                    currencySymbol = currencySymbol,
                    onCategorizeSwipe = onSwipeToTag,
                    onRenameClick = { onRenameClick(transaction.id) },
                    onUpdateTransaction = onUpdateTransaction,
                    onViewReceipt = onViewReceipt,
                    onDeleteTransaction = onDeleteTransaction
                )
            }
        }
    }
}
