package com.wolfeleo2.fika.theme

import androidx.compose.foundation.shape.CornerBasedShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.ListItemShapes
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/** 4 pt / 8 pt spacing scale for Fika screens. */
@Immutable
data class FikaSpacing(
    val xxs: Dp = 4.dp,
    val xs: Dp = 8.dp,
    val sm: Dp = 12.dp,
    val md: Dp = 16.dp,
    val lg: Dp = 24.dp,
    val xl: Dp = 32.dp,
    val xxl: Dp = 48.dp,
)

val LocalFikaSpacing = staticCompositionLocalOf { FikaSpacing() }

/** Cohesive surface corner radii tokens. */
@Immutable
data class FikaShapes(
    val small: RoundedCornerShape = RoundedCornerShape(8.dp),
    val medium: RoundedCornerShape = RoundedCornerShape(12.dp),
    val surface: RoundedCornerShape = RoundedCornerShape(16.dp),
    val large: RoundedCornerShape = RoundedCornerShape(24.dp),
    val pill: RoundedCornerShape = RoundedCornerShape(999.dp),
)

val LocalFikaShapes = staticCompositionLocalOf { FikaShapes() }

/**
 * Custom wrapper around [ListItemDefaults.segmentedShapes] to fix M3 behavior where
 * count == 1 returns 4dp corners instead of full 16dp container corners.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun segmentedShapes(index: Int, count: Int): ListItemShapes {
    val defaultShapes = ListItemDefaults.shapes()
    if (count == 1) {
        val baseShape = defaultShapes.shape
        if (baseShape is CornerBasedShape) {
            return defaultShapes.copy(shape = RoundedCornerShape(16.dp))
        }
    }
    return ListItemDefaults.segmentedShapes(index, count, defaultShapes)
}
