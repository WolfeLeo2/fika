package com.wolfeleo2.fika.auth

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.wolfeleo2.fika.theme.FikaTheme

/** Six PIN boxes over one hidden text field; filled boxes show a dot. */
@Composable
fun PinField(
    pin: String,
    onPinChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    length: Int = PIN_LENGTH,
) {
    val cellShape = FikaTheme.shapes.surface
    BasicTextField(
        value = pin,
        onValueChange = onPinChange,
        modifier = modifier.semantics {
            contentDescription = if (pin.isEmpty()) "6-digit PIN input, empty"
            else "6-digit PIN input, ${pin.length} of $length entered"
        },
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
        cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
        decorationBox = {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(FikaTheme.spacing.xs)) {
                repeat(length) { i ->
                    val filled = i < pin.length
                    val active = i == pin.length
                    Box(
                        Modifier
                            .weight(1f)
                            .height(58.dp)
                            .background(
                                if (filled) MaterialTheme.colorScheme.primaryContainer
                                else MaterialTheme.colorScheme.surfaceContainerHigh,
                                cellShape,
                            )
                            .then(
                                if (active) Modifier.border(2.dp, MaterialTheme.colorScheme.primary, cellShape)
                                else Modifier,
                            ),
                        contentAlignment = Alignment.Center,
                    ) {
                        if (filled) Box(Modifier.size(10.dp).background(MaterialTheme.colorScheme.onPrimaryContainer, CircleShape))
                    }
                }
            }
        },
    )
}
