package com.wolfeleo2.fika.orders

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import androidx.activity.compose.LocalActivity
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.google.android.libraries.navigation.ListenableResultFuture
import com.google.android.libraries.navigation.NavigationApi
import com.google.android.libraries.navigation.NavigationView
import com.google.android.libraries.navigation.Navigator
import com.google.android.libraries.navigation.Waypoint

/** The key the manifest placeholder fills in from local.properties (`maps.apiKey`). Blank means not set up. */
private const val API_KEY_META = "com.google.android.geo.API_KEY"

/** Remaining time or distance changes of at least this much are reported (they feed each position ping). */
private const val REPORT_EVERY_SECONDS = 10
private const val REPORT_EVERY_METERS = 50

@Composable
actual fun InAppNavigationView(
    destLat: Double,
    destLng: Double,
    title: String,
    onRemaining: (seconds: Int, meters: Int) -> Unit,
    onUnavailable: () -> Unit,
    modifier: Modifier,
) {
    val context = LocalContext.current
    val activity = LocalActivity.current
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val remaining by rememberUpdatedState(onRemaining)
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
        }
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

    DisposableEffect(navView) {
        var navigator: Navigator? = null
        var disposed = false
        fun fallBack() { if (!disposed) unavailable() }

        val progress = Navigator.RemainingTimeOrDistanceChangedListener {
            navigator?.currentTimeAndDistance?.let { remaining(it.seconds, it.meters) }
        }
        // Terms are shown once per install and only if not yet accepted. Declining falls back to Google Maps.
        NavigationApi.showTermsAndConditionsDialog(activity!!, appName(context)) { accepted ->
            if (disposed) return@showTermsAndConditionsDialog
            if (!accepted) return@showTermsAndConditionsDialog fallBack()
            NavigationApi.getNavigator(activity, object : NavigationApi.NavigatorListener {
                override fun onNavigatorReady(ready: Navigator) {
                    if (disposed) return ready.cleanup()
                    navigator = ready
                    navView.getMapAsync { _ ->
                        if (disposed) return@getMapAsync
                        val destination = Waypoint.builder().setLatLng(destLat, destLng).setTitle(title).build()
                        ready.setDestination(destination).setOnResultListener(
                            ListenableResultFuture.OnResultListener<Navigator.RouteStatus> { status ->
                                if (disposed) return@OnResultListener
                                if (status == Navigator.RouteStatus.OK) {
                                    ready.addRemainingTimeOrDistanceChangedListener(REPORT_EVERY_SECONDS, REPORT_EVERY_METERS, progress)
                                    ready.startGuidance()
                                    ready.currentTimeAndDistance?.let { remaining(it.seconds, it.meters) }
                                } else {
                                    fallBack()
                                }
                            },
                        )
                    }
                }

                override fun onError(errorCode: Int) = fallBack()

            })
        }
        onDispose {
            disposed = true
            navigator?.run {
                removeRemainingTimeOrDistanceChangedListener(progress)
                stopGuidance()
                clearDestinations()
                cleanup()
            }
        }
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
