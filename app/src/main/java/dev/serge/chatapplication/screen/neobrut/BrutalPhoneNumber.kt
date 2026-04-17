package dev.serge.chatapplication.screen.neobrut

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun BrutalNumberField(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String = "TYPE HERE"
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isFocused by interactionSource.collectIsFocusedAsState()

    val offset by animateDpAsState(
        targetValue = if (isFocused) 0.dp else 4.dp,
        animationSpec = tween(80),
        label = ""
    )

    Box(
        modifier = Modifier
            .offset {
                IntOffset(offset.roundToPx(), offset.roundToPx())
            }
            .background(MaterialTheme.colorScheme.background)
            .padding(horizontal = 12.dp)
    ) {
        Row(
            modifier = Modifier
                .offset {
                    IntOffset(-offset.roundToPx(), -offset.roundToPx())
                }
                .border(3.dp, MaterialTheme.colorScheme.surface)
                .background(MaterialTheme.colorScheme.surfaceContainer)
                .padding(horizontal = 8.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            BasicTextField(
                value = value,
                onValueChange = onValueChange,
                interactionSource = interactionSource,
                textStyle = TextStyle(
                    color = MaterialTheme.colorScheme.surface,
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp
                ),
                keyboardOptions = KeyboardOptions(
                    imeAction = ImeAction.Send,
                    keyboardType = KeyboardType.Phone
                ),
                cursorBrush = SolidColor(MaterialTheme.colorScheme.surface),
                singleLine = true,
                modifier = Modifier
                    .weight(1f)
                    .padding(horizontal = 8.dp, vertical = 8.dp),
                decorationBox = { innerTextField ->
                    Box {
                        if (value.isEmpty()) {
                            Text(
                                text = placeholder,
                                color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f),
                                fontWeight = FontWeight.Black
                            )
                        }
                        innerTextField()
                    }
                }
            )
        }
    }
}