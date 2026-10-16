package com.example.ledger.ui.screens

import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.OffsetMapping
import androidx.compose.ui.text.input.TransformedText
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ledger.data.SystemDaemon
import com.example.ledger.ui.theme.AccentYellow
import com.example.ledger.ui.theme.OledBlack
import com.example.ledger.ui.theme.SlateGrey
import com.example.ledger.ui.theme.StarkWhite
import java.text.NumberFormat
import java.util.Locale
import java.util.UUID

@Composable
fun ConfigurationView(
    monthlyBudget: Double,
    onBudgetChange: (Double) -> Unit,
    currencySymbol: String = "$",
    onCurrencySymbolChange: (String) -> Unit = {},
    systemDaemons: List<SystemDaemon>,
    onAddDaemon: (SystemDaemon) -> Unit,
    onRemoveDaemon: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .background(OledBlack)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 24.dp, vertical = 32.dp),
        verticalArrangement = Arrangement.spacedBy(48.dp)
    ) {
        BlockA_GlobalDynamicBudgetSlider(
            budget = monthlyBudget,
            currencySymbol = currencySymbol,
            onBudgetChange = onBudgetChange
        )

        BlockE_CurrencySelector(
            selectedSymbol = currencySymbol,
            onSymbolSelected = onCurrencySymbolChange
        )

        BlockD_SystemDaemons(
            daemons = systemDaemons,
            currencySymbol = currencySymbol,
            onAddDaemon = onAddDaemon,
            onRemoveDaemon = onRemoveDaemon
        )

        Spacer(modifier = Modifier.height(120.dp))
    }
}

@Composable
fun BlockE_CurrencySelector(
    selectedSymbol: String,
    onSymbolSelected: (String) -> Unit
) {
    val haptic = LocalHapticFeedback.current
    val symbols = listOf("$", "₹", "€", "£", "¥", "CHF", "C$")

    Column(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = "CURRENCY UNIT",
            style = MaterialTheme.typography.labelSmall.copy(letterSpacing = 2.sp),
            color = SlateGrey
        )

        Spacer(modifier = Modifier.height(16.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            symbols.forEach { sym ->
                val isSelected = sym == selectedSymbol
                val bgColor = if (isSelected) AccentYellow else OledBlack
                val textColor = if (isSelected) OledBlack else StarkWhite
                val borderColor = if (isSelected) AccentYellow else SlateGrey

                Box(
                    modifier = Modifier
                        .weight(1f)
                        .height(44.dp)
                        .background(bgColor)
                        .border(1.dp, borderColor)
                        .clickable {
                            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            onSymbolSelected(sym)
                        },
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = sym,
                        style = MaterialTheme.typography.bodyLarge,
                        color = textColor
                    )
                }
            }
        }
    }
}

@Composable
fun BlockA_GlobalDynamicBudgetSlider(
    budget: Double,
    currencySymbol: String = "$",
    onBudgetChange: (Double) -> Unit
) {
    var budgetText by remember { mutableStateOf(budget.toInt().toString()) }

    val handleKeyPress = { key: String ->
        var newText = budgetText
        if (key == "BACKSPACE") {
            if (newText.isNotEmpty()) {
                newText = newText.dropLast(1)
            }
        } else {
            newText += key
        }

        var filtered = newText.filter { it.isDigit() }
        filtered = filtered.trimStart('0')
        if (filtered.isEmpty()) filtered = "0"

        if (filtered.length <= 9) {
            budgetText = filtered
            val parsed = filtered.toDoubleOrNull() ?: 0.0
            onBudgetChange(parsed.coerceIn(0.0, 10000000.0))
        }
    }

    Column(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = "MONTHLY BUDGET",
            style = MaterialTheme.typography.labelSmall.copy(letterSpacing = 2.sp),
            color = SlateGrey
        )

        Spacer(modifier = Modifier.height(16.dp))

        var isEditing by remember { mutableStateOf(false) }

        Box(modifier = Modifier.fillMaxWidth()) {
            BasicTextField(
                value = budgetText,
                onValueChange = {},
                readOnly = true,
                textStyle = MaterialTheme.typography.displayLarge.copy(color = StarkWhite),
                singleLine = true,
                cursorBrush = SolidColor(AccentYellow),
                visualTransformation = CurrencyVisualTransformation(currencySymbol),
                modifier = Modifier.fillMaxWidth()
            )
            Box(
                modifier = Modifier
                    .matchParentSize()
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null
                    ) {
                        isEditing = !isEditing
                    }
            )
        }

        Spacer(modifier = Modifier.height(16.dp))

        androidx.compose.animation.AnimatedVisibility(
            visible = isEditing,
            enter = androidx.compose.animation.expandVertically(
                animationSpec = spring(dampingRatio = 0.7f, stiffness = 200f)
            ) + androidx.compose.animation.fadeIn(),
            exit = androidx.compose.animation.shrinkVertically(
                animationSpec = spring(dampingRatio = 0.7f, stiffness = 200f)
            ) + androidx.compose.animation.fadeOut()
        ) {
            Column {
                NumericBrutalistNumpad(onKeyPress = handleKeyPress)
                
                Spacer(modifier = Modifier.height(16.dp))
                
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp)
                        .background(StarkWhite)
                        .clickable { isEditing = false },
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "[ SET BUDGET ]",
                        style = MaterialTheme.typography.labelSmall.copy(
                            color = OledBlack,
                            letterSpacing = 2.sp
                        )
                    )
                }
            }
        }
    }
}

