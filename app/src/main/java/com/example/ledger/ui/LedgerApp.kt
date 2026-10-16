package com.example.ledger.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedContentTransitionScope.SlideDirection
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import kotlinx.coroutines.launch
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInParent
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.example.ledger.data.LedgerPreferences
import com.example.ledger.data.Transaction
import com.example.ledger.data.TransactionStorage
import com.example.ledger.ui.components.TerminalInputSheet
import com.example.ledger.ui.components.TypographyInsightsView
import com.example.ledger.ui.screens.LedgerDashboard
import com.example.ledger.ui.theme.AccentYellow
import com.example.ledger.ui.theme.OledBlack
import com.example.ledger.ui.theme.SlateGrey
import com.example.ledger.ui.theme.StarkWhite
import java.time.LocalDate
import java.time.ZoneId
import java.util.UUID
import kotlin.math.abs

enum class Screen { DASHBOARD, INSIGHTS, CONFIG }

@Composable
fun LedgerApp(
    sharedReceiptUri: android.net.Uri? = null,
    initialWidgetAction: String? = null
) {
    val context = androidx.compose.ui.platform.LocalContext.current

    // ── Persistence layer ────────────────────────────────────────
    val storage = remember { TransactionStorage(context) }
    val prefs = remember { LedgerPreferences(context) }
    val scope = rememberCoroutineScope()

    // ── Transaction state (loaded asynchronously from JSON file) ─
    var transactions by remember { mutableStateOf<List<Transaction>>(emptyList()) }

    // ── Settings state (loaded from SharedPreferences) ───────────
    var monthlyBudget by remember { mutableStateOf(prefs.monthlyBudget) }
    var currencySymbol by remember { mutableStateOf(prefs.currencySymbol) }
    var tagThresholds by remember { mutableStateOf(prefs.tagThresholds) }
    var hapticKeyTicks by remember { mutableStateOf(prefs.hapticKeyTicks) }
    var hapticInterfaceSnaps by remember { mutableStateOf(prefs.hapticInterfaceSnaps) }
    var hapticImpactThuds by remember { mutableStateOf(prefs.hapticImpactThuds) }
    var systemDaemons by remember { mutableStateOf(prefs.systemDaemons) }

    LaunchedEffect(Unit) {
        transactions = storage.loadAll()

        val lastRunMs = prefs.lastDaemonRunTimestamp
        val now = LocalDate.now()
        val lastRunDate = if (lastRunMs > 0L) {
            java.time.Instant.ofEpochMilli(lastRunMs).atZone(ZoneId.systemDefault()).toLocalDate()
        } else {
            now.minusDays(1)
        }

        if (now.isAfter(lastRunDate)) {
            var anyInjected = false
            
            val daemons = prefs.systemDaemons
            for (daemon in daemons) {
                val maxDayThisMonth = now.lengthOfMonth()
                val actualTriggerDay = minOf(daemon.triggerDay, maxDayThisMonth)
                val triggerDateThisMonth = now.withDayOfMonth(actualTriggerDay)
                
                if ((now.isEqual(triggerDateThisMonth) || now.isAfter(triggerDateThisMonth)) &&
                    lastRunDate.isBefore(triggerDateThisMonth)) {
                    
                    val tagsWithSystem = (daemon.tags + "SYSTEM").distinct()
                    val tx = Transaction(
                        id = UUID.randomUUID().toString(),
                        amount = daemon.amount,
                        note = daemon.note,
                        tags = tagsWithSystem,
                        timestamp = System.currentTimeMillis()
                    )
                    storage.insert(tx)
                    anyInjected = true
                }
            }
            
            if (anyInjected) {
                transactions = storage.loadAll()
            }
            prefs.lastDaemonRunTimestamp = System.currentTimeMillis()
        }
    }

    // ── Balance calculation ──────────────────────────────────────
    // Balance = monthlyBudget - sum(current month's expenses)
    // Resets automatically on the 1st of every month because we only
    // subtract transactions whose timestamp falls in the current month.
    val currentBalance by remember(transactions, monthlyBudget) {
        derivedStateOf {
            val now = LocalDate.now()
            val monthStartMs = now.withDayOfMonth(1)
                .atStartOfDay(ZoneId.systemDefault())
                .toInstant()
                .toEpochMilli()

            val currentMonthExpenses = transactions
                .filter { it.timestamp >= monthStartMs && it.amount < 0 }
                .sumOf { abs(it.amount) }

            monthlyBudget - currentMonthExpenses
        }
    }

    var taggingTransactionId by remember { mutableStateOf<String?>(null) }
    var renamingTransactionId by remember { mutableStateOf<String?>(null) }
    var viewingReceiptPath by remember { mutableStateOf<String?>(null) }

    var showTerminal by remember { mutableStateOf(false) }
    var startInCameraMode by remember { mutableStateOf(false) }
    var currentScreen by remember { mutableStateOf(Screen.DASHBOARD) }

    LaunchedEffect(sharedReceiptUri, initialWidgetAction) {
        if (sharedReceiptUri != null) {
            showTerminal = true
        }
        if (initialWidgetAction == "CAMERA") {
            showTerminal = true
            startInCameraMode = true
        } else if (initialWidgetAction == "TERMINAL") {
            showTerminal = true
            startInCameraMode = false
        }
    }

    // BONUS FIX: Clear focus (and dismiss keyboard) when leaving CONFIG screen.
    val focusManager = LocalFocusManager.current
    DisposableEffect(currentScreen) {
        onDispose {
            // When currentScreen changes, clear any active focus to hide keyboard
            focusManager.clearFocus()
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(OledBlack)
            .systemBarsPadding()
    ) {
        Column(modifier = Modifier.fillMaxSize()) {

            BrutalistTabHeader(
                currentScreen = currentScreen,
                onScreenSelected = { currentScreen = it }
            )

            AnimatedContent(
                targetState = currentScreen,
                transitionSpec = {
                    val slideDirection = if (targetState == Screen.INSIGHTS || (targetState == Screen.CONFIG && initialState == Screen.INSIGHTS)) {
                        SlideDirection.Left
                    } else {
                        SlideDirection.Right
                    }
                    val springSpec = spring<IntOffset>(
                        dampingRatio = 0.8f,
                        stiffness = 250f
                    )

                    (slideIntoContainer(slideDirection, animationSpec = springSpec) + fadeIn()) togetherWith
                            (slideOutOfContainer(slideDirection, animationSpec = springSpec) + fadeOut())
                },
                label = "ScreenTransition",
                modifier = Modifier.weight(1f)
            ) { screen ->
                when (screen) {
                    Screen.DASHBOARD -> {
                        LedgerDashboard(
                            transactions = transactions,
                            currentBalance = currentBalance,
                            monthlyBudget = monthlyBudget,
                            currencySymbol = currencySymbol,
                            onTriggerTerminal = { showTerminal = true },
                            onSwipeToTag = { id -> taggingTransactionId = id },
                            onRenameTransaction = { id -> renamingTransactionId = id },
                            onUpdateTransaction = { updatedTx ->
                                scope.launch {
                                    storage.update(updatedTx)
                                    transactions = storage.loadAll()
                                }
                            },
                            onViewReceipt = { path -> viewingReceiptPath = path },
                            onDeleteTransaction = { id ->
                                scope.launch {
                                    storage.delete(id)
                                    transactions = storage.loadAll()
                                }
                            }
                        )
                    }
                    Screen.INSIGHTS -> {
                        TypographyInsightsView(
                            transactions = transactions,
                            monthlyBudget = monthlyBudget,
                            currencySymbol = currencySymbol,
                            modifier = Modifier.fillMaxSize()
                        )
                    }
                    Screen.CONFIG -> {
                        com.example.ledger.ui.screens.ConfigurationView(
                            monthlyBudget = monthlyBudget,
                            onBudgetChange = {
                                monthlyBudget = it
                                prefs.monthlyBudget = it
                            },
                            currencySymbol = currencySymbol,
                            onCurrencySymbolChange = { newSym ->
                                currencySymbol = newSym
                                prefs.currencySymbol = newSym
                            },
                            systemDaemons = systemDaemons,
                            onAddDaemon = { newDaemon ->
                                val updated = systemDaemons + newDaemon
                                systemDaemons = updated
                                prefs.systemDaemons = updated
                            },
                            onRemoveDaemon = { daemonId ->
                                val updated = systemDaemons.filter { it.id != daemonId }
                                systemDaemons = updated
                                prefs.systemDaemons = updated
                            }
                        )
                    }
                }
            }
        }

        TerminalInputSheet(
            isVisible = showTerminal,
            sharedReceiptUri = sharedReceiptUri,
            onSubmit = { amount, note, receiptPath ->
                val extractedTags = note.split(" ")
                    .filter { it.startsWith("#") }
                    .map { it.removePrefix("#").uppercase() }

                val cleanNote = note.split(" ")
                    .filter { !it.startsWith("#") }
                    .joinToString(" ")
                    .trim()

                val newTx = Transaction(
                    id = UUID.randomUUID().toString(),
                    amount = -amount,
                    note = cleanNote,
                    tags = extractedTags,
                    timestamp = System.currentTimeMillis(),
                    receiptPath = receiptPath
                )

                // Persist to JSON file asynchronously and update state
                scope.launch {
                    storage.insert(newTx)
                    transactions = storage.loadAll()
                }
                showTerminal = false
                startInCameraMode = false
            },
            onDismiss = {
                showTerminal = false
                startInCameraMode = false
            },
            startInCameraMode = startInCameraMode,
            modifier = Modifier.align(Alignment.BottomCenter)
        )

        if (taggingTransactionId != null) {
            BrutalistTagDialog(
                onDismiss = { taggingTransactionId = null },
                onSave = { newTag ->
                    val txId = taggingTransactionId ?: return@BrutalistTagDialog
                    val txToUpdate = transactions.find { it.id == txId }
                    if (txToUpdate != null) {
                        val updatedTx = txToUpdate.copy(
                            tags = (txToUpdate.tags + newTag.uppercase()).distinct()
                        )
                        scope.launch {
                            storage.update(updatedTx)
                            transactions = storage.loadAll()
                        }
                    }
                    taggingTransactionId = null
                }
            )
        }

        if (renamingTransactionId != null) {
            val txId = renamingTransactionId ?: return
            val txToUpdate = transactions.find { it.id == txId }
            if (txToUpdate != null) {
                BrutalistRenameDialog(
                    initialNote = txToUpdate.note,
                    onDismiss = { renamingTransactionId = null },
                    onSave = { newNote ->
                        val updatedTx = txToUpdate.copy(note = newNote)
                        scope.launch {
                            storage.update(updatedTx)
                            transactions = storage.loadAll()
                        }
                        renamingTransactionId = null
                    }
                )
            } else {
                renamingTransactionId = null
            }
        }

        if (viewingReceiptPath != null) {
            val path = viewingReceiptPath ?: return
            BrutalistReceiptOverlay(
                receiptPath = path,
                onDismiss = { viewingReceiptPath = null }
            )
        }
    }
}

@Composable
private fun BrutalistTagDialog(
    onDismiss: () -> Unit,
    onSave: (String) -> Unit
) {
    var tagText by remember { mutableStateOf("") }

    Dialog(onDismissRequest = onDismiss) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(OledBlack)
                .border(1.dp, SlateGrey)
                .padding(24.dp)
        ) {
            Column {
                Text(
                    text = "ADD TAG",
                    style = MaterialTheme.typography.labelSmall.copy(letterSpacing = 2.sp),
                    color = SlateGrey
                )

                Spacer(modifier = Modifier.height(16.dp))

                BasicTextField(
                    value = tagText,
                    onValueChange = { tagText = it.filter { c -> c.isLetterOrDigit() } },
                    textStyle = MaterialTheme.typography.bodyMedium.copy(color = StarkWhite),
                    singleLine = true,
                    cursorBrush = SolidColor(AccentYellow),
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Text,
                        imeAction = ImeAction.Done
                    ),
                    keyboardActions = KeyboardActions(onDone = {
                        if (tagText.isNotBlank()) onSave(tagText)
                    }),
                    decorationBox = { innerTextField ->
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .border(1.dp, StarkWhite)
                                .padding(horizontal = 12.dp, vertical = 12.dp)
                        ) {
                            if (tagText.isEmpty()) {
                                Text(
                                    text = "tag name",
                                    style = MaterialTheme.typography.bodyMedium.copy(
                                        color = SlateGrey.copy(alpha = 0.4f)
                                    )
                                )
                            }
                            innerTextField()
                        }
                    }
                )

                Spacer(modifier = Modifier.height(20.dp))

                Text(
                    text = "SAVE",
                    style = MaterialTheme.typography.bodyMedium.copy(letterSpacing = 2.sp),
                    color = if (tagText.isNotBlank()) AccentYellow else SlateGrey,
                    modifier = Modifier
                        .align(Alignment.End)
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null
                        ) {
                            if (tagText.isNotBlank()) onSave(tagText)
                        }
                        .padding(4.dp)
                )
            }
        }
    }
}

