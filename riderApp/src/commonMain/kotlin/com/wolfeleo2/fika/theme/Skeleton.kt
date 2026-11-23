package com.wolfeleo2.fika.theme

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Shape
import com.wolfeleo2.fika.rememberReducedMotion

/**
 * M3-themed animated shimmer brush for skeleton loaders.
 * Falls back to a calm static surface fill when the user has reduced motion enabled.
 */
fun Modifier.shimmer(): Modifier = composed {
    val scheme = MaterialTheme.colorScheme
    val base = scheme.surfaceContainerHigh
    val highlight = scheme.surfaceContainerHighest

    if (rememberReducedMotion()) {
        return@composed background(base)
    }

    val transition = rememberInfiniteTransition(label = "shimmer")
    val progress by transition.animateFloat(
        initialValue = -1f,
        targetValue = 2f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1200, easing = LinearEasing),
            repeatMode = RepeatMode.Restart,
        ),
        label = "shimmer-offset",
    )

    val brush = Brush.linearGradient(
        colors = listOf(base, highlight, base),
        start = Offset(x = progress * 600f, y = 0f),
        end = Offset(x = progress * 600f + 320f, y = 180f),
    )
    background(brush)
}

@Composable
fun ShimmerBox(
    modifier: Modifier = Modifier,
    shape: Shape = FikaTheme.shapes.small,
) {
    Box(modifier = modifier.clip(shape).shimmer())
}