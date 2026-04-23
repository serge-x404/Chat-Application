package dev.serge.chatapplication.screen.neobrut

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp

@Composable
fun BrutalButton(text: String, onClick: () -> Unit, modifier: Modifier, color: Color) {

    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()

    val offset by animateDpAsState(
        targetValue = if (isPressed) 0.dp else 4.dp,
        animationSpec = tween(80)
    )

    Box(
        modifier = modifier
            .offset{
                IntOffset(offset.roundToPx(), offset.roundToPx())
            }
            .clickable(
                interactionSource = interactionSource,
                indication = null
            ) { onClick() }
            .background(MaterialTheme.colorScheme.surface)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .offset{
                    IntOffset(-offset.roundToPx(), -offset.roundToPx())
                }
                .border(3.dp, MaterialTheme.colorScheme.surface)
                .background(color)
                .padding(horizontal = 20.dp, vertical = 12.dp)
        ) {
            Text(
                text = text,
                fontWeight = FontWeight.Black,
                color = MaterialTheme.colorScheme.onTertiary,
                modifier = Modifier.align(Alignment.Center)
            )
        }
    }
}