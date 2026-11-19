package com.wolfeleo2.fika.tracking

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.adamglin.PhosphorIcons
import com.adamglin.phosphoricons.Duotone
import com.adamglin.phosphoricons.duotone.GpsSlash
import fika.riderapp.generated.resources.*
import org.jetbrains.compose.resources.stringResource

import com.wolfeleo2.fika.theme.FikaTheme

/**
 * Shown while an order is on the way but nothing is being tracked: either location is off (open settings)
 * or the service stopped, for example after a phone restart (resume).
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun TrackingBanner(
    locationPermission: Boolean,
    onResume: () -> Unit,
    onOpenSettings: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val scheme = MaterialTheme.colorScheme
    Surface(modifier.fillMaxWidth(), shape = FikaTheme.shapes.large, color = scheme.errorContainer, contentColor = scheme.onErrorContainer) {
        Column(Modifier.padding(FikaTheme.spacing.md), verticalArrangement = Arrangement.spacedBy(FikaTheme.spacing.sm)) {
            Row(horizontalArrangement = Arrangement.spacedBy(FikaTheme.spacing.sm), verticalAlignment = Alignment.CenterVertically) {
                Icon(PhosphorIcons.Duotone.GpsSlash, null, Modifier.size(24.dp))
                Text(
                    stringResource(if (locationPermission) Res.string.tracking_banner_paused else Res.string.tracking_banner_no_permission),
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
            Button(
                onClick = if (locationPermission) onResume else onOpenSettings,
                shapes = ButtonDefaults.shapesFor(ButtonDefaults.MediumContainerHeight),
                colors = ButtonDefaults.buttonColors(containerColor = scheme.error, contentColor = scheme.onError),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(
                    stringResource(if (locationPermission) Res.string.action_resume_tracking else Res.string.action_open_settings),
                    style = ButtonDefaults.textStyleFor(ButtonDefaults.MediumContainerHeight),
                )
            }
        }
    }
}

/** The one-line reason shown before the system location prompt. Both buttons start the delivery. */
@Composable
fun LocationReasonDialog(onContinue: () -> Unit, onSkip: () -> Unit) {
    AlertDialog(
        onDismissRequest = onSkip,
        title = { Text(stringResource(Res.string.location_reason_title)) },
        text = { Text(stringResource(Res.string.location_reason_text)) },
        confirmButton = { TextButton(onContinue) { Text(stringResource(Res.string.location_reason_continue)) } },
        dismissButton = { TextButton(onSkip) { Text(stringResource(Res.string.location_reason_skip)) } },
    )
}

/** Asked once, with a plain explanation, before the system "ignore battery optimisation" prompt. */
@Composable
fun BatteryDialog(onAllow: () -> Unit, onLater: () -> Unit) {
    AlertDialog(
        onDismissRequest = onLater,
        title = { Text(stringResource(Res.string.battery_title)) },
        text = { Text(stringResource(Res.string.battery_text)) },
        confirmButton = { TextButton(onAllow) { Text(stringResource(Res.string.battery_allow)) } },
        dismissButton = { TextButton(onLater) { Text(stringResource(Res.string.battery_later)) } },
    )
}
