package com.blazify.music.ui.component

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.geometry.Offset
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


/** Animated circular indicator used by download actions throughout the app. */
@Composable
fun BlazeDownloadLoader(
    modifier: Modifier = Modifier,
    color: Color = Color.Unspecified,
    strokeWidth: Dp = 2.dp,
    trackColor: Color = Color.Unspecified,
    strokeCap: StrokeCap = StrokeCap.Round,
) {
    val primary = androidx.compose.material3.MaterialTheme.colorScheme.primary
    val resolvedColor = if (color == Color.Unspecified) primary else color
    val resolvedTrack = if (trackColor == Color.Unspecified) {
        primary.copy(alpha = 0.18f)
    } else trackColor
    val transition = rememberInfiniteTransition(label = "strix-download-loader")
    val rotation by transition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(900, easing = LinearEasing),
            repeatMode = RepeatMode.Restart,
        ),
        label = "strix-download-loader-rotation",
    )

    Canvas(modifier = modifier) {
        val stroke = Stroke(width = strokeWidth.toPx(), cap = strokeCap)
        val diameter = (size.minDimension - stroke.width).coerceAtLeast(0f)
        val topLeft = Offset(
            (size.width - diameter) / 2f,
            (size.height - diameter) / 2f,
        )
        val arcSize = androidx.compose.ui.geometry.Size(diameter, diameter)
        drawArc(
            color = resolvedTrack,
            startAngle = 0f,
            sweepAngle = 360f,
            useCenter = false,
            topLeft = topLeft,
            size = arcSize,
            style = stroke,
        )
        rotate(rotation) {
            drawArc(
                color = resolvedColor,
                startAngle = 0f,
                sweepAngle = 105f,
                useCenter = false,
                topLeft = topLeft,
                size = arcSize,
                style = stroke,
            )
        }
    }
}
