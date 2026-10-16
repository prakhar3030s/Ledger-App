package com.example.ledger.ui

import android.content.Context
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.platform.LocalContext
import com.example.ledger.data.LedgerPreferences

/**
 * Interface for Ledger's custom deterministic haptic engine.
 * Bypasses generic Compose haptics for raw, mechanical feedback.
 */
interface LedgerHaptics {
    /** A very light, crisp vibration. (e.g., keystrokes, scrolling past thresholds). */
    fun tick()
    
    /** A sharp, medium vibration. (e.g., Pull-to-Terminal threshold, Swipe detents). */
    fun snap()
    
    /** A heavy, dense vibration. (e.g., expanding a row, dropping a drawer). */
    fun thud()
    
    /** A quick double-tap. (e.g., committing a new transaction). */
    fun successPulse()
}

/**
 * Global CompositionLocal so the engine can be accessed anywhere without prop-drilling.
 */
val LocalLedgerHaptics = staticCompositionLocalOf<LedgerHaptics> {
    error("No LedgerHaptics provided. Ensure your app is wrapped in CompositionLocalProvider(LocalLedgerHaptics provides haptics).")
}

/**
 * Initializes and remembers the Haptic Engine using Android's native Vibrator APIs.
 */
@Composable
fun rememberLedgerHaptics(prefs: LedgerPreferences? = null): LedgerHaptics {
    val context = LocalContext.current
    return remember(context, prefs) {
        val vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val vibratorManager = context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager
            vibratorManager.defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            context.getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
        }
        
        LedgerHapticsImpl(vibrator, prefs)
    }
}

/**
 * The concrete implementation of the mechanical haptic profiles.
 * Respects user settings in LedgerPreferences.
 */
private class LedgerHapticsImpl(
    private val vibrator: Vibrator?,
    private val prefs: LedgerPreferences?
) : LedgerHaptics {

    override fun tick() {
        if (prefs?.hapticKeyTicks == false) return
        if (vibrator == null || !vibrator.hasVibrator()) return
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            vibrator.vibrate(VibrationEffect.createPredefined(VibrationEffect.EFFECT_TICK))
        } else {
            @Suppress("DEPRECATION")
            vibrator.vibrate(VibrationEffect.createOneShot(10L, VibrationEffect.DEFAULT_AMPLITUDE))
        }
    }

    override fun snap() {
        if (prefs?.hapticInterfaceSnaps == false) return
        if (vibrator == null || !vibrator.hasVibrator()) return
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            vibrator.vibrate(VibrationEffect.createPredefined(VibrationEffect.EFFECT_CLICK))
        } else {
            @Suppress("DEPRECATION")
            vibrator.vibrate(VibrationEffect.createOneShot(20L, VibrationEffect.DEFAULT_AMPLITUDE))
        }
    }

    override fun thud() {
        if (prefs?.hapticImpactThuds == false) return
        if (vibrator == null || !vibrator.hasVibrator()) return
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            vibrator.vibrate(VibrationEffect.createPredefined(VibrationEffect.EFFECT_HEAVY_CLICK))
        } else {
            @Suppress("DEPRECATION")
            // 40ms duration with heavier amplitude for a dull thud.
            vibrator.vibrate(VibrationEffect.createOneShot(40L, 255))
        }
    }

    override fun successPulse() {
        if (prefs?.hapticImpactThuds == false && prefs.hapticInterfaceSnaps == false) return
        if (vibrator == null || !vibrator.hasVibrator()) return
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            vibrator.vibrate(VibrationEffect.createPredefined(VibrationEffect.EFFECT_DOUBLE_CLICK))
        } else {
            @Suppress("DEPRECATION")
            // Pattern: wait 0ms, vibrate 20ms, pause 40ms, vibrate 20ms. No repeat (-1).
            vibrator.vibrate(longArrayOf(0, 20, 40, 20), -1)
        }
    }
}
