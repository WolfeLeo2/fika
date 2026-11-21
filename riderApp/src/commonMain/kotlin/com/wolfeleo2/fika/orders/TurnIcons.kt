package com.wolfeleo2.fika.orders

import androidx.compose.ui.graphics.vector.ImageVector
import com.adamglin.PhosphorIcons
import com.adamglin.phosphoricons.Bold
import com.adamglin.phosphoricons.bold.ArrowBendUpLeft
import com.adamglin.phosphoricons.bold.ArrowBendUpRight
import com.adamglin.phosphoricons.bold.ArrowClockwise
import com.adamglin.phosphoricons.bold.ArrowCounterClockwise
import com.adamglin.phosphoricons.bold.ArrowElbowUpLeft
import com.adamglin.phosphoricons.bold.ArrowElbowUpRight
import com.adamglin.phosphoricons.bold.ArrowUUpLeft
import com.adamglin.phosphoricons.bold.ArrowUUpRight
import com.adamglin.phosphoricons.bold.ArrowUp
import com.adamglin.phosphoricons.bold.ArrowUpLeft
import com.adamglin.phosphoricons.bold.ArrowUpRight
import com.adamglin.phosphoricons.bold.ArrowsMerge
import com.adamglin.phosphoricons.bold.FlagCheckered
import com.wolfeleo2.fika.tracking.TurnKind

/** The arrow drawn in the navigation banner for a maneuver. Bold weight so it reads at a glance on a moving bike. */
internal fun TurnKind.icon(): ImageVector = when (this) {
    TurnKind.Depart, TurnKind.Straight, TurnKind.Other -> PhosphorIcons.Bold.ArrowUp
    TurnKind.SlightLeft, TurnKind.ForkLeft -> PhosphorIcons.Bold.ArrowUpLeft
    TurnKind.SlightRight, TurnKind.ForkRight -> PhosphorIcons.Bold.ArrowUpRight
    TurnKind.Left -> PhosphorIcons.Bold.ArrowBendUpLeft
    TurnKind.Right -> PhosphorIcons.Bold.ArrowBendUpRight
    TurnKind.SharpLeft -> PhosphorIcons.Bold.ArrowElbowUpLeft
    TurnKind.SharpRight -> PhosphorIcons.Bold.ArrowElbowUpRight
    TurnKind.UTurnLeft -> PhosphorIcons.Bold.ArrowUUpLeft
    TurnKind.UTurnRight -> PhosphorIcons.Bold.ArrowUUpRight
    TurnKind.Merge -> PhosphorIcons.Bold.ArrowsMerge
    TurnKind.RoundaboutClockwise -> PhosphorIcons.Bold.ArrowClockwise
    TurnKind.RoundaboutCounterclockwise -> PhosphorIcons.Bold.ArrowCounterClockwise
    TurnKind.Arrive -> PhosphorIcons.Bold.FlagCheckered
}
