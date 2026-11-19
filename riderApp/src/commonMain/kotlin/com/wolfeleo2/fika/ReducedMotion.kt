package com.wolfeleo2.fika

import androidx.compose.runtime.Composable

/** True when the user asked the system to reduce or remove animations. Decorative motion must then be skipped. */
@Composable
expect fun rememberReducedMotion(): Boolean
