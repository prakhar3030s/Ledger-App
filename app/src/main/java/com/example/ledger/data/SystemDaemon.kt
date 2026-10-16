package com.example.ledger.data

import androidx.compose.runtime.Immutable

/**
 * SystemDaemon — A recurring transaction scheduled to fire on a specific day each month.
 *
 * @param id          Unique identifier for the daemon.
 * @param amount      The transaction value, typically an expense (negative).
 * @param note        Description of the daemon (e.g., "Netflix").
 * @param tags        Extracted category tags.
 * @param triggerDay  The day of the month (1-31) when this daemon should inject the transaction.
 */
@Immutable
data class SystemDaemon(
    val id: String,
    val amount: Double,
    val note: String,
    val tags: List<String>,
    val triggerDay: Int
)
