package com.example.ledger.data

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/**
 * TransactionStorage — JSON file-based persistence for transactions.
 *
 * Stores all transactions as a JSON array in internal storage.
 * Uses atomic file operations and Dispatchers.IO for asynchronous non-blocking storage.
 */
class TransactionStorage(private val context: Context) {

    private val file = File(context.filesDir, "transactions.json")

    /** Load all transactions from disk asynchronously, sorted by most recent first. */
    suspend fun loadAll(): List<Transaction> = withContext(Dispatchers.IO) {
        synchronized(this@TransactionStorage) {
            if (!file.exists()) return@synchronized emptyList()
            try {
                val json = file.readText()
                if (json.isBlank()) return@synchronized emptyList()
                val array = JSONArray(json)
                (0 until array.length()).map { i ->
                    val obj = array.getJSONObject(i)
                    Transaction(
                        id = obj.getString("id"),
                        amount = obj.getDouble("amount"),
                        note = obj.getString("note"),
                        tags = (0 until obj.getJSONArray("tags").length()).map { j ->
                            obj.getJSONArray("tags").getString(j)
                        },
                        timestamp = obj.getLong("timestamp"),
                        receiptPath = if (obj.has("receiptPath") && !obj.isNull("receiptPath")) obj.getString("receiptPath") else null
                    )
                }.sortedByDescending { it.timestamp }
            } catch (e: Exception) {
                emptyList()
            }
        }
    }

    /** Save the entire transaction list to disk atomically on IO dispatcher. */
    private suspend fun saveAll(transactions: List<Transaction>) = withContext(Dispatchers.IO) {
        synchronized(this@TransactionStorage) {
            try {
                val array = JSONArray()
                transactions.forEach { tx ->
                    val obj = JSONObject().apply {
                        put("id", tx.id)
                        put("amount", tx.amount)
                        put("note", tx.note)
                        put("tags", JSONArray(tx.tags))
                        put("timestamp", tx.timestamp)
                        if (tx.receiptPath != null) {
                            put("receiptPath", tx.receiptPath)
                        }
                    }
                    array.put(obj)
                }

                // Atomic write via temp file
                val tempFile = File.createTempFile("txns_", ".tmp", context.filesDir)
                tempFile.writeText(array.toString())

                if (tempFile.exists()) {
                    if (!tempFile.renameTo(file)) {
                        tempFile.copyTo(file, overwrite = true)
                        tempFile.delete()
                    }
                }
            } catch (e: Exception) {
                android.util.Log.e("TransactionStorage", "Failed atomic file save", e)
            }

            // Trigger Glance widget update
            kotlinx.coroutines.CoroutineScope(Dispatchers.IO).launch {
                com.example.ledger.widget.BudgetWidget.forceUpdate(context)
            }
        }
    }

    /** Insert a new transaction and persist asynchronously. */
    suspend fun insert(transaction: Transaction) {
        val current = loadAll().toMutableList()
        current.add(0, transaction)
        saveAll(current)
    }

    /** Update an existing transaction (matched by id) and persist asynchronously. */
    suspend fun update(transaction: Transaction) {
        val current = loadAll().map { tx ->
            if (tx.id == transaction.id) transaction else tx
        }
        saveAll(current)
    }

    /** Delete a transaction by id and persist asynchronously. */
    suspend fun delete(id: String) {
        val current = loadAll().filter { it.id != id }
        saveAll(current)
    }
}
