package com.wolfeleo2.fika.orders

import android.annotation.SuppressLint
import android.content.Context
import android.view.ViewGroup
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.MapView
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.LatLngBounds
import com.google.android.gms.maps.model.MarkerOptions

/**
 * Holds the preview map's view for the last destination shown, so coming back to the order does not rebuild and redraw
 * it. A view belongs to one activity, so a new activity (rotation) or a new destination replaces it.
 */
internal object MapCache {
    private var view: MapView? = null
    private var key: String? = null
    private var owner: Context? = null

    /** False until the map's pin, gestures and camera have been set up once. */
    var configured = false

    fun obtain(context: Context, destKey: String): MapView {
        val cached = view
        if (cached != null && key == destKey && owner === context) return cached
        clear()
        return MapView(context).also {
            it.onCreate(null)
            view = it
            key = destKey
            owner = context
            configured = false
        }
    }

    fun clear() {
        view?.let {
            (it.parent as? ViewGroup)?.removeView(it)
            runCatching { it.onDestroy() }
        }
        view = null
        key = null
        owner = null
        configured = false
    }
}

@Composable
actual fun DeliveryMap(destLat: Double, destLng: Double, title: String, modifier: Modifier) {
    val context = LocalContext.current
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    if (!hasMapsKey(context)) return

    val mapKey = "$destLat,$destLng"
    val mapView = remember(mapKey) { MapCache.obtain(context, mapKey) }

    // The view needs the host's lifecycle forwarded by hand. Leaving only pauses it; the cache decides when it dies.
    DisposableEffect(lifecycle, mapView) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_START -> mapView.onStart()
                Lifecycle.Event.ON_RESUME -> mapView.onResume()
                Lifecycle.Event.ON_PAUSE -> mapView.onPause()
                Lifecycle.Event.ON_STOP -> mapView.onStop()
                else -> Unit
            }
        }
        lifecycle.addObserver(observer)
        onDispose {
            lifecycle.removeObserver(observer)
            if (lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) mapView.onPause()
            if (lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)) mapView.onStop()
        }
    }

    val hasLocation = hasLocation(context)
    DisposableEffect(mapView) {
        if (!MapCache.configured) {
            MapCache.configured = true
            val destination = LatLng(destLat, destLng)
            mapView.getMapAsync { map ->
                map.clear()
                map.uiSettings.setAllGesturesEnabled(false)
                map.uiSettings.isMapToolbarEnabled = false
                map.uiSettings.isZoomControlsEnabled = false
                map.uiSettings.isCompassEnabled = false
                map.addMarker(MarkerOptions().position(destination).title(title))
                map.moveCamera(CameraUpdateFactory.newLatLngZoom(destination, 15f))
                if (hasLocation) {
                    @SuppressLint("MissingPermission") // checked by hasLocation above
                    map.isMyLocationEnabled = true
                    map.uiSettings.isMyLocationButtonEnabled = false
                    // Once the rider's position is known, frame both points; until then the pin stays centred.
                    @Suppress("DEPRECATION")
                    map.setOnMyLocationChangeListener { location ->
                        map.setOnMyLocationChangeListener(null)
                        val bounds = LatLngBounds.Builder().include(destination).include(LatLng(location.latitude, location.longitude)).build()
                        runCatching { map.animateCamera(CameraUpdateFactory.newLatLngBounds(bounds, 96)) }
                    }
                }
            }
        }
        onDispose {}
    }

    // A cached view may still sit in the previous screen's layout; take it from there.
    AndroidView(factory = { mapView.also { (it.parent as? ViewGroup)?.removeView(it) } }, modifier = modifier)
}
