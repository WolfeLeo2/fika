package com.wolfeleo2.fika

import android.app.Service
import android.content.Intent
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.Messenger
import com.google.android.libraries.mapsplatform.turnbyturn.TurnByTurnManager
import com.google.android.libraries.mapsplatform.turnbyturn.model.Maneuver
import com.google.android.libraries.mapsplatform.turnbyturn.model.NavInfo
import com.google.android.libraries.mapsplatform.turnbyturn.model.NavState
import com.google.android.libraries.mapsplatform.turnbyturn.model.StepInfo
import com.wolfeleo2.fika.tracking.NavGuidance
import com.wolfeleo2.fika.tracking.NavStep
import com.wolfeleo2.fika.tracking.NavigationProgress
import com.wolfeleo2.fika.tracking.TurnKind
import org.koin.android.ext.android.get

/**
 * Receives the Navigation SDK's turn-by-turn feed (registered while in-app navigation runs) so the app can draw
 * its own banner instead of Google's. The SDK binds to this service in-process; it is not exported.
 */
class NavInfoService : Service() {
    private val manager = TurnByTurnManager.createInstance()
    private val messenger by lazy {
        Messenger(
            Handler(Looper.getMainLooper()) { message ->
                if (message.what != TurnByTurnManager.MSG_NAV_INFO) return@Handler false
                manager.readNavInfoFromBundle(message.data)?.let { get<NavigationProgress>().guidance.value = it.toGuidance() }
                true
            },
        )
    }

    override fun onBind(intent: Intent?): IBinder = messenger.binder
}

private fun NavInfo.toGuidance(): NavGuidance? {
    val current = currentStep ?: return null
    return NavGuidance(
        step = current.toStep(),
        distanceToStepMeters = distanceToCurrentStepMeters,
        upcoming = remainingSteps.orEmpty().map { it.toStep() },
        rerouting = navState == NavState.REROUTING,
    )
}

private fun StepInfo.toStep() = NavStep(
    turn = maneuver.toTurn(),
    // The full name first: it carries "toward ..." and the road aliases. The simple name is the fallback.
    road = fullRoadName?.takeIf { it.isNotBlank() } ?: simpleRoadName?.takeIf { it.isNotBlank() },
    distanceFromPreviousMeters = distanceFromPrevStepMeters,
)

/** The SDK has about 60 maneuvers; the banner distinguishes the ones a rider acts differently on. */
private fun Int.toTurn(): TurnKind = when (this) {
    Maneuver.DEPART -> TurnKind.Depart
    Maneuver.DESTINATION, Maneuver.DESTINATION_LEFT, Maneuver.DESTINATION_RIGHT -> TurnKind.Arrive
    Maneuver.STRAIGHT, Maneuver.NAME_CHANGE -> TurnKind.Straight
    Maneuver.TURN_LEFT, Maneuver.ON_RAMP_LEFT, Maneuver.OFF_RAMP_LEFT -> TurnKind.Left
    Maneuver.TURN_RIGHT, Maneuver.ON_RAMP_RIGHT, Maneuver.OFF_RAMP_RIGHT -> TurnKind.Right
    Maneuver.TURN_KEEP_LEFT, Maneuver.TURN_SLIGHT_LEFT, Maneuver.ON_RAMP_KEEP_LEFT, Maneuver.ON_RAMP_SLIGHT_LEFT,
    Maneuver.OFF_RAMP_KEEP_LEFT, Maneuver.OFF_RAMP_SLIGHT_LEFT -> TurnKind.SlightLeft
    Maneuver.TURN_KEEP_RIGHT, Maneuver.TURN_SLIGHT_RIGHT, Maneuver.ON_RAMP_KEEP_RIGHT, Maneuver.ON_RAMP_SLIGHT_RIGHT,
    Maneuver.OFF_RAMP_KEEP_RIGHT, Maneuver.OFF_RAMP_SLIGHT_RIGHT -> TurnKind.SlightRight
    Maneuver.TURN_SHARP_LEFT, Maneuver.ON_RAMP_SHARP_LEFT, Maneuver.OFF_RAMP_SHARP_LEFT -> TurnKind.SharpLeft
    Maneuver.TURN_SHARP_RIGHT, Maneuver.ON_RAMP_SHARP_RIGHT, Maneuver.OFF_RAMP_SHARP_RIGHT -> TurnKind.SharpRight
    Maneuver.TURN_U_TURN_COUNTERCLOCKWISE, Maneuver.ON_RAMP_U_TURN_COUNTERCLOCKWISE,
    Maneuver.OFF_RAMP_U_TURN_COUNTERCLOCKWISE -> TurnKind.UTurnLeft
    Maneuver.TURN_U_TURN_CLOCKWISE, Maneuver.ON_RAMP_U_TURN_CLOCKWISE, Maneuver.OFF_RAMP_U_TURN_CLOCKWISE -> TurnKind.UTurnRight
    Maneuver.MERGE_UNSPECIFIED, Maneuver.MERGE_LEFT, Maneuver.MERGE_RIGHT -> TurnKind.Merge
    Maneuver.FORK_LEFT -> TurnKind.ForkLeft
    Maneuver.FORK_RIGHT -> TurnKind.ForkRight
    Maneuver.ROUNDABOUT_CLOCKWISE, Maneuver.ROUNDABOUT_STRAIGHT_CLOCKWISE, Maneuver.ROUNDABOUT_LEFT_CLOCKWISE,
    Maneuver.ROUNDABOUT_RIGHT_CLOCKWISE, Maneuver.ROUNDABOUT_SLIGHT_LEFT_CLOCKWISE, Maneuver.ROUNDABOUT_SLIGHT_RIGHT_CLOCKWISE,
    Maneuver.ROUNDABOUT_SHARP_LEFT_CLOCKWISE, Maneuver.ROUNDABOUT_SHARP_RIGHT_CLOCKWISE, Maneuver.ROUNDABOUT_U_TURN_CLOCKWISE,
    Maneuver.ROUNDABOUT_EXIT_CLOCKWISE -> TurnKind.RoundaboutClockwise
    Maneuver.ROUNDABOUT_COUNTERCLOCKWISE, Maneuver.ROUNDABOUT_STRAIGHT_COUNTERCLOCKWISE, Maneuver.ROUNDABOUT_LEFT_COUNTERCLOCKWISE,
    Maneuver.ROUNDABOUT_RIGHT_COUNTERCLOCKWISE, Maneuver.ROUNDABOUT_SLIGHT_LEFT_COUNTERCLOCKWISE,
    Maneuver.ROUNDABOUT_SLIGHT_RIGHT_COUNTERCLOCKWISE, Maneuver.ROUNDABOUT_SHARP_LEFT_COUNTERCLOCKWISE,
    Maneuver.ROUNDABOUT_SHARP_RIGHT_COUNTERCLOCKWISE, Maneuver.ROUNDABOUT_U_TURN_COUNTERCLOCKWISE,
    Maneuver.ROUNDABOUT_EXIT_COUNTERCLOCKWISE -> TurnKind.RoundaboutCounterclockwise
    else -> TurnKind.Other
}
