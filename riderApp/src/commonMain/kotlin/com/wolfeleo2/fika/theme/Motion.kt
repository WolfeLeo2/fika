package com.wolfeleo2.fika.theme

import androidx.compose.animation.core.CubicBezierEasing

/** M3 "emphasized decelerate": a fast start and a long soft landing. For things that arrive. */
val EmphasizedDecelerate = CubicBezierEasing(0.05f, 0.7f, 0.1f, 1f)

/** M3 "emphasized accelerate": a slow start that speeds up. For things that leave. */
val EmphasizedAccelerate = CubicBezierEasing(0.3f, 0f, 0.8f, 0.15f)
