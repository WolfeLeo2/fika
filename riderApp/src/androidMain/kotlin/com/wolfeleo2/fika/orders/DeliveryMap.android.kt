package com.wolfeleo2.fika.orders

import android.annotation.SuppressLint
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

@Composable
actual fun DeliveryMap(destLat: Double, destLng: Double, title: String, modifier: Modifier) {
    val context = LocalContext.current
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    if (!hasMapsKey(context)) return

    val mapView = remember { MapView(context).also { it.onCreate(null) } }

    // The view needs the host's lifecycle forwarded by hand.
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
            mapView.onDestroy()
        }
    }

    val hasLocation = hasLocation(context)
    DisposableEffect(mapView, destLat, destLng) {
        val destination = LatLng(destLat, destLng)
        mapView.getMapAsync { map ->
            map.clear()
            map.uiSettings.setAllGesturesEnabled(false)
            map.uiSettings.isMapToolbarEnabled = false
            map.uiSettings.isZoomControlsEnabled = false
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
        onDispose {}
    }

    AndroidView(factory = { mapView }, modifier = modifier)
}
