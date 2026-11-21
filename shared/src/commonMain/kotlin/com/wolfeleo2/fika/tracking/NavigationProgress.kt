package com.wolfeleo2.fika.tracking

import kotlinx.coroutines.flow.MutableStateFlow

/** Time and distance left on the route, as reported by the navigator. */
class RemainingTrip(val seconds: Int, val meters: Int)

/** What the driver has to do at a step, reduced to what the banner can draw. */
enum class TurnKind {
    Depart, Straight, SlightLeft, Left, SharpLeft, SlightRight, Right, SharpRight,
    UTurnLeft, UTurnRight, Merge, ForkLeft, ForkRight, RoundaboutClockwise, RoundaboutCounterclockwise, Arrive, Other,
}

/** One maneuver: what to do, on which road, and how far it is from the maneuver before it. */
class NavStep(val turn: TurnKind, val road: String?, val distanceFromPreviousMeters: Int? = null)

/**
 * The upcoming maneuver and how far away it is, the steps after it (as many as the feed was asked for), and
 * whether the route is being recalculated.
 */
class NavGuidance(val step: NavStep, val distanceToStepMeters: Int?, val upcoming: List<NavStep>, val rerouting: Boolean) {
    val next: NavStep? get() = upcoming.firstOrNull()
}

/**
 * The in-app navigation screen writes the remaining trip, and the turn feed writes the guidance. The tracking
 * service reads the trip for each ping (`eta_seconds`, `distance_m`). Both are null whenever no navigation
 * session is running.
 */
class NavigationProgress {
    val remaining = MutableStateFlow<RemainingTrip?>(null)
    val guidance = MutableStateFlow<NavGuidance?>(null)
}
