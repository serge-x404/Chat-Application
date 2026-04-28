package dev.serge.chatapplication.screen.neobrut

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun BrutalTopBar(
    title: String,
    modifier: Modifier = Modifier,
    onBackClick: (() -> Unit)? = null,
    navigateToCall: () -> Unit
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()

    val backButton = remember { MutableInteractionSource() }
    val backPressed by backButton.collectIsPressedAsState()

    val offset by animateDpAsState(
        targetValue = if (isPressed) 0.dp else 4.dp,
        animationSpec = tween(80),
        label = ""
    )

    val backOffset by animateDpAsState(
        targetValue = if (backPressed) 0.dp else 4.dp,
        animationSpec = tween(80),
        label = ""
    )

    Box(
        modifier = modifier
            .fillMaxWidth()
            .offset(6.dp, 6.dp)
            .background(MaterialTheme.colorScheme.background)
    ) {
        Row(
            modifier = Modifier
                .offset((-6).dp, (-6).dp)
                .border(3.dp, MaterialTheme.colorScheme.surface)
                .background(MaterialTheme.colorScheme.secondary)
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            onBackClick?.let {
                Box(
                    modifier = modifier
                        .offset {
                            IntOffset(backOffset.roundToPx(), backOffset.roundToPx())
                        }
                        .clickable(
                            interactionSource = backButton
                        ) {it()}
                        .background(MaterialTheme.colorScheme.surface)
                ) {
                    Box(
                        modifier = Modifier
                            .offset {
                                IntOffset(-backOffset.roundToPx(), -backOffset.roundToPx())
                            }
                            .border(3.dp, MaterialTheme.colorScheme.surface)
                            .background(MaterialTheme.colorScheme.tertiary)
                            .padding(horizontal = 12.dp, vertical = 6.dp)
                    ) {
                        Text(
                            "Back".uppercase(),
                            fontWeight = FontWeight.Black
                        )
                    }
                }
                Spacer(Modifier.width(12.dp))
            }

            Text(
                text = title.uppercase(),
                fontWeight = FontWeight.Black,
                fontSize = 20.sp,
                modifier = Modifier.weight(1f)
            )

            Box(
                modifier = modifier
                    .offset {
                        IntOffset(offset.roundToPx(), offset.roundToPx())
                    }
                    .clickable(
                        interactionSource = interactionSource
                    ) {
                        navigateToCall()
                    }
                    .background(MaterialTheme.colorScheme.surface)
            ) {
                Box(
                    modifier = Modifier
                        .offset {
                            IntOffset(-offset.roundToPx(), -offset.roundToPx())
                        }
                        .border(3.dp, MaterialTheme.colorScheme.surface)
                        .background(MaterialTheme.colorScheme.background)
                        .padding(horizontal = 10.dp, vertical = 6.dp)
                ) {
                    Text(
                        text = "Call".uppercase(),
                        color = Color.Black,
                        fontWeight = FontWeight.Bold,
                        fontSize = 12.sp
                    )
                }
            }
        }
    }
}