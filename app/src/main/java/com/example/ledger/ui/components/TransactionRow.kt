package com.example.ledger.ui.components

import android.util.Log
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import android.content.Context
import android.net.Uri
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxState
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.Text
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import kotlinx.coroutines.launch
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextAlign
import androidx.core.content.FileProvider
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ledger.data.Transaction
import com.example.ledger.ui.theme.AccentYellow
import com.example.ledger.ui.theme.OledBlack
import com.example.ledger.ui.theme.SlateGrey
import com.example.ledger.ui.theme.StarkWhite
import java.text.NumberFormat
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import java.io.File
import java.util.UUID

@OptIn(ExperimentalLayoutApi::class, ExperimentalMaterial3Api::class)
@Composable
fun TransactionRow(
    transaction: Transaction,
    modifier: Modifier = Modifier,
    currencySymbol: String = "$",
    onCategorizeSwipe: (String) -> Unit = {},
    onRenameClick: () -> Unit = {},
    onUpdateTransaction: (Transaction) -> Unit = {},
    onViewReceipt: (String) -> Unit = {},
    onDeleteTransaction: (String) -> Unit = {}
) {
    var isExpanded by remember { mutableStateOf(false) }
    val haptic = LocalHapticFeedback.current

    val dismissState = rememberSwipeToDismissBoxState(
        positionalThreshold = { totalDistance -> totalDistance * 0.4f }
    )

    val scope = rememberCoroutineScope()

    LaunchedEffect(dismissState.targetValue) {
        if (dismissState.targetValue == SwipeToDismissBoxValue.EndToStart || dismissState.targetValue == SwipeToDismissBoxValue.StartToEnd) {
            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
        }
    }

    LaunchedEffect(dismissState.currentValue) {
        if (dismissState.currentValue == SwipeToDismissBoxValue.EndToStart) {
            onCategorizeSwipe(transaction.id)
            dismissState.reset()
        }
    }

    SwipeToDismissBox(
        state = dismissState,
        backgroundContent = {
            SwipeBackground(
                state = dismissState,
                onDeleteConfirm = { onDeleteTransaction(transaction.id) },
                onReset = { scope.launch { dismissState.reset() } }
            )
        },
        enableDismissFromStartToEnd = true,
        enableDismissFromEndToStart = true,
        modifier = modifier
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .defaultMinSize(minHeight = 72.dp)
                .background(OledBlack)
                .clickable {
                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                    isExpanded = !isExpanded
                }
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    val cleanNote = transaction.note.trim()
                    val isUntitled = cleanNote.isEmpty()
                    val displayNote = if (isUntitled) "Untitled Transaction" else cleanNote
                    val noteColor = if (isUntitled) Color(0xFF8E8E93) else Color(0xFFFFFFFF)

                    Text(
                        text = displayNote,
                        style = MaterialTheme.typography.bodyMedium,
                        color = noteColor,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )

                    if (transaction.tags.isNotEmpty()) {
                        Spacer(modifier = Modifier.height(8.dp))

                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            transaction.tags.forEach { tag ->
                                BrutalistTag(label = tag)
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.width(16.dp))

                Column(horizontalAlignment = Alignment.End) {
                    Text(
                        text = formatTransactionAmount(transaction.amount, currencySymbol),
                        style = MaterialTheme.typography.titleLarge.copy(fontFamily = FontFamily.Monospace),
                        color = StarkWhite
                    )

                    Spacer(modifier = Modifier.height(4.dp))

                    Text(
                        text = formatTransactionTime(transaction.timestamp),
                        style = MaterialTheme.typography.labelSmall,
                        color = SlateGrey
                    )
                }
            }

            AnimatedVisibility(
                visible = isExpanded,
                enter = expandVertically(
                    animationSpec = spring(dampingRatio = 0.7f, stiffness = 200f)
                ) + fadeIn(),
                exit = shrinkVertically(
                    animationSpec = spring(dampingRatio = 0.7f, stiffness = 200f)
                ) + fadeOut()
            ) {
                ExpandedMetadata(
                    transaction = transaction,
                    onRenameClick = onRenameClick,
                    onUpdateTransaction = onUpdateTransaction,
                    onViewReceipt = onViewReceipt
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SwipeBackground(
    state: SwipeToDismissBoxState,
    onDeleteConfirm: () -> Unit = {},
    onReset: () -> Unit = {}
) {
    val isStartToEnd = state.targetValue == SwipeToDismissBoxValue.StartToEnd || state.currentValue == SwipeToDismissBoxValue.StartToEnd

    if (isStartToEnd) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color(0xFFFF0033))
                .clickable { onReset() },
            contentAlignment = Alignment.CenterStart
        ) {
            Box(
                modifier = Modifier
                    .fillMaxHeight()
                    .clickable { onDeleteConfirm() }
                    .padding(horizontal = 24.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "[ CONFIRM PURGE ]",
                    style = MaterialTheme.typography.labelSmall.copy(
                        letterSpacing = 2.sp,
                        color = Color.White
                    )
                )
            }
        }
    } else {
        val targetScale = if (state.targetValue == SwipeToDismissBoxValue.EndToStart) 1.1f else 1.0f
        val scale by animateFloatAsState(
            targetValue = targetScale,
            animationSpec = spring(dampingRatio = 0.7f, stiffness = 200f),
            label = "bgScale"
        )

        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(AccentYellow)
                .padding(end = 24.dp),
            contentAlignment = Alignment.CenterEnd
        ) {
            Text(
                text = "ADD TAG",
                style = MaterialTheme.typography.labelSmall.copy(
                    letterSpacing = 2.sp,
                    color = OledBlack
                ),
                modifier = Modifier.graphicsLayer {
                    scaleX = scale
                    scaleY = scale
                }
            )
        }
    }
}

