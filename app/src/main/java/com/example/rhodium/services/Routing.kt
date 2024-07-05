package com.example.rhodium.services

import androidx.compose.ui.graphics.Color

enum class RouteColor(val color: Color) {
    GREEN(Color.Green),
    YELLOW(Color.Yellow),
    ORANGE(Color(0xFFFFA500)), // Orange color
    RED(Color.Red),
    BLACK(Color.Black),
    NOCOLOR(Color.Transparent)
}

fun getColorByIndex(index: Int): Color {
    return when (index) {
        1 -> RouteColor.GREEN.color
        2 -> RouteColor.YELLOW.color
        3 -> RouteColor.ORANGE.color
        4 -> RouteColor.RED.color
        5 -> RouteColor.BLACK.color
        else -> RouteColor.NOCOLOR.color
    }
}
