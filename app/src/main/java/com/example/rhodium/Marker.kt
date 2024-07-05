package com.example.rhodium

import android.graphics.BlurMaskFilter.Blur
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlurEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.unit.dp
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp

@Composable
fun LocationMarker(x: Float, y: Float) {
    Canvas(modifier = Modifier.size(40.dp)) {
        val radius = size.minDimension / 4

        // Draw blurred outer circles to create a blur effect
        for (i in 1..10) {
            drawCircle(
                color = Color(0xFF007BB5).copy(alpha = 0.1f * (11 - i)),
                radius = radius * 1.7f * i / 10,
                center = Offset(x, y)
            )
        }

        // Draw outer circle
        drawCircle(
            color = Color(0xFFAEDFF7), // Light blue color
            radius = radius,
            center = Offset(x, y)
        )

        // Draw inner circle
        drawCircle(
            color = Color(0xFF007BB5), // Blue color
            radius = radius * 0.5f,
            center = Offset(x, y)
        )
    }
}


@Composable
fun pxToDp(px: Float): Dp {
    return with(LocalDensity.current) { px.toDp() }
}