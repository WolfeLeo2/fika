package com.wolfeleo2.fika.tracking

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.PowerManager
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext

@Composable
actual fun rememberTrackingPermissions(): TrackingPermissions {
    val context = LocalContext.current
    var pending by remember { mutableStateOf<((Boolean) -> Unit)?>(null) }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { result ->
        val granted = result[Manifest.permission.ACCESS_FINE_LOCATION] == true || result[Manifest.permission.ACCESS_COARSE_LOCATION] == true
        pending?.invoke(granted)
        pending = null
    }
    return remember(context) {
        object : TrackingPermissions {
            private val prefs = context.getSharedPreferences("fika_tracking", Context.MODE_PRIVATE)

            override fun hasLocation() =
                context.checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED ||
                    context.checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED

            override fun request(onResult: (Boolean) -> Unit) {
                if (hasLocation()) return onResult(true)
                pending = onResult
                launcher.launch(
                    arrayOf(
                        Manifest.permission.ACCESS_FINE_LOCATION,
                        Manifest.permission.ACCESS_COARSE_LOCATION,
                        Manifest.permission.POST_NOTIFICATIONS,
                    ),
                )
            }

            override fun openSettings() {
                context.startActivity(
                    Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:${context.packageName}"))
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                )
            }

            override fun shouldAskBatteryExemption(): Boolean =
                !prefs.getBoolean(BATTERY_ASKED, false) &&
                    !context.getSystemService(PowerManager::class.java).isIgnoringBatteryOptimizations(context.packageName)

            override fun requestBatteryExemption() {
                skipBatteryExemption()
                context.startActivity(
                    Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, Uri.parse("package:${context.packageName}"))
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                )
            }

            override fun skipBatteryExemption() {
                prefs.edit().putBoolean(BATTERY_ASKED, true).apply()
            }
        }
    }
}

private const val BATTERY_ASKED = "battery_asked"
