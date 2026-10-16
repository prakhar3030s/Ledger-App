package com.example.ledger.data

import android.content.Context
import android.content.SharedPreferences
import androidx.glance.appwidget.updateAll
import kotlinx.coroutines.launch

/**
 * LedgerPreferences — SharedPreferences wrapper for app settings.
 *
 * Stores: monthly budget, tag thresholds, haptic preferences.
 * All values persist across app restarts.
 */
class LedgerPreferences(private val context: Context) {

    private val prefs: SharedPreferences =
        context.applicationContext.getSharedPreferences("ledger_prefs", Context.MODE_PRIVATE)

    companion object {
        private const val KEY_MONTHLY_BUDGET = "monthly_budget"
        private const val KEY_HAPTIC_KEY_TICKS = "haptic_key_ticks"
        private const val KEY_HAPTIC_INTERFACE_SNAPS = "haptic_interface_snaps"
        private const val KEY_HAPTIC_IMPACT_THUDS = "haptic_impact_thuds"
        private const val KEY_TAG_THRESHOLDS = "tag_thresholds"
        private const val KEY_SYSTEM_DAEMONS = "system_daemons"
        private const val KEY_LAST_DAEMON_RUN = "last_daemon_run"
        private const val KEY_ONBOARDING_COMPLETE = "onboarding_complete"
        private const val KEY_CURRENCY_SYMBOL = "currency_symbol"

        private const val DEFAULT_MONTHLY_BUDGET = 5000.0
    }

    var currencySymbol: String
        get() = prefs.getString(KEY_CURRENCY_SYMBOL, "$") ?: "$"
        set(value) {
            prefs.edit().putString(KEY_CURRENCY_SYMBOL, value).apply()
            kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.IO).launch {
                com.example.ledger.widget.BudgetWidget.forceUpdate(context)
            }
        }

    var lastDaemonRunTimestamp: Long
        get() = prefs.getLong(KEY_LAST_DAEMON_RUN, 0L)
        set(value) = prefs.edit().putLong(KEY_LAST_DAEMON_RUN, value).apply()

    var systemDaemons: List<SystemDaemon>
        get() {
            val raw = prefs.getString(KEY_SYSTEM_DAEMONS, "[]") ?: "[]"
            return try {
                val array = org.json.JSONArray(raw)
                (0 until array.length()).map { i ->
                    val obj = array.getJSONObject(i)
                    SystemDaemon(
                        id = obj.getString("id"),
                        amount = obj.getDouble("amount"),
                        note = obj.getString("note"),
                        tags = (0 until obj.getJSONArray("tags").length()).map { j ->
                            obj.getJSONArray("tags").getString(j)
                        },
                        triggerDay = obj.getInt("triggerDay")
                    )
                }
            } catch (e: Exception) {
                emptyList()
            }
        }
        set(value) {
            val array = org.json.JSONArray()
            value.forEach { daemon ->
                val obj = org.json.JSONObject().apply {
                    put("id", daemon.id)
                    put("amount", daemon.amount)
                    put("note", daemon.note)
                    put("tags", org.json.JSONArray(daemon.tags))
                    put("triggerDay", daemon.triggerDay)
                }
                array.put(obj)
            }
            prefs.edit().putString(KEY_SYSTEM_DAEMONS, array.toString()).apply()
        }

    var monthlyBudget: Double
        get() = Double.fromBits(prefs.getLong(KEY_MONTHLY_BUDGET, DEFAULT_MONTHLY_BUDGET.toBits()))
        set(value) {
            prefs.edit().putLong(KEY_MONTHLY_BUDGET, value.toBits()).apply()
            
            // Trigger Glance widget update by forcing recomposition state
            kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.IO).launch {
                com.example.ledger.widget.BudgetWidget.forceUpdate(context)
            }
        }
        
    var isOnboardingComplete: Boolean
        get() = prefs.getBoolean(KEY_ONBOARDING_COMPLETE, false)
        set(value) = prefs.edit().putBoolean(KEY_ONBOARDING_COMPLETE, value).apply()

    var hapticKeyTicks: Boolean
        get() = prefs.getBoolean(KEY_HAPTIC_KEY_TICKS, true)
        set(value) = prefs.edit().putBoolean(KEY_HAPTIC_KEY_TICKS, value).apply()

    var hapticInterfaceSnaps: Boolean
        get() = prefs.getBoolean(KEY_HAPTIC_INTERFACE_SNAPS, true)
        set(value) = prefs.edit().putBoolean(KEY_HAPTIC_INTERFACE_SNAPS, value).apply()

    var hapticImpactThuds: Boolean
        get() = prefs.getBoolean(KEY_HAPTIC_IMPACT_THUDS, true)
        set(value) = prefs.edit().putBoolean(KEY_HAPTIC_IMPACT_THUDS, value).apply()

    var tagThresholds: Map<String, Double>
        get() {
            val raw = prefs.getString(KEY_TAG_THRESHOLDS, "") ?: ""
            if (raw.isBlank()) return mapOf("TRANSPORT" to 200.0, "FOOD" to 600.0)
            return raw.split(",").mapNotNull { entry ->
                val parts = entry.split(":")
                if (parts.size == 2) {
                    parts[0] to (parts[1].toDoubleOrNull() ?: 0.0)
                } else null
            }.toMap()
        }
        set(value) {
            val raw = value.entries.joinToString(",") { "${it.key}:${it.value}" }
            prefs.edit().putString(KEY_TAG_THRESHOLDS, raw).apply()
        }
}