/**
 * Custom Visual Transformation for currency with configurable symbol.
 */
class CurrencyVisualTransformation(
    private val currencySymbol: String = "$"
) : VisualTransformation {
    override fun filter(text: AnnotatedString): TransformedText {
        val rawText = text.text
        if (rawText.isEmpty()) {
            return TransformedText(
                AnnotatedString("${currencySymbol}0"),
                object : OffsetMapping {
                    override fun originalToTransformed(offset: Int): Int = currencySymbol.length + 1
                    override fun transformedToOriginal(offset: Int): Int = 0
                }
            )
        }

        val value = rawText.toDoubleOrNull() ?: 0.0
        val formattedNumber = NumberFormat.getNumberInstance(Locale.US).apply {
            maximumFractionDigits = 0
        }.format(value)
        val formattedText = "$currencySymbol$formattedNumber"

        val offsetMapping = object : OffsetMapping {
            override fun originalToTransformed(offset: Int): Int = formattedText.length
            override fun transformedToOriginal(offset: Int): Int = rawText.length
        }

        return TransformedText(AnnotatedString(formattedText), offsetMapping)
    }
}

@Composable
fun BlockD_SystemDaemons(
    daemons: List<SystemDaemon>,
    currencySymbol: String = "$",
    onAddDaemon: (SystemDaemon) -> Unit,
    onRemoveDaemon: (String) -> Unit
) {
    var newAmount by remember { mutableStateOf("") }
    var newNote by remember { mutableStateOf("") }
    var newDay by remember { mutableStateOf("") }
    val haptic = LocalHapticFeedback.current

    Column(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = "RECURRING TRANSACTIONS",
            style = MaterialTheme.typography.labelSmall.copy(letterSpacing = 2.sp),
            color = SlateGrey
        )
        
        Spacer(modifier = Modifier.height(24.dp))
        
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .border(1.dp, SlateGrey)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text(
                text = "ADD RECURRING TRANSACTION",
                style = MaterialTheme.typography.labelSmall,
                color = StarkWhite
            )
            
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                BasicTextField(
                    value = newAmount,
                    onValueChange = { newAmount = it.filter { c -> c.isDigit() || c == '.' } },
                    textStyle = MaterialTheme.typography.bodyMedium.copy(color = StarkWhite),
                    modifier = Modifier.weight(1.2f).border(1.dp, SlateGrey).padding(12.dp),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    decorationBox = { innerTextField ->
                        if (newAmount.isEmpty()) Text("Amount", color = SlateGrey.copy(alpha = 0.5f))
                        innerTextField()
                    }
                )
                BasicTextField(
                    value = newNote,
                    onValueChange = { newNote = it },
                    textStyle = MaterialTheme.typography.bodyMedium.copy(color = StarkWhite),
                    modifier = Modifier.weight(2f).border(1.dp, SlateGrey).padding(12.dp),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Text),
                    decorationBox = { innerTextField ->
                        if (newNote.isEmpty()) Text("Note/Tag", color = SlateGrey.copy(alpha = 0.5f))
                        innerTextField()
                    }
                )
                BasicTextField(
                    value = newDay,
                    onValueChange = { newDay = it.filter { c -> c.isDigit() } },
                    textStyle = MaterialTheme.typography.bodyMedium.copy(color = StarkWhite),
                    modifier = Modifier.weight(1f).border(1.dp, SlateGrey).padding(12.dp),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    decorationBox = { innerTextField ->
                        if (newDay.isEmpty()) Text("Day", color = SlateGrey.copy(alpha = 0.5f))
                        innerTextField()
                    }
                )
            }
            
            val isFormValid = newAmount.toDoubleOrNull() != null && newNote.isNotBlank() && (newDay.toIntOrNull() in 1..31)
            
            Text(
                text = "[ ADD TRANSACTION ]",
                style = MaterialTheme.typography.labelSmall.copy(letterSpacing = 2.sp),
                color = if (isFormValid) AccentYellow else SlateGrey,
                modifier = Modifier
                    .align(Alignment.End)
                    .clickable(
                        enabled = isFormValid,
                        onClick = {
                            val extractedTags = newNote.split(" ")
                                .filter { it.startsWith("#") }
                                .map { it.removePrefix("#").uppercase() }
                            val cleanNote = newNote.split(" ")
                                .filter { !it.startsWith("#") }
                                .joinToString(" ").trim()
                                
                            val daemon = SystemDaemon(
                                id = UUID.randomUUID().toString(),
                                amount = -(newAmount.toDoubleOrNull() ?: 0.0),
                                note = cleanNote,
                                tags = extractedTags,
                                triggerDay = newDay.toInt()
                            )
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            onAddDaemon(daemon)
                            newAmount = ""
                            newNote = ""
                            newDay = ""
                        }
                    )
            )
        }
        
        Spacer(modifier = Modifier.height(24.dp))
        
        daemons.forEach { daemon ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    val amountVal = Math.abs(daemon.amount)
                    val amountStr = "$currencySymbol${if (amountVal % 1.0 == 0.0) amountVal.toLong() else amountVal}"
                    Text(
                        text = "$amountStr • Day ${daemon.triggerDay}",
                        style = MaterialTheme.typography.bodyLarge,
                        color = StarkWhite
                    )
                    val desc = buildString {
                        append(daemon.note)
                        if (daemon.tags.isNotEmpty()) {
                            if (daemon.note.isNotBlank()) append(" ")
                            append(daemon.tags.joinToString(" ") { "#$it" })
                        }
                    }
                    if (desc.isNotBlank()) {
                        Text(
                            text = desc,
                            style = MaterialTheme.typography.bodyMedium,
                            color = SlateGrey
                        )
                    }
                }
                
                Text(
                    text = "[ REMOVE ]",
                    style = MaterialTheme.typography.labelSmall.copy(letterSpacing = 2.sp),
                    color = OledBlack,
                    modifier = Modifier
                        .background(StarkWhite)
                        .clickable {
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            onRemoveDaemon(daemon.id)
                        }
                        .padding(horizontal = 12.dp, vertical = 6.dp)
                )
            }
        }
    }
}

