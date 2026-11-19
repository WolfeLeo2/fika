package com.wolfeleo2.fika.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MaterialExpressiveTheme
import androidx.compose.material3.MotionScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider

object FikaTheme {
    val success: SuccessColors
        @Composable get() = LocalSuccessColors.current
    val spacing: FikaSpacing
        @Composable get() = LocalFikaSpacing.current
    val shapes: FikaShapes
        @Composable get() = LocalFikaShapes.current
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun FikaTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    CompositionLocalProvider(LocalSuccessColors provides if (darkTheme) DarkSuccess else LightSuccess) {
        MaterialExpressiveTheme(
            colorScheme = if (darkTheme) DarkColors else LightColors,
            motionScheme = MotionScheme.expressive(),
            typography = fikaTypography(),
            content = content,
        )
    }
}
