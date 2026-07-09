package com.slideindex.app.util

fun Float.coerceSafe(min: Float, max: Float): Float =
    if (max < min) min else coerceIn(min, max)

fun Int.coerceSafe(min: Int, max: Int): Int =
    if (max < min) min else coerceIn(min, max)