@Composable
private fun BrutalistRenameDialog(
    initialNote: String,
    onDismiss: () -> Unit,
    onSave: (String) -> Unit
) {
    val haptic = LocalHapticFeedback.current
    var noteText by remember { mutableStateOf(initialNote) }

    Dialog(onDismissRequest = onDismiss) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(OledBlack)
                .border(1.dp, SlateGrey)
                .padding(24.dp)
        ) {
            Column {
                Text(
                    text = "RENAME TRANSACTION",
                    style = MaterialTheme.typography.labelSmall.copy(letterSpacing = 2.sp),
                    color = SlateGrey
                )

                Spacer(modifier = Modifier.height(16.dp))

                BasicTextField(
                    value = noteText,
                    onValueChange = { noteText = it },
                    textStyle = MaterialTheme.typography.bodyMedium.copy(color = StarkWhite),
                    singleLine = true,
                    cursorBrush = SolidColor(AccentYellow),
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Text,
                        imeAction = ImeAction.Done
                    ),
                    keyboardActions = KeyboardActions(onDone = {
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        onSave(noteText.trim())
                    }),
                    decorationBox = { innerTextField ->
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .border(1.dp, StarkWhite)
                                .padding(horizontal = 12.dp, vertical = 12.dp)
                        ) {
                            if (noteText.isEmpty()) {
                                Text(
                                    text = "Untitled Transaction",
                                    style = MaterialTheme.typography.bodyMedium.copy(
                                        color = SlateGrey.copy(alpha = 0.4f)
                                    )
                                )
                            }
                            innerTextField()
                        }
                    }
                )

                Spacer(modifier = Modifier.height(20.dp))

                Text(
                    text = "SAVE",
                    style = MaterialTheme.typography.bodyMedium.copy(letterSpacing = 2.sp),
                    color = AccentYellow,
                    modifier = Modifier
                        .align(Alignment.End)
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null
                        ) {
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            onSave(noteText.trim())
                        }
                        .padding(4.dp)
                )
            }
        }
    }
}