@Composable
private fun ExpandedMetadata(
    transaction: Transaction,
    onRenameClick: () -> Unit,
    onUpdateTransaction: (Transaction) -> Unit,
    onViewReceipt: (String) -> Unit
) {
    val dashedBorderColor = SlateGrey.copy(alpha = 0.3f)
    val haptic = LocalHapticFeedback.current
    val context = LocalContext.current

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 16.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "ID: TXN-${transaction.id.take(8).uppercase()}",
                style = MaterialTheme.typography.labelSmall,
                color = SlateGrey
            )
            Spacer(modifier = Modifier.width(16.dp))
            Text(
                text = "[ EDIT NAME ]",
                style = MaterialTheme.typography.labelSmall.copy(letterSpacing = 2.sp),
                color = StarkWhite,
                modifier = Modifier.clickable {
                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                    onRenameClick()
                }
            )
        }

        Spacer(modifier = Modifier.height(12.dp))

        if (transaction.receiptPath != null) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(100.dp)
                    .background(
                        color = SlateGrey.copy(alpha = 0.10f),
                        shape = RoundedCornerShape(4.dp)
                    )
                    .border(1.dp, StarkWhite, RoundedCornerShape(4.dp)),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = "RECEIPT STORED",
                        style = MaterialTheme.typography.labelSmall.copy(color = StarkWhite, letterSpacing = 3.sp)
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Row {
                        Text(
                            text = "[ VIEW ]",
                            style = MaterialTheme.typography.labelSmall.copy(color = AccentYellow, letterSpacing = 2.sp),
                            modifier = Modifier.clickable {
                                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                onViewReceipt(transaction.receiptPath)
                            }.padding(8.dp)
                        )
                        Spacer(modifier = Modifier.width(16.dp))
                        Text(
                            text = "[ PURGE ]",
                            style = MaterialTheme.typography.labelSmall.copy(color = SlateGrey, letterSpacing = 2.sp),
                            modifier = Modifier.clickable {
                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                try { File(transaction.receiptPath).delete() } catch (e: Exception) {}
                                onUpdateTransaction(transaction.copy(receiptPath = null))
                            }.padding(8.dp)
                        )
                    }
                }
            }
        } else {
            var showLaunchers by remember { mutableStateOf(false) }

            if (!showLaunchers) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(100.dp)
                        .background(
                            color = SlateGrey.copy(alpha = 0.10f),
                            shape = RoundedCornerShape(4.dp)
                        )
                        .clickable {
                            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            showLaunchers = true
                        }
                        .drawBehind {
                            drawRoundRect(
                                color = dashedBorderColor,
                                cornerRadius = CornerRadius(4.dp.toPx()),
                                style = Stroke(
                                    width = 1.dp.toPx(),
                                    pathEffect = PathEffect.dashPathEffect(
                                        intervals = floatArrayOf(8.dp.toPx(), 6.dp.toPx()),
                                        phase = 0f
                                    )
                                )
                            )
                        },
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "NO RECEIPT ATTACHED",
                        style = MaterialTheme.typography.labelSmall.copy(
                            color = SlateGrey,
                            letterSpacing = 3.sp
                        ),
                        textAlign = TextAlign.Center
                    )
                }
            } else {
                val galleryLauncher = rememberLauncherForActivityResult(
                    contract = ActivityResultContracts.GetContent()
                ) { uri ->
                    if (uri != null) {
                        try {
                            val inputStream = context.contentResolver.openInputStream(uri)
                            if (inputStream != null) {
                                val receiptsDir = File(context.filesDir, "receipts")
                                receiptsDir.mkdirs()
                                val destFile = File(receiptsDir, "${UUID.randomUUID()}.jpg")
                                destFile.outputStream().use { out -> inputStream.copyTo(out) }
                                onUpdateTransaction(transaction.copy(receiptPath = destFile.absolutePath))
                            }
                        } catch (e: Exception) {
                            // Ignored
                        }
                    }
                    showLaunchers = false
                }

                var pendingCameraPath by remember { mutableStateOf<String?>(null) }
                val cameraLauncher = rememberLauncherForActivityResult(
                    contract = ActivityResultContracts.TakePicture()
                ) { success ->
                    if (success && pendingCameraPath != null) {
                        onUpdateTransaction(transaction.copy(receiptPath = pendingCameraPath))
                    }
                    showLaunchers = false
                }

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(100.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight()
                            .background(OledBlack)
                            .border(1.dp, StarkWhite)
                            .clickable {
                                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                galleryLauncher.launch("image/*")
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "[ > GALLERY ]",
                            style = MaterialTheme.typography.labelSmall.copy(color = StarkWhite, letterSpacing = 2.sp)
                        )
                    }

                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight()
                            .background(OledBlack)
                            .border(1.dp, StarkWhite)
                            .clickable {
                                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                val receiptsDir = File(context.filesDir, "receipts")
                                receiptsDir.mkdirs()
                                val tempFile = File(receiptsDir, "${UUID.randomUUID()}.jpg")
                                pendingCameraPath = tempFile.absolutePath
                                val uri = FileProvider.getUriForFile(
                                    context,
                                    "${context.packageName}.fileprovider",
                                    tempFile
                                )
                                cameraLauncher.launch(uri)
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "[ > CAMERA ]",
                            style = MaterialTheme.typography.labelSmall.copy(color = StarkWhite, letterSpacing = 2.sp)
                        )
                    }
                }
            }
        }
    }
}

private fun formatTransactionAmount(amount: Double, currencySymbol: String = "$"): String {
    val absVal = kotlin.math.abs(amount)
    val formatted = String.format(Locale.US, "%.2f", absVal)
    return if (amount >= 0) {
        "+$currencySymbol$formatted"
    } else {
        "-$currencySymbol$formatted"
    }
}

private fun formatTransactionTime(timestamp: Long): String {
    val formatter = DateTimeFormatter.ofPattern("h:mm a", Locale.ENGLISH)
    return Instant.ofEpochMilli(timestamp)
        .atZone(ZoneId.systemDefault())
        .format(formatter)
}

// ── DEFINITIVE FIX: OCR Artifact Recovery + Phone Number Shield ──
suspend fun extractReceiptTotal(image: InputImage): Double? = suspendCancellableCoroutine { continuation ->
    val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
    var resumed = false

    fun safeResume(value: Double?) {
        try {
            recognizer.close()
        } catch (e: Exception) {
            Log.w("LedgerOCR", "Failed to close recognizer", e)
        }
        if (!resumed && continuation.isActive) {
            resumed = true
            continuation.resume(value)
        }
    }

    try {
        recognizer.process(image)
            .addOnSuccessListener { visionText ->
                val fullText = visionText.text
                Log.d("LedgerOCR", "=== RAW OCR TEXT ===\n$fullText\n====================")

                // Detect if we are looking at a digital UI rather than a physical receipt
                val isUpiScreen = fullText.contains("UPI", ignoreCase = true) || 
                                  fullText.contains("Google Pay", ignoreCase = true) ||
                                  fullText.contains("Completed", ignoreCase = true)

                // 1. Strict parser: annihilates phone numbers and transaction IDs
                fun parseNumber(raw: String): Double? {
                    val clean = raw.replace(",", "")
                    val hasDecimal = clean.contains(".")
                    
                    // Reject raw digit blocks longer than 6 characters (>= 1,000,000) if they lack a decimal.
                    // This instantly kills phone numbers (9199842) and transaction IDs (609186610463).
                    if (!hasDecimal && clean.length > 6) return null
                    
                    return clean.toDoubleOrNull()
                }

                val currencyRegex = Regex("""[\d,]+(?:\.\d{1,2})?""")

                // ── PASS 1: GIANT FONT HEURISTIC (With OCR Artifact Correction) ──
                var maxLineHeight = 0
                var avgHeight = 0
                var totalLines = 0

                // Find average and max heights to establish the spatial baseline
                for (block in visionText.textBlocks) {
                    for (line in block.lines) {
                        val h = line.boundingBox?.height() ?: 0
                        avgHeight += h
                        totalLines++
                        if (h > maxLineHeight) maxLineHeight = h
                    }
                }
                avgHeight = if (totalLines > 0) avgHeight / totalLines else 0

                val giantCandidates = mutableListOf<Double>()

                // Re-scan for lines that qualify as "Giant Font"
                for (block in visionText.textBlocks) {
                    for (line in block.lines) {
                        val h = line.boundingBox?.height() ?: 0
                        
                        // If text is 50% larger than the average screen text, it's the amount
                        if (h > avgHeight * 1.5) {
                            var rawText = line.text
                            
                            // ── OCR ARTIFACT RECOVERY ──
                            // If the giant text is very short (e.g. "₹I", "l", "O"), forcefully 
                            // correct the known ML Kit single-digit misreads before regex parsing.
                            if (rawText.length <= 5) {
                                rawText = rawText.replace(Regex("[Il|]"), "1").replace(Regex("[oO]"), "0")
                            }
                            
                            val matches = currencyRegex.findAll(rawText)
                            for (match in matches) {
                                val amount = parseNumber(match.value)
                                if (amount != null && amount > 0) {
                                    giantCandidates.add(amount)
                                }
                            }
                        }
                    }
                }

                if (giantCandidates.isNotEmpty()) {
                    val upiAmount = giantCandidates.maxOrNull()
                    Log.d("LedgerOCR", "Giant Font Match: $upiAmount")
                    safeResume(upiAmount)
                    return@addOnSuccessListener
                }

                // ── PASS 2: KEYWORD HEURISTIC (For flat physical receipts) ──
                val lines = fullText.split('\n', '\r')
                val totalKeywords = listOf("total", "amount", "due", "balance", "grand", "sum", "charge", "final")
                var keywordTotal: Double? = null

                for (line in lines) {
                    val lower = line.lowercase()
                    if ("sub" in lower && "total" in lower) continue

                    if (totalKeywords.any { it in lower }) {
                        val matches = currencyRegex.findAll(line).toList()
                        if (matches.isNotEmpty()) {
                            val amount = matches.mapNotNull { parseNumber(it.value) }.maxOrNull()
                            if (amount != null && amount > 0) {
                                if (keywordTotal == null || amount > keywordTotal) {
                                    keywordTotal = amount
                                }
                            }
                        }
                    }
                }

                if (keywordTotal != null) {
                    Log.d("LedgerOCR", "Keyword Match: $keywordTotal")
                    safeResume(keywordTotal)
                    return@addOnSuccessListener
                }

                // ── PASS 3: ABSOLUTE FALLBACK (Dangerous on UPI screens) ──
                if (isUpiScreen) {
                    // CRITICAL FIX: If it's a UPI screen and we didn't find a Giant Font, DO NOT guess randomly.
                    // Guessing randomly leads to extracting rogue phone number chunks.
                    Log.d("LedgerOCR", "UPI Screen detected but no giant font found. Aborting fallback to prevent ID leak.")
                    safeResume(null)
                    return@addOnSuccessListener
                }

                val allMatches = currencyRegex.findAll(fullText)
                val fallbackMax = allMatches.mapNotNull { parseNumber(it.value) }.maxOrNull()

                Log.d("LedgerOCR", "Fallback Max: $fallbackMax")
                safeResume(fallbackMax)
            }
            .addOnFailureListener { e ->
                Log.e("LedgerOCR", "ML Kit failed", e)
                safeResume(null)
            }
    } catch (e: Exception) {
        Log.e("LedgerOCR", "ML Kit init failed", e)
        safeResume(null)
    }
}