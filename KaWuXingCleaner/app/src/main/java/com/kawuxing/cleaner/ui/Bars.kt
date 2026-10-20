package com.kawuxing.cleaner.ui

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

/** 不确定进度条（扫描阶段用，因为总文件数未知，不做假进度） */
@Composable
fun IndeterminateBar(
    modifier: Modifier = Modifier,
    color: Color = MaterialTheme.colorScheme.primary
) {
    val track = MaterialTheme.colorScheme.surfaceVariant
    val transition = rememberInfiniteTransition(label = "scan")
    val x by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1200, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "x"
    )
    Canvas(modifier = modifier.fillMaxWidth().height(6.dp)) {
        val h = size.height
        val radius = CornerRadius(h / 2f, h / 2f)
        drawRoundRect(color = track, size = Size(size.width, h), cornerRadius = radius)
        val barW = size.width * 0.34f
        val left = (size.width + barW) * x - barW
        drawRoundRect(
            color = color,
            topLeft = Offset(left, 0f),
            size = Size(barW, h),
            cornerRadius = radius
        )
    }
}

/** 确定进度条（清理阶段用） */
@Composable
fun ProgressBar(
    fraction: Float,
    modifier: Modifier = Modifier,
    color: Color = MaterialTheme.colorScheme.primary
) {
    val track = MaterialTheme.colorScheme.surfaceVariant
    val f = fraction.coerceIn(0f, 1f)
    Canvas(modifier = modifier.fillMaxWidth().height(6.dp)) {
        val h = size.height
        val radius = CornerRadius(h / 2f, h / 2f)
        drawRoundRect(color = track, size = Size(size.width, h), cornerRadius = radius)
        if (f > 0f) {
            drawRoundRect(
                color = color,
                size = Size(size.width * f, h),
                cornerRadius = radius
            )
        }
    }
}