@Composable
private fun BrutalistReceiptOverlay(
    receiptPath: String,
    onDismiss: () -> Unit
) {
    Dialog(onDismissRequest = onDismiss) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(OledBlack)
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = onDismiss
                ),
            contentAlignment = Alignment.Center
        ) {
            val bitmap = remember(receiptPath) {
                android.graphics.BitmapFactory.decodeFile(receiptPath)?.asImageBitmap()
            }
            if (bitmap != null) {
                Image(
                    bitmap = bitmap,
                    contentDescription = "Receipt Image",
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(24.dp)
                        .border(1.dp, StarkWhite),
                    contentScale = ContentScale.Fit
                )
            } else {
                Text(
                    text = "IMAGE CORRUPTED OR MISSING",
                    style = MaterialTheme.typography.labelSmall,
                    color = AccentYellow
                )
            }
        }
    }
}

@Composable
private fun BrutalistTabHeader(
    currentScreen: Screen,
    onScreenSelected: (Screen) -> Unit
) {
    val haptic = LocalHapticFeedback.current
    val density = LocalDensity.current

    var dashboardTabX by remember { mutableStateOf(0.dp) }
    var dashboardTabWidth by remember { mutableStateOf(0.dp) }

    var insightsTabX by remember { mutableStateOf(0.dp) }
    var insightsTabWidth by remember { mutableStateOf(0.dp) }

    var configTabX by remember { mutableStateOf(0.dp) }
    var configTabWidth by remember { mutableStateOf(0.dp) }

    val indicatorX by animateDpAsState(
        targetValue = when (currentScreen) {
            Screen.DASHBOARD -> dashboardTabX
            Screen.INSIGHTS -> insightsTabX
            Screen.CONFIG -> configTabX
        },
        animationSpec = spring(dampingRatio = 0.6f, stiffness = 200f),
        label = "indicatorX"
    )
    val indicatorWidth by animateDpAsState(
        targetValue = when (currentScreen) {
            Screen.DASHBOARD -> dashboardTabWidth
            Screen.INSIGHTS -> insightsTabWidth
            Screen.CONFIG -> configTabWidth
        },
        animationSpec = spring(dampingRatio = 0.6f, stiffness = 200f),
        label = "indicatorWidth"
    )

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp, vertical = 16.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(
                text = "LEDGER",
                style = MaterialTheme.typography.bodyMedium.copy(letterSpacing = 2.sp),
                color = if (currentScreen == Screen.DASHBOARD) StarkWhite else SlateGrey,
                modifier = Modifier
                    .onGloballyPositioned { coordinates ->
                        dashboardTabX = with(density) { coordinates.positionInParent().x.toDp() }
                        dashboardTabWidth = with(density) { coordinates.size.width.toDp() }
                    }
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null
                    ) {
                        if (currentScreen != Screen.DASHBOARD) {
                            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            onScreenSelected(Screen.DASHBOARD)
                        }
                    }
            )

            Spacer(modifier = Modifier.width(32.dp))

            Text(
                text = "INSIGHTS",
                style = MaterialTheme.typography.bodyMedium.copy(letterSpacing = 2.sp),
                color = if (currentScreen == Screen.INSIGHTS) StarkWhite else SlateGrey,
                modifier = Modifier
                    .onGloballyPositioned { coordinates ->
                        insightsTabX = with(density) { coordinates.positionInParent().x.toDp() }
                        insightsTabWidth = with(density) { coordinates.size.width.toDp() }
                    }
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null
                    ) {
                        if (currentScreen != Screen.INSIGHTS) {
                            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            onScreenSelected(Screen.INSIGHTS)
                        }
                    }
            )

            Spacer(modifier = Modifier.width(32.dp))

            Text(
                text = "CONFIG",
                style = MaterialTheme.typography.bodyMedium.copy(letterSpacing = 2.sp),
                color = if (currentScreen == Screen.CONFIG) StarkWhite else SlateGrey,
                modifier = Modifier
                    .onGloballyPositioned { coordinates ->
                        configTabX = with(density) { coordinates.positionInParent().x.toDp() }
                        configTabWidth = with(density) { coordinates.size.width.toDp() }
                    }
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null
                    ) {
                        if (currentScreen != Screen.CONFIG) {
                            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            onScreenSelected(Screen.CONFIG)
                        }
                    }
            )
        }

        Spacer(modifier = Modifier.height(8.dp))

        Box(
            modifier = Modifier
                .offset(x = indicatorX)
                .width(indicatorWidth)
                .height(2.dp)
                .background(AccentYellow)
        )
    }
}