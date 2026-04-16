package dev.serge.chatapplication.screen.neobrut

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun BrutalSendButton(
    enabled: Boolean,
    onClick: () -> Unit
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()

    val offset by animateDpAsState(
        targetValue = if (isPressed) 0.dp else 3.dp,
        animationSpec = tween(80),
        label = ""
    )
    Box(
        modifier = Modifier
            .offset {
                IntOffset(offset.roundToPx(), offset.roundToPx())
            }
            .background(MaterialTheme.colorScheme.surface)
    ) {
        Box(
            modifier = Modifier
                .offset {
                    IntOffset(-offset.roundToPx(), -offset.roundToPx())
                }
                .border(3.dp, MaterialTheme.colorScheme.surface)
                .background(
                    if (enabled)
                        MaterialTheme.colorScheme.tertiary
                    else
                        MaterialTheme.colorScheme.primary
                )
                .clickable(
                    interactionSource = interactionSource,
                    indication = null
                ) {
                    onClick()
                }
                .padding(horizontal = 12.dp, vertical = 8.dp)
        ) {
            Text(
                text = "SEND",
                color = Color.Black,
                fontWeight = FontWeight.Black,
                fontSize = 12.sp
            )
        }
    }
}