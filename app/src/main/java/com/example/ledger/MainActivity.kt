package com.example.ledger

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import android.graphics.Color
import androidx.activity.SystemBarStyle
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.LocalOverscrollConfiguration
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import com.example.ledger.data.LedgerPreferences
import com.example.ledger.ui.LedgerApp
import com.example.ledger.ui.LocalLedgerHaptics
import com.example.ledger.ui.rememberLedgerHaptics
import com.example.ledger.ui.screens.OnboardingScreen
import com.example.ledger.ui.theme.LedgerTheme

class MainActivity : ComponentActivity() {
    // Global state to hold the incoming shared receipt
    var sharedReceiptUri by mutableStateOf<Uri?>(null)
    var widgetAction by mutableStateOf<String?>(null)

    @OptIn(ExperimentalFoundationApi::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Catch intent on cold boot
        handleIntent(intent)

        // Forces completely transparent status and navigation bars for true edge-to-edge
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(Color.TRANSPARENT)
        )

        setContent {
            LedgerTheme {
                val context = androidx.compose.ui.platform.LocalContext.current
                val prefs = remember { LedgerPreferences(context) }
                val ledgerHaptics = rememberLedgerHaptics(prefs)
                var isOnboardingComplete by remember { mutableStateOf(prefs.isOnboardingComplete) }

                // Inject custom haptics and disable Android's default stretch overscroll globally
                CompositionLocalProvider(
                    LocalLedgerHaptics provides ledgerHaptics,
                    LocalOverscrollConfiguration provides null
                ) {
                    // The Root Canvas: A pure OLED Black surface
                    Surface(
                        modifier = Modifier.fillMaxSize(),
                        color = MaterialTheme.colorScheme.background
                    ) {
                        if (isOnboardingComplete) {
                            LedgerApp(sharedReceiptUri = sharedReceiptUri, initialWidgetAction = widgetAction)
                        } else {
                            OnboardingScreen(
                                onOnboardingComplete = { budget ->
                                    val parsed = budget.toDoubleOrNull()
                                    if (parsed != null && parsed > 0) {
                                        prefs.monthlyBudget = parsed
                                    }
                                    prefs.isOnboardingComplete = true
                                    isOnboardingComplete = true
                                }
                            )
                        }
                    }
                }
            }
        }
    }

    // Catch intent if the app is already running in the background
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleIntent(intent)
    }

    private fun handleIntent(intent: Intent) {
        if (intent.action == "com.example.ledger.OPEN_CAMERA") {
            widgetAction = "CAMERA"
        } else if (intent.action == "com.example.ledger.OPEN_TERMINAL") {
            widgetAction = "TERMINAL"
        } else if (intent.action == Intent.ACTION_SEND && intent.type?.startsWith("image/") == true) {
            val uri = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                intent.getParcelableExtra(Intent.EXTRA_STREAM, Uri::class.java)
            } else {
                @Suppress("DEPRECATION")
                intent.getParcelableExtra<Uri>(Intent.EXTRA_STREAM)
            }
            
            if (uri != null) {
                sharedReceiptUri = uri
            }
        }
    }
}