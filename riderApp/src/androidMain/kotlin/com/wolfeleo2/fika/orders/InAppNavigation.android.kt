package com.wolfeleo2.fika.orders

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import androidx.activity.compose.LocalActivity
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.view.WindowCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.google.android.libraries.navigation.ListenableResultFuture
import com.google.android.libraries.navigation.ForceNightMode
import com.google.android.libraries.navigation.NavigationApi
import com.google.android.libraries.navigation.NavigationUpdatesOptions
import com.google.android.libraries.navigation.NavigationView
import com.google.android.libraries.navigation.Navigator
import com.google.android.libraries.navigation.Waypoint

/** The key the manifest placeholder fills in from local.properties (`maps.apiKey`). Blank means not set up. */
private const val API_KEY_META = "com.google.android.geo.API_KEY"

/** Remaining time or distance changes of at least this much are reported (they feed each position ping). */
private const val REPORT_EVERY_SECONDS = 10
private const val REPORT_EVERY_METERS = 50

/** How many steps after the current one the turn feed carries, for the steps list. */
private const val UPCOMING_STEPS = 15

/** Declared in the app module's manifest; the SDK binds to it by name. */
private const val NAV_INFO_SERVICE = "com.wolfeleo2.fika.NavInfoService"

actual fun endNavigationSession() = NavigationSession.end()

@Composable
actual fun InAppNavigationView(
    destLat: Double,
    destLng: Double,
    title: String,
    darkTheme: Boolean,
    overviewRequest: Int,
    onRemaining: (seconds: Int, meters: Int) -> Unit,
    onArrived: () -> Unit,
    onUnavailable: () -> Unit,
    modifier: Modifier,
) {
    val context = LocalContext.current
    val activity = LocalActivity.current
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val remaining by rememberUpdatedState(onRemaining)
    val arrived by rememberUpdatedState(onArrived)
    val unavailable by rememberUpdatedState(onUnavailable)

    val canRun = activity != null && hasMapsKey(context) && hasLocation(context)
    if (!canRun) {
        LaunchedEffect(Unit) { unavailable() }
        return
    }

    val navView = remember {
        NavigationView(activity).also {
            it.onCreate(null)
            // The screen's own bottom panel shows time and distance, so the SDK's ETA card would duplicate it.
            it.setEtaCardEnabled(false)
            // Disable the SDK's default green header so Fika can render its own clean, custom top banner.
            it.setHeaderEnabled(false)
        }
    }

    // The SDK decides day or night by the clock unless told; the app's own light or dark choice wins.
    SideEffect { navView.setForceNightMode(if (darkTheme) ForceNightMode.FORCE_NIGHT else ForceNightMode.FORCE_DAY) }

    // The banner above the map is near black in both themes, so the status bar needs light icons while this shows.
    DisposableEffect(activity) {
        val controller = WindowCompat.getInsetsController(activity!!.window, activity.window.decorView)
        val previous = controller.isAppearanceLightStatusBars
        controller.isAppearanceLightStatusBars = false
        onDispose { controller.isAppearanceLightStatusBars = previous }
    }

    // The view needs the host's lifecycle forwarded by hand.
    DisposableEffect(lifecycle, navView) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_START -> navView.onStart()
                Lifecycle.Event.ON_RESUME -> navView.onResume()
                Lifecycle.Event.ON_PAUSE -> navView.onPause()
                Lifecycle.Event.ON_STOP -> navView.onStop()
                else -> Unit
            }
        }
        lifecycle.addObserver(observer)
        onDispose {
            lifecycle.removeObserver(observer)
            if (lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) navView.onPause()
            if (lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)) navView.onStop()
            navView.onDestroy()
        }
    }

    // A running session for this destination is reused instead of asking for the route again: every route request
    // is a billed destination. Leaving the screen only detaches the listeners; the session ends with the delivery.
    DisposableEffect(navView) {
        var disposed = false
        fun fallBack() { if (!disposed) unavailable() }
        val key = NavigationSession.keyFor(destLat, destLng)

        val arrival = Navigator.ArrivalListener { event -> if (event.isFinalDestination && !disposed) arrived() }
        val progress = Navigator.RemainingTimeOrDistanceChangedListener {
            NavigationSession.navigator?.currentTimeAndDistance?.let { remaining(it.seconds, it.meters) }
        }

        fun attach(nav: Navigator) {
            NavigationSession.navigator = nav
            navView.getMapAsync { map ->
                if (disposed) return@getMapAsync
                map.uiSettings.isCompassEnabled = false
                nav.addRemainingTimeOrDistanceChangedListener(REPORT_EVERY_SECONDS, REPORT_EVERY_METERS, progress)
                nav.addArrivalListener(arrival)
                // Feeds NavInfoService with the next maneuver, road name and distance for the app's own banner.
                nav.registerServiceForNavUpdates(
                    context.packageName,
                    NAV_INFO_SERVICE,
                    NavigationUpdatesOptions.builder().setNumNextStepsToPreview(UPCOMING_STEPS).build(),
                )
                if (NavigationSession.destinationKey == key && nav.isGuidanceRunning) {
                    // Already navigating here: no new route, just show it again on this view.
                    nav.startGuidance()
                    nav.currentTimeAndDistance?.let { remaining(it.seconds, it.meters) }
                } else {
                    val destination = Waypoint.builder().setLatLng(destLat, destLng).setTitle(title).build()
                    nav.setDestination(destination).setOnResultListener(
                        ListenableResultFuture.OnResultListener<Navigator.RouteStatus> { status ->
                            if (disposed) return@OnResultListener
                            if (status == Navigator.RouteStatus.OK) {
                                NavigationSession.destinationKey = key
                                nav.startGuidance()
                                nav.currentTimeAndDistance?.let { remaining(it.seconds, it.meters) }
                            } else {
                                fallBack()
                            }
                        },
                    )
                }
            }
        }

        val existing = NavigationSession.navigator
        if (existing != null) {
            attach(existing)
        } else {
            // Terms are shown once per install and only if not yet accepted. Declining falls back to Google Maps.
            NavigationApi.showTermsAndConditionsDialog(activity!!, appName(context)) { accepted ->
                if (disposed) return@showTermsAndConditionsDialog
                if (!accepted) return@showTermsAndConditionsDialog fallBack()
                NavigationApi.getNavigator(activity, object : NavigationApi.NavigatorListener {
                    override fun onNavigatorReady(ready: Navigator) {
                        // Even if the screen is already gone, keep the navigator so the next visit reuses it.
                        if (disposed) NavigationSession.navigator = ready else attach(ready)
                    }

                    override fun onError(errorCode: Int) = fallBack()
                })
            }
        }
        onDispose {
            disposed = true
            NavigationSession.navigator?.run {
                removeRemainingTimeOrDistanceChangedListener(progress)
                removeArrivalListener(arrival)
                unregisterServiceForNavUpdates()
            }
        }
    }

    // Zoom out to the whole route when asked; the SDK's own recenter button brings the camera back to following.
    LaunchedEffect(overviewRequest) {
        if (overviewRequest > 0) runCatching { navView.showRouteOverview() }
    }

    AndroidView(factory = { navView }, modifier = modifier)
}

@Suppress("DEPRECATION") // the flags overload needs API 33; minSdk is 31
internal fun hasMapsKey(context: Context): Boolean = runCatching {
    context.packageManager.getApplicationInfo(context.packageName, PackageManager.GET_META_DATA).metaData?.getString(API_KEY_META)
}.getOrNull().isNullOrBlank().not()

internal fun hasLocation(context: Context): Boolean =
    context.checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED

private fun appName(context: Context): String = context.applicationInfo.loadLabel(context.packageManager).toString()