@Composable
fun NumericBrutalistNumpad(
    onKeyPress: (String) -> Unit
) {
    val haptic = LocalHapticFeedback.current

    val handlePress = { key: String ->
        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
        onKeyPress(key)
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 8.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp)
    ) {
        val rows = listOf(
            listOf("1", "2", "3"),
            listOf("4", "5", "6"),
            listOf("7", "8", "9")
        )

        for (row in rows) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                for (key in row) {
                    NumericNumpadKey(
                        key = key,
                        modifier = Modifier
                            .weight(1f)
                            .height(56.dp),
                        onClick = { handlePress(key) }
                    )
                }
            }
        }
        
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            NumericNumpadKey(
                key = "0",
                modifier = Modifier
                    .weight(2f)
                    .height(56.dp),
                onClick = { handlePress("0") }
            )
            NumericNumpadKey(
                key = "BACKSPACE",
                modifier = Modifier
                    .weight(1f)
                    .height(56.dp),
                onClick = { handlePress("BACKSPACE") }
            )
        }
    }
}

@Composable
private fun NumericNumpadKey(
    key: String,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()

    val bgColor = if (isPressed) StarkWhite else androidx.compose.ui.graphics.Color(0xFF1C1C1E)
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
                fontSize = 24.sp,
                fontFamily = MaterialTheme.typography.displayLarge.fontFamily
            ),
            color = textColor
        )
    }
}
