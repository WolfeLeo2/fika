package com.wolfeleo2.fika.orders

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.toShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathMeasure
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.wolfeleo2.fika.rememberReducedMotion
import com.wolfeleo2.fika.theme.EmphasizedDecelerate
import com.wolfeleo2.fika.theme.FikaTheme
import fika.riderapp.generated.resources.*
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel

/** How long the next delivery is offered before it opens on its own. */
private const val AUTO_ADVANCE_MILLIS = 5_000

@Composable
fun DeliveredRoute(
    customerName: String?,
    outcome: PaymentOutcome,
    amountKes: Int,
    time: String,
    onNext: (String) -> Unit,
    onHome: () -> Unit,
    viewModel: DeliveredViewModel = koinViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    DeliveredScreen(customerName, outcome, amountKes, time, state, onNext, onHome)
}

/**
 * Success screen. About one second of motion on open, in this order: the burst springs in with overshoot and
 * then turns slowly, two rings ripple out, the check draws itself, then the title, receipt and buttons rise in
 * one after another. The Next delivery button fills along its bottom edge over five seconds and opens the next
 * order unless the rider taps first. A short haptic tick marks the burst. All of it is skipped under reduced
 * motion, where nothing opens by itself either.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun DeliveredScreen(
    customerName: String?,
    outcome: PaymentOutcome,
    amountKes: Int,
    time: String,
    state: DeliveredUiState,
    onNext: (String) -> Unit,
    onHome: () -> Unit,
) {
    val green = FikaTheme.success
    val reduced = rememberReducedMotion()
    val haptics = LocalHapticFeedback.current
    val motion = MaterialTheme.motionScheme
    val resting = if (reduced) 1f else 0f
    val burst = remember { Animatable(resting) }
    val ringA = remember { Animatable(resting) }
    val ringB = remember { Animatable(resting) }
    val check = remember { Animatable(resting) }
    val rise = remember { List(4) { Animatable(resting) } } // title, meta line, receipt chips, buttons
    val countdown = remember { Animatable(0f) }
    val spin = if (reduced) 0f else rememberInfiniteTransition(label = "burst-spin")
        .animateFloat(0f, 360f, infiniteRepeatable(tween(30_000, easing = LinearEasing)), label = "burst-spin").value

    LaunchedEffect(reduced) {
        if (reduced) return@LaunchedEffect
        haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
        launch { burst.animateTo(1f, spring(dampingRatio = 0.42f, stiffness = Spring.StiffnessLow)) }
        launch { delay(250); ringA.animateTo(1f, tween(700, easing = EmphasizedDecelerate)) }
        launch { delay(400); ringB.animateTo(1f, tween(700, easing = EmphasizedDecelerate)) }
        launch { delay(380); check.animateTo(1f, tween(420, easing = EmphasizedDecelerate)) }
        rise.forEachIndexed { index, step -> launch { delay(500L + 110L * index); step.animateTo(1f, motion.defaultSpatialSpec()) } }
    }

    val next = state.next
    // Starts once the entrance is done, and only when there is a next order to offer.
    LaunchedEffect(next?.id, reduced) {
        if (next == null || reduced) return@LaunchedEffect
        delay(1_000)
        countdown.animateTo(1f, tween(AUTO_ADVANCE_MILLIS, easing = LinearEasing))
        onNext(next.id)
    }

    Column(
        Modifier.fillMaxSize().background(green.success).safeDrawingPadding().padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
            Box(Modifier.size(200.dp), contentAlignment = Alignment.Center) {
                Canvas(Modifier.matchParentSize()) {
                    val base = size.minDimension / 2
                    listOf(ringA.value, ringB.value).forEach { p ->
                        if (p > 0.001f && p < 0.999f) {
                            drawCircle(green.successContainer, radius = base * (1f + 1.1f * p), style = Stroke(3.dp.toPx()), alpha = (1f - p) * 0.55f)
                        }
                    }
                }
                Box(
                    Modifier
                        .fillMaxSize()
                        .graphicsLayer { scaleX = burst.value; scaleY = burst.value; rotationZ = spin }
                        .background(green.successContainer, MaterialShapes.Cookie12Sided.toShape()),
                )
                Canvas(Modifier.size(96.dp)) { drawCheck(check.value, green.onSuccessContainer) }
            }
            Spacer(Modifier.height(32.dp))
            Text(
                stringResource(Res.string.success_title),
                style = MaterialTheme.typography.displayMedium,
                color = green.onSuccess,
                textAlign = TextAlign.Center,
                modifier = Modifier.rise(rise[0]),
            )
            Text(
                stringResource(Res.string.success_meta, displayName(customerName), time),
                style = MaterialTheme.typography.bodyLarge,
                color = green.onSuccess,
                modifier = Modifier.rise(rise[1]),
            )
            Spacer(Modifier.height(24.dp))
            Column(Modifier.rise(rise[2]), horizontalAlignment = Alignment.CenterHorizontally) {
                Chip(
                    when (outcome) {
                        PaymentOutcome.Mpesa -> stringResource(Res.string.receipt_mpesa, formatKes(amountKes))
                        PaymentOutcome.Cash -> stringResource(Res.string.receipt_cash, formatKes(amountKes))
                        PaymentOutcome.NotPaid -> stringResource(Res.string.receipt_unpaid)
                        else -> stringResource(Res.string.receipt_prepaid)
                    },
                )
                if (state.doneToday > 0) {
                    Spacer(Modifier.height(8.dp))
                    Chip(stringResource(Res.string.success_done_today, state.doneToday))
                }
            }
        }
        val large = ButtonDefaults.LargeContainerHeight
        Column(Modifier.rise(rise[3]), horizontalAlignment = Alignment.CenterHorizontally) {
            if (next != null) {
                Box(Modifier.fillMaxWidth()) {
                    Button(
                        onClick = { onNext(next.id) },
                        shapes = ButtonDefaults.shapesFor(large),
                        colors = ButtonDefaults.buttonColors(containerColor = green.onSuccess, contentColor = green.success),
                        modifier = Modifier.fillMaxWidth().height(large),
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(stringResource(Res.string.success_next), style = MaterialTheme.typography.labelMedium)
                            Text(
                                listOfNotNull(displayName(next.customerName), next.landmark?.substringBefore(',')).joinToString(", "),
                                style = MaterialTheme.typography.titleLarge,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }
                    if (!reduced) {
                        // The countdown: a thin line near the bottom edge of the button that fills over five seconds.
                        Box(
                            Modifier
                                .align(Alignment.BottomCenter)
                                .padding(start = 32.dp, end = 32.dp, bottom = 12.dp)
                                .fillMaxWidth()
                                .height(4.dp)
                                .background(green.success.copy(alpha = 0.2f), CircleShape)
                                .drawBehind {
                                    drawRoundRect(
                                        green.success,
                                        size = Size(size.width * countdown.value, size.height),
                                        cornerRadius = CornerRadius(size.height / 2),
                                    )
                                },
                        )
                    }
                }
                TextButton(onHome, Modifier.padding(top = 8.dp)) {
                    Text(stringResource(Res.string.success_back_today), color = green.onSuccess, style = MaterialTheme.typography.titleMedium)
                }
            } else {
                Button(
                    onClick = onHome,
                    shapes = ButtonDefaults.shapesFor(large),
                    colors = ButtonDefaults.buttonColors(containerColor = green.onSuccess, contentColor = green.success),
                    modifier = Modifier.fillMaxWidth().height(large),
                ) { Text(stringResource(Res.string.success_back_today), style = ButtonDefaults.textStyleFor(large)) }
            }
        }
    }
}

/** Rises 24 dp into place while fading in, driven by [progress] from 0 to 1. */
private fun Modifier.rise(progress: Animatable<Float, *>): Modifier = graphicsLayer {
    alpha = progress.value.coerceIn(0f, 1f)
    translationY = (1f - progress.value) * 24.dp.toPx()
}

/** A check mark that is drawn along its path as [progress] goes from 0 to 1. */
private fun DrawScope.drawCheck(progress: Float, color: Color) {
    if (progress <= 0f) return
    val w = size.width
    val h = size.height
    val path = Path().apply {
        moveTo(w * 0.20f, h * 0.54f)
        lineTo(w * 0.42f, h * 0.75f)
        lineTo(w * 0.80f, h * 0.28f)
    }
    val measure = PathMeasure().apply { setPath(path, false) }
    val drawn = Path()
    measure.getSegment(0f, measure.length * progress, drawn, true)
    drawPath(drawn, color, style = Stroke(width = 12.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round))
}

@Composable
private fun Chip(text: String) {
    Surface(shape = RoundedCornerShape(50), color = FikaTheme.success.onSuccessContainer.copy(alpha = 0.35f), contentColor = FikaTheme.success.onSuccess) {
        Text(text, Modifier.padding(horizontal = 16.dp, vertical = 10.dp), style = MaterialTheme.typography.titleSmall)
    }
}
