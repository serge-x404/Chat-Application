package dev.serge.chatapplication.screen.neobrut

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

@Composable
fun BrutalLoader(
    modifier: Modifier,
    size: Dp = 48.dp
) {
    val infiniteTransition = rememberInfiniteTransition(label = "")

    val rotation by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(600, easing = LinearEasing)
        ),
        label = ""
    )

    Box(
        modifier = modifier
            .size(size)
            .border(3.dp, MaterialTheme.colorScheme.surface)
            .background(MaterialTheme.colorScheme.background),
        contentAlignment = Alignment.Center
    ) {
        Box(
            modifier = Modifier
                .size(size * 0.6f)
                .graphicsLayer {
                    rotationZ = rotation
                }
                .border(4.dp, MaterialTheme.colorScheme.primary)
        )
    }
}