package com.example.ledger.ui.components

import android.media.ExifInterface
import android.util.Log
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.FileProvider
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import java.io.File
import java.util.UUID
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ledger.ui.theme.AccentYellow
import com.example.ledger.ui.theme.SlateGrey
import com.example.ledger.ui.theme.StarkWhite
import com.example.ledger.ui.theme.OledBlack

private enum class InputMode { AMOUNT, NOTE }

@Composable
fun TerminalInputSheet(
    isVisible: Boolean,
    onSubmit: (amount: Double, note: String, receiptPath: String?) -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    sharedReceiptUri: android.net.Uri? = null,
    startInCameraMode: Boolean = false
) {
    if (isVisible) {
        BackHandler { onDismiss() }
    }

    AnimatedVisibility(
        visible = isVisible,
        enter = slideInVertically(
            initialOffsetY = { fullHeight -> fullHeight },
            animationSpec = spring(dampingRatio = 0.7f, stiffness = 200f)
        ),
        exit = slideOutVertically(
            targetOffsetY = { fullHeight -> fullHeight },
            animationSpec = spring(dampingRatio = 0.8f, stiffness = 300f)
        ),
        modifier = modifier
    ) {
        TerminalContent(
            onSubmit = onSubmit, 
            onDismiss = onDismiss, 
            sharedReceiptUri = sharedReceiptUri,
            startInCameraMode = startInCameraMode
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun TerminalContent(
    onSubmit: (amount: Double, note: String, receiptPath: String?) -> Unit,
    onDismiss: () -> Unit,
    sharedReceiptUri: android.net.Uri? = null,
    startInCameraMode: Boolean = false
) {
    var inputMode by rememberSaveable { mutableStateOf(InputMode.AMOUNT) }
    var amountText by rememberSaveable { mutableStateOf("") }
    var noteFieldValue by remember { mutableStateOf(TextFieldValue("")) }
    var activeTags by rememberSaveable { mutableStateOf(listOf<String>()) }

    val context = LocalContext.current
    var isScanning by rememberSaveable { mutableStateOf(false) }
    var showHardwareCamera by rememberSaveable { mutableStateOf(startInCameraMode) }
    var cameraPermissionDenied by rememberSaveable { mutableStateOf(false) }
    var scannedAmount by rememberSaveable { mutableStateOf<Double?>(null) }
    var pendingCameraPath by rememberSaveable { mutableStateOf<String?>(null) }
    var capturedReceiptPath by rememberSaveable { mutableStateOf<String?>(null) }
    var scanError by rememberSaveable { mutableStateOf(false) }

    val haptic = LocalHapticFeedback.current
    val amountFocusRequester = remember { FocusRequester() }
    val noteFocusRequester = remember { FocusRequester() }
    val scope = rememberCoroutineScope()

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = androidx.activity.result.contract.ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            showHardwareCamera = true
            cameraPermissionDenied = false
        } else {
            cameraPermissionDenied = true
            scanError = true
        }
    }

    LaunchedEffect(sharedReceiptUri) {
        if (sharedReceiptUri != null && !isScanning) {
            isScanning = true
            scanError = false
            scannedAmount = null
            
            scope.launch(kotlinx.coroutines.Dispatchers.IO) {
                var extractedTotal: Double? = null
                var processFailed = false

                try {
                    // ML Kit natively handles URIs perfectly, no BitmapFactory needed!
                    val image = com.google.mlkit.vision.common.InputImage.fromFilePath(context, sharedReceiptUri)
                    extractedTotal = extractReceiptTotal(image)
                    android.util.Log.d("LedgerShare", "Shared OCR result: $extractedTotal")
                } catch (e: Exception) {
                    android.util.Log.e("LedgerShare", "Failed to process shared image", e)
                    processFailed = true
                }

                kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) {
                    isScanning = false
                    if (extractedTotal != null && extractedTotal > 0) {
                        scannedAmount = extractedTotal
                        // Convert URI to string path so it can be saved in your database
                        capturedReceiptPath = sharedReceiptUri.toString()
                        scanError = false
                    } else if (processFailed || extractedTotal == null) {
                        scanError = true
                        scannedAmount = null
                    }
                }
            }
        }
    }

    val processCapturedReceipt = { path: String ->
        pendingCameraPath = path
        showHardwareCamera = false
        isScanning = true
        scanError = false
        scannedAmount = null

        scope.launch(kotlinx.coroutines.Dispatchers.IO) {
            var extractedTotal: Double? = null
            var processFailed = false

            try {
                val file = java.io.File(path)

                if (!file.exists() || file.length() == 0L) {
                    android.util.Log.e("LedgerScan", "Hardware fault: Camera wrote an empty/corrupt file.")
                    processFailed = true
                } else {
                    val boundsOptions = android.graphics.BitmapFactory.Options().apply {
                        inJustDecodeBounds = true
                    }
                    android.graphics.BitmapFactory.decodeFile(path, boundsOptions)

                    val targetWidth = 1280
                    val sampleSize = if (boundsOptions.outWidth > targetWidth) {
                        (boundsOptions.outWidth.toFloat() / targetWidth).toInt().coerceAtLeast(1)
                    } else 1

                    val decodeOptions = android.graphics.BitmapFactory.Options().apply {
                        inSampleSize = sampleSize
                    }
                    val bitmap = android.graphics.BitmapFactory.decodeFile(path, decodeOptions)

                    if (bitmap != null) {
                        val rotation = try {
                            val exif = android.media.ExifInterface(path)
                            when (exif.getAttributeInt(android.media.ExifInterface.TAG_ORIENTATION, android.media.ExifInterface.ORIENTATION_NORMAL)) {
                                android.media.ExifInterface.ORIENTATION_ROTATE_90 -> 90
                                android.media.ExifInterface.ORIENTATION_ROTATE_180 -> 180
                                android.media.ExifInterface.ORIENTATION_ROTATE_270 -> 270
                                else -> 0
                            }
                        } catch (e: Exception) {
                            android.util.Log.w("LedgerScan", "EXIF failed", e)
                            0
                        }

                        // Apply Rotation & Overwrite to permanently fix the orientation and reduce size
                        var finalBitmap = bitmap
                        if (rotation != 0) {
                            val matrix = android.graphics.Matrix().apply { postRotate(rotation.toFloat()) }
                            finalBitmap = android.graphics.Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
                            if (finalBitmap != bitmap && !bitmap.isRecycled) {
                                bitmap.recycle()
                            }
                        }

                        // Compress and Overwrite the file with the downscaled, correctly-oriented image
                        java.io.FileOutputStream(file).use { out ->
                            finalBitmap.compress(android.graphics.Bitmap.CompressFormat.JPEG, 70, out)
                        }

                        // ML Kit now takes the corrected bitmap with 0 rotation
                        val image = com.google.mlkit.vision.common.InputImage.fromBitmap(finalBitmap, 0)
                        extractedTotal = extractReceiptTotal(image)
                        if (!finalBitmap.isRecycled) {
                            finalBitmap.recycle()
                        }
                        android.util.Log.d("LedgerScan", "OCR result: $extractedTotal")
                    } else {
                        android.util.Log.e("LedgerScan", "BitmapFactory returned null")
                        processFailed = true
                    }
                }
            } catch (e: Throwable) {
                android.util.Log.e("LedgerScan", "Pipeline crashed", e)
                processFailed = true
            }

            kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) {
                isScanning = false

                if (extractedTotal != null && extractedTotal > 0) {
                    scannedAmount = extractedTotal
                    capturedReceiptPath = pendingCameraPath
                    scanError = false
                } else if (processFailed || extractedTotal == null) {
                    scanError = true
                    scannedAmount = null
                    pendingCameraPath = null
                }
            }
        }
    }

    LaunchedEffect(Unit) { amountFocusRequester.requestFocus() }

    LaunchedEffect(inputMode, scannedAmount) {
        if (inputMode == InputMode.NOTE && scannedAmount == null) {
            // Increased delay prevents Android OS from miscalculating the keyboard inset bounds 
            // when swapping out the DETECTED scan block.
            kotlinx.coroutines.delay(150) 
            try {
                noteFocusRequester.requestFocus()
            } catch (_: IllegalStateException) {}
        }
    }

    val pivotProgress by animateFloatAsState(
        targetValue = if (inputMode == InputMode.NOTE) 1f else 0f,
        animationSpec = spring(dampingRatio = 0.65f, stiffness = 200f),
        label = "pivotProgress"
    )

    val amountStyle = TextStyle(
        fontFamily = MaterialTheme.typography.displayLarge.fontFamily,
        fontSize = 56.sp,
        color = StarkWhite
    )
    val noteStyle = MaterialTheme.typography.bodyMedium.copy(color = StarkWhite)

    val handleNumpadKey = { key: String ->
        if (key == "BACKSPACE") {
            if (amountText.isNotEmpty()) {
                amountText = amountText.dropLast(1)
            }
        } else {
            amountText = filterAmountInput(amountText + key)
        }
    }

    val pivotToNote = fun(): Boolean {
        var cleanAmount = amountText.trim()
        val hasOperator = cleanAmount.any { it == '+' || it == '-' || it == '*' || it == '/' }
        if (hasOperator) {
            try {
                val evaluated = evaluateExpression(cleanAmount)
                cleanAmount = if (evaluated % 1.0 == 0.0) evaluated.toLong().toString() else evaluated.toString()
            } catch (e: Exception) {
                return false
            }
        }
        var finalAmount = cleanAmount.toDoubleOrNull() ?: 0.0
        if (finalAmount > 10000000.0) {
            finalAmount = 10000000.0
            cleanAmount = if (finalAmount % 1.0 == 0.0) finalAmount.toLong().toString() else finalAmount.toString()
        }
        if (finalAmount == 0.0) {
            return false
        }
        amountText = cleanAmount
        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
        inputMode = InputMode.NOTE
        return true
    }

    val commitTransaction = {
        var cleanAmountString = amountText.trim()
        val hasOperator = cleanAmountString.any { it == '+' || it == '-' || it == '*' || it == '/' }
        if (hasOperator) {
            try {
                val evaluated = evaluateExpression(cleanAmountString)
                cleanAmountString = if (evaluated % 1.0 == 0.0) evaluated.toLong().toString() else evaluated.toString()
                amountText = cleanAmountString
                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
            } catch (e: Exception) {
                cleanAmountString = "0.0"
                amountText = cleanAmountString
            }
        }
        var amount = cleanAmountString.toDoubleOrNull() ?: 0.0
        if (amount > 10000000.0) {
            amount = 10000000.0
        }

        if (amount > 0.0) {
            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
            val finalNote = buildString {
                val text = noteFieldValue.text.trim()
                if (text.isNotEmpty()) append(text)
                if (activeTags.isNotEmpty()) {
                    if (isNotEmpty()) append(" ")
                    append(activeTags.joinToString(" ") { "#$it" })
                }
            }
            onSubmit(amount, finalNote, capturedReceiptPath)
        }

        amountText = ""
        noteFieldValue = TextFieldValue("")
        activeTags = emptyList()
        inputMode = InputMode.AMOUNT
        capturedReceiptPath = null
        pendingCameraPath = null
        scannedAmount = null
        scanError = false
        isScanning = false
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF000000))
            .pointerInput(Unit) {}
            .imePadding()
            .navigationBarsPadding()
            .padding(horizontal = 24.dp, vertical = 16.dp),
        contentAlignment = Alignment.BottomStart
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            if (activeTags.isNotEmpty()) {
                FlowRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    activeTags.forEach { tag ->
                        AnimatedVisibility(
                            visible = true,
                            enter = scaleIn() + fadeIn(),
                            exit = scaleOut() + fadeOut()
                        ) {
                            BrutalistTag(label = tag)
                        }
                    }
                }
                Spacer(modifier = Modifier.height(12.dp))
            }

            Box(
                modifier = Modifier.fillMaxWidth(),
                contentAlignment = Alignment.CenterStart
            ) {
                if (isScanning) {
                    Text(
                        text = "SCANNING...",
                        style = amountStyle.copy(color = AccentYellow, fontSize = 32.sp),
                        modifier = Modifier
                            .graphicsLayer {
                                val scale = 1f - (0.5f * pivotProgress)
                                scaleX = scale
                                scaleY = scale
                                transformOrigin = TransformOrigin(0f, 0.5f)
                                alpha = if (pivotProgress == 1f) 0.5f else 1f
                            }
                    )
                } else if (scannedAmount != null) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .graphicsLayer {
                                val scale = 1f - (0.5f * pivotProgress)
                                scaleX = scale
                                scaleY = scale
                                transformOrigin = TransformOrigin(0f, 0.5f)
                                alpha = if (pivotProgress == 1f) 0.5f else 1f
                            }
                    ) {
                        Text(
                            text = "DETECTED: $${String.format(java.util.Locale.US, "%.2f", scannedAmount)}",
                            style = amountStyle.copy(fontSize = 32.sp)
                        )
                        Spacer(modifier = Modifier.height(20.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(16.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .background(AccentYellow)
                                    .clickable {
                                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                        amountText = if (scannedAmount!! % 1.0 == 0.0) scannedAmount!!.toLong().toString() else scannedAmount!!.toString()
                                        scannedAmount = null
                                        scanError = false
                                        inputMode = InputMode.NOTE
                                    }
                                    .padding(horizontal = 16.dp, vertical = 10.dp)
                            ) {
                                Text(
                                    text = "[ ACCEPT ]",
                                    style = MaterialTheme.typography.labelSmall.copy(
                                        color = OledBlack,
                                        letterSpacing = 2.sp
                                    )
                                )
                            }
                            Box(
                                modifier = Modifier
                                    .border(1.dp, SlateGrey)
                                    .clickable {
                                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                        scannedAmount = null
                                        capturedReceiptPath = null
                                        pendingCameraPath = null
                                        scanError = false
                                    }
                                    .padding(horizontal = 16.dp, vertical = 10.dp)
                            ) {
                                Text(
                                    text = "[ REJECT ]",
                                    style = MaterialTheme.typography.labelSmall.copy(
                                        color = SlateGrey,
                                        letterSpacing = 2.sp
                                    )
                                )
                            }
                        }
                    }
                } else if (scanError) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { 
                                scanError = false 
                                cameraPermissionDenied = false
                            }
                    ) {
                        Text(
                            text = if (cameraPermissionDenied) "[ CAMERA OFFLINE ]" else "NO TOTAL DETECTED",
                            style = amountStyle.copy(color = SlateGrey, fontSize = 28.sp)
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = if (cameraPermissionDenied) "Permission denied. Tap to dismiss" else "Tap to dismiss and try again",
                            style = MaterialTheme.typography.labelSmall.copy(
                                color = SlateGrey.copy(alpha = 0.6f)
                            )
                        )
                    }
                } else {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        BasicTextField(
                            value = amountText,
                            onValueChange = { newValue ->
                                if (inputMode == InputMode.NOTE) return@BasicTextField
                                if (newValue.endsWith(" ")) {
                                    pivotToNote()
                                } else {
                                    amountText = filterAmountInput(newValue)
                                }
                            },
                            readOnly = true,
                            textStyle = amountStyle,
                            cursorBrush = SolidColor(AccentYellow),
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(
                                keyboardType = KeyboardType.Decimal,
                                imeAction = ImeAction.Done
                            ),
                            keyboardActions = KeyboardActions(onDone = { commitTransaction() }),
                            modifier = Modifier
                                .weight(1f)
                                .focusRequester(amountFocusRequester)
                                .graphicsLayer {
                                    val scale = 1f - (0.5f * pivotProgress)
                                    scaleX = scale
                                    scaleY = scale
                                    transformOrigin = TransformOrigin(0f, 0.5f)
                                    alpha = if (pivotProgress == 1f) 0.5f else 1f
                                },
                            decorationBox = { innerTextField ->
                                Box(contentAlignment = Alignment.CenterStart) {
                                    if (amountText.isEmpty()) {
                                        Text(
                                            text = "0.00",
                                            style = amountStyle.copy(color = SlateGrey.copy(alpha = 0.4f))
                                        )
                                    }
                                    innerTextField()
                                }
                            }
                        )

                        if (amountText.isEmpty() && inputMode == InputMode.AMOUNT) {
                            Box(
                                modifier = Modifier
                                // 1. Massive Touch Target: Expanded to 88x64 to prevent text field stealing
                                .defaultMinSize(minWidth = 88.dp, minHeight = 64.dp)
                                .clickable {
                                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                    permissionLauncher.launch(android.Manifest.permission.CAMERA)
                                },
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "[ \uD83D\uDCF7 SCAN ]",
                                style = MaterialTheme.typography.labelSmall.copy(color = SlateGrey, letterSpacing = 2.sp),
                                modifier = Modifier.padding(12.dp) // Pushes the mechanical hit-box outward
                            )
                        }
                    }
                }
            }

            if (pivotProgress > 0f) {
                BasicTextField(
                    value = noteFieldValue,
                    onValueChange = { newFieldValue ->
                        val newText = newFieldValue.text

                        val tagRegex = Regex("""/(\w+)\s$""")
                        val matchResult = tagRegex.find(newText)

                        if (matchResult != null) {
                            val tagName = matchResult.groupValues[1]
                            activeTags = activeTags + tagName
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)

                            val stripped = newText.removeRange(matchResult.range)
                            val newCursor = (newFieldValue.selection.start - matchResult.value.length).coerceAtLeast(0)
                            noteFieldValue = TextFieldValue(text = stripped, selection = TextRange(newCursor))
                            return@BasicTextField
                        }

                        if (newText.isEmpty() && noteFieldValue.text.isEmpty() && activeTags.isEmpty()) {
                            inputMode = InputMode.AMOUNT
                            return@BasicTextField
                        }

                        noteFieldValue = newFieldValue
                    },
                    textStyle = noteStyle,
                    enabled = inputMode == InputMode.NOTE,
                    cursorBrush = SolidColor(AccentYellow),
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Text,
                        imeAction = ImeAction.Done
                    ),
                    keyboardActions = KeyboardActions(onDone = { commitTransaction() }),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 120.dp)
                        .focusRequester(noteFocusRequester)
                        .graphicsLayer {
                            alpha = pivotProgress
                            translationX = 40f * (1f - pivotProgress)
                        }
                        .onKeyEvent { keyEvent ->
                            if (keyEvent.key == Key.Backspace &&
                                keyEvent.type == KeyEventType.KeyDown &&
                                noteFieldValue.text.isEmpty()
                            ) {
                                if (activeTags.isNotEmpty()) {
                                    val lastTag = activeTags.last()
                                    activeTags = activeTags.dropLast(1)
                                    val restoredText = "/$lastTag"
                                    noteFieldValue = TextFieldValue(
                                        text = restoredText,
                                        selection = TextRange(restoredText.length)
                                    )
                                } else {
                                    inputMode = InputMode.AMOUNT
                                }
                                true
                            } else {
                                false
                            }
                        },
                    decorationBox = { innerTextField ->
                        Box(contentAlignment = Alignment.CenterStart) {
                            if (noteFieldValue.text.isEmpty()) {
                                Text(
                                    text = if (activeTags.isEmpty()) "what was it for?" else "add a note or /tag",
                                    style = noteStyle.copy(color = SlateGrey.copy(alpha = 0.4f))
                                )
                            }
                            innerTextField()
                        }
                    }
                )
            }
            }

            AnimatedVisibility(
                visible = inputMode == InputMode.AMOUNT && !isScanning && scannedAmount == null && !scanError,
                enter = androidx.compose.animation.slideInVertically(initialOffsetY = { it }) + androidx.compose.animation.fadeIn(),
                // CRITICAL FIX: If moving to NOTE, destroy layout instantly so it doesn't stack on top of the rising system keyboard.
                exit = if (inputMode == InputMode.NOTE) {
                    androidx.compose.animation.shrinkVertically(animationSpec = androidx.compose.animation.core.tween(0)) + 
                    androidx.compose.animation.fadeOut(animationSpec = androidx.compose.animation.core.tween(0))
                } else {
                    androidx.compose.animation.slideOutVertically(targetOffsetY = { it }) + androidx.compose.animation.fadeOut()
                }
            ) {
                BrutalistNumpad(
                    onKeyPress = handleNumpadKey,
                    onLogNote = pivotToNote
                )
            }
        }

        androidx.compose.animation.AnimatedVisibility(
            visible = showHardwareCamera,
            enter = androidx.compose.animation.fadeIn() + androidx.compose.animation.slideInVertically(initialOffsetY = { it }),
            exit = androidx.compose.animation.fadeOut() + androidx.compose.animation.slideOutVertically(targetOffsetY = { it })
        ) {
            BrutalistCameraView(
                onImageCaptured = { path -> processCapturedReceipt(path) },
                onCancel = { showHardwareCamera = false }
            )
        }
    }
}

@Composable
private fun BrutalistNumpad(
    onKeyPress: (String) -> Unit,
    onLogNote: () -> Boolean
) {
    val haptic = LocalHapticFeedback.current

    val handlePress = { key: String ->
        when (key) {
            "BACKSPACE", "/", "*", "-", "+" -> haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
            else -> haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
        }
        onKeyPress(key)
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 16.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp)
    ) {
        val keys = listOf(
            listOf("7", "8", "9", "/"),
            listOf("4", "5", "6", "*"),
            listOf("1", "2", "3", "-"),
            listOf(".", "0", "BACKSPACE", "+")
        )

        for (row in keys) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                for (key in row) {
                    NumpadKey(
                        key = key,
                        modifier = Modifier
                            .weight(1f)
                            .height(64.dp),
                        onClick = { handlePress(key) }
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        val scope = rememberCoroutineScope()
        var errorFlash by remember { mutableStateOf(false) }
        val bgColor = if (errorFlash) Color(0xFFD32F2F) else AccentYellow
        val textColor = if (errorFlash) StarkWhite else OledBlack

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(64.dp)
                .background(bgColor)
                .clickable {
                    if (!onLogNote()) {
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        scope.launch {
                            errorFlash = true
                            kotlinx.coroutines.delay(150)
                            errorFlash = false
                        }
                    }
                },
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = "[ LOG NOTE ]",
                style = MaterialTheme.typography.displayLarge.copy(
                    fontSize = 24.sp,
                    color = textColor,
                    letterSpacing = 2.sp
                )
            )
        }
    }
}

@Composable
private fun NumpadKey(
    key: String,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()

    val bgColor = if (isPressed) StarkWhite else Color(0xFF1C1C1E)
    val textColor = if (isPressed) OledBlack else StarkWhite
    val displayText = if (key == "BACKSPACE") "⌫" else key

    Box(
        modifier = modifier
            .background(bgColor)
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = onClick
            ),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = displayText,
            style = MaterialTheme.typography.displayLarge.copy(
                fontSize = 28.sp,
                fontFamily = MaterialTheme.typography.displayLarge.fontFamily
            ),
            color = textColor
        )
    }
}

private fun filterAmountInput(raw: String): String {
    val operators = setOf('+', '-', '*', '/')
    val filtered = buildString {
        var lastChar: Char? = null
        for (char in raw) {
            if (char.isDigit() || char in operators || char == '.') {
                if (char in operators && lastChar in operators) continue
                if (char == '.' && lastChar == '.') continue
                append(char)
                lastChar = char
            }
        }
    }
    
    if (filtered.none { it in operators }) {
        val amount = filtered.toDoubleOrNull()
        if (amount != null && amount > 10000000.0) {
            return "10000000"
        }
    }
    
    return filtered
}

private fun evaluateExpression(expression: String): Double {
    val str = expression.replace(" ", "")
    if (str.isEmpty()) return 0.0

    class Parser {
        var pos = -1
        var ch = 0

        fun nextChar() {
            ch = if (++pos < str.length) str[pos].code else -1
        }

        fun eat(charToEat: Int): Boolean {
            while (ch == ' '.code) nextChar()
            if (ch == charToEat) {
                nextChar()
                return true
            }
            return false
        }

        fun parse(): Double {
            nextChar()
            val x = parseExpression()
            if (pos < str.length) throw RuntimeException("Unexpected: " + ch.toChar())
            return x
        }

        fun parseExpression(): Double {
            var x = parseTerm()
            while (true) {
                when {
                    eat('+'.code) -> x += parseTerm()
                    eat('-'.code) -> x -= parseTerm()
                    else -> return x
                }
            }
        }

        fun parseTerm(): Double {
            var x = parseFactor()
            while (true) {
                when {
                    eat('*'.code) -> x *= parseFactor()
                    eat('/'.code) -> {
                        val divisor = parseFactor()
                        if (divisor == 0.0) throw ArithmeticException("Division by zero")
                        x /= divisor
                    }
                    else -> return x
                }
            }
        }

        fun parseFactor(): Double {
            if (eat('+'.code)) return parseFactor()
            if (eat('-'.code)) return -parseFactor()

            var x: Double
            val startPos = pos
            if (eat('('.code)) {
                x = parseExpression()
                eat(')'.code)
            } else if ((ch >= '0'.code && ch <= '9'.code) || ch == '.'.code) {
                while ((ch >= '0'.code && ch <= '9'.code) || ch == '.'.code) nextChar()
                x = str.substring(startPos, pos).toDouble()
            } else {
                throw RuntimeException("Unexpected: " + ch.toChar())
            }
            return x
        }
    }

    val result = Parser().parse()
    if (result.isInfinite() || result.isNaN()) {
        throw ArithmeticException("Invalid result: $result")
    }
    return result
}
