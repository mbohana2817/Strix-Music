package com.blazify.music.ui.component

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import com.blazify.music.R

/**
 * Lightweight Strix anime-mark loader. Keeps the existing API so every screen
 * using the old waveform loader automatically receives the current brand mark.
 */
@Suppress("UNUSED_PARAMETER")
@Composable
fun BlazeLoader(
    modifier: Modifier = Modifier,
    color: Color = Color.Unspecified,
    strokeWidth: Dp = Dp.Unspecified,
    trackColor: Color = Color.Unspecified,
    strokeCap: StrokeCap = StrokeCap.Round,
) {
    val transition = rememberInfiniteTransition(label = "strix-anime-loader")
    val pulse by transition.animateFloat(
        initialValue = 0.94f,
        targetValue = 1.04f,
        animationSpec = infiniteRepeatable(
            animation = tween(1150, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "strix-anime-loader-pulse",
    )

    Image(
        painter = painterResource(R.drawable.strix_logo_mark),
        contentDescription = "Strix Music",
        modifier = modifier
            .size(44.dp)
            .graphicsLayer {
                scaleX = pulse
                scaleY = pulse
            },
    )
}
