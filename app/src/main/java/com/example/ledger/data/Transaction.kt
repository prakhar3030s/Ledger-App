package com.example.ledger.data

import androidx.compose.runtime.Immutable

/**
 * Transaction — A single financial event in the ledger.
 *
 * @param id        Unique identifier for stable LazyColumn keying.
 * @param amount    Signed value: negative = expense, positive = income.
 * @param note      Free-text description entered by the user.
 * @param tags      Slash-extracted category tags (e.g., "transport", "food").
 * @param timestamp Unix epoch millis — used for time display and sorting.
 */
@Immutable
data class Transaction(
    val id: String,
    val amount: Double,
    val note: String,
    val tags: List<String>,
    val timestamp: Long,
    val receiptPath: String? = null
)
