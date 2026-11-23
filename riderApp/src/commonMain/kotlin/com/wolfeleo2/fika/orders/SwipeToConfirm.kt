package com.wolfeleo2.fika.orders

import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.disabled
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.adamglin.PhosphorIcons
import com.adamglin.phosphoricons.Bold
import com.adamglin.phosphoricons.Duotone
import com.adamglin.phosphoricons.Regular
import com.adamglin.phosphoricons.bold.Check
import com.adamglin.phosphoricons.duotone.Check
import com.adamglin.phosphoricons.duotone.PersonSimpleBike
import com.adamglin.phosphoricons.regular.ArrowRight
import com.wolfeleo2.fika.theme.FikaTheme
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

private val Thumb = 56.dp
private val Pad = 4.dp

/** Dragging past this share of the track confirms: the thumb snaps to the end and nothing more is needed. */
private const val CONFIRM_AT = 0.85f

/**
 * Drag the thumb across to confirm. Used for actions with side effects (starting a delivery turns on
 * tracking), so a stray tap can't trigger them.
 *
 * Feedback while dragging: a green fill follows the thumb, the thumb turns from the accent to white,
 * and the label fades. Past the threshold the thumb snaps to the end with a haptic and shows a check.
 * Letting go earlier springs it back. Screen readers get a plain click action instead of a drag.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
internal fun SwipeToConfirm(
    label: String,
    enabled: Boolean,
    busy: Boolean,
    onConfirm: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val scheme = MaterialTheme.colorScheme
    val green = FikaTheme.success
    val density = LocalDensity.current
    val haptics = LocalHapticFeedback.current
    val scope = rememberCoroutineScope()
    val spec = MaterialTheme.motionScheme.defaultSpatialSpec<Float>()
    val offset = remember { Animatable(0f) }
    var confirmed by remember { mutableStateOf(false) }
    var trackPx by remember { mutableIntStateOf(0) }
    val padPx = with(density) { Pad.toPx() }
    val thumbPx = with(density) { Thumb.toPx() }
    val maxOffset = (trackPx - thumbPx - 2 * padPx).coerceAtLeast(1f)
    val progress = (offset.value / maxOffset).coerceIn(0f, 1f)
    val live = enabled && !busy && !confirmed

    fun confirm() {
        if (!live) return
        confirmed = true
        haptics.performHapticFeedback(HapticFeedbackType.LongPress)
        scope.launch {
            offset.animateTo(maxOffset, spec)
            onConfirm()
        }
    }

    // Back to the start whenever the action isn't in flight, for example after an error or a declined prompt.
    LaunchedEffect(busy, enabled) {
        if (!busy) {
            confirmed = false
            offset.animateTo(0f, spec)
        }
    }

    Box(
        modifier
            .fillMaxWidth()
            .height(Thumb + Pad * 2)
            .clip(CircleShape)
            .background(scheme.surfaceContainerHighest)
            .drawBehind {
                val activeWidth = if (progress > 0f) padPx + offset.value + thumbPx else 0f
                if (activeWidth > 0f) {
                    drawRoundRect(
                        color = green.success,
                        size = Size(activeWidth, size.height),
                        cornerRadius = androidx.compose.ui.geometry.CornerRadius(size.height / 2, size.height / 2),
                    )
                }
            }
            .onSizeChanged { trackPx = it.width }
            .semantics(mergeDescendants = true) {
                role = Role.Button
                contentDescription = label
                if (!live) disabled()
                onClick(label) {
                    confirm()
                    live
                }
            },
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.titleMedium,
            color = scheme.onSurface,
            modifier = Modifier
                .align(Alignment.Center)
                .graphicsLayer { alpha = (1f - progress * 1.6f).coerceIn(0f, 1f) },
        )
        Box(
            Modifier
                .align(Alignment.CenterStart)
                .offset { IntOffset((padPx + offset.value).roundToInt(), 0) }
                .size(Thumb)
                .background(
                    when {
                        !enabled -> scheme.outline
                        confirmed -> green.success
                        else -> scheme.primary
                    },
                    CircleShape,
                )
                .draggable(
                    enabled = enabled && !busy && !confirmed,
                    orientation = Orientation.Horizontal,
                    state = rememberDraggableState { delta ->
                        if (confirmed) return@rememberDraggableState
                        scope.launch {
                            if (confirmed) return@launch
                            val next = (offset.value + delta).coerceIn(0f, maxOffset)
                            offset.snapTo(next)
                            if (next >= maxOffset * CONFIRM_AT) confirm()
                        }
                    },
                    onDragStopped = { if (!confirmed) offset.animateTo(0f, spec) },
                ),
            contentAlignment = Alignment.Center,
        ) {
            when {
                busy -> LoadingIndicator(Modifier.size(32.dp), color = green.onSuccess)
                confirmed -> Icon(PhosphorIcons.Duotone.Check, null, Modifier.size(28.dp), tint = green.onSuccess)
                else -> Icon(
                    PhosphorIcons.Duotone.PersonSimpleBike,
                    contentDescription = null,
                    modifier = Modifier.size(28.dp),
                    tint = if (enabled) scheme.onPrimary else scheme.surface,
                )
            }
        }
    }
}
