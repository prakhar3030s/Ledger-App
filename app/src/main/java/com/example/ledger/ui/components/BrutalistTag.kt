package com.example.ledger.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ledger.ui.theme.SlateGrey
import com.example.ledger.ui.theme.StarkWhite

/**
 * BrutalistTag — A minimal, stark tag pill.
 *
 * No border, no shadow. A strict 4dp radius box with a faint SlateGrey fill
 * and uppercase Inter lettering with wide tracking.
 *
 * @param label The tag text (displayed uppercase, without the slash).
 */
@Composable
fun BrutalistTag(
    label: String,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .background(
                color = SlateGrey.copy(alpha = 0.15f),
                shape = RoundedCornerShape(4.dp)
            )
            .padding(horizontal = 10.dp, vertical = 5.dp)
    ) {
        Text(
            text = label.uppercase(),
            style = MaterialTheme.typography.labelSmall.copy(
                color = StarkWhite,
                letterSpacing = 2.sp
            )
        )
    }
}
