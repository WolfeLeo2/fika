package com.wolfeleo2.fika

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.unit.dp
import com.wolfeleo2.fika.orders.ShopInfo
import com.wolfeleo2.fika.orders.initials
import com.wolfeleo2.fika.theme.FikaTheme
import fika.riderapp.generated.resources.*
import org.jetbrains.compose.resources.stringResource

/** Who is signed in, the shop they work for, a way to call it, and Sign out. */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun AccountScreen(name: String?, shop: ShopInfo?, onSignOut: () -> Unit, contentPadding: PaddingValues) {
    val spacing = FikaTheme.spacing
    val uriHandler = LocalUriHandler.current
    val medium = ButtonDefaults.MediumContainerHeight
    var confirm by rememberSaveable { mutableStateOf(false) }

    Column(
        Modifier
            .fillMaxSize()
            .padding(top = contentPadding.calculateTopPadding(), bottom = contentPadding.calculateBottomPadding())
            .padding(horizontal = spacing.md, vertical = spacing.sm),
        verticalArrangement = Arrangement.spacedBy(spacing.sm),
    ) {
        RiderAvatar(initials(name), 72.dp)
        Column(Modifier.padding(top = spacing.xs)) {
            name?.let { Text(it, style = MaterialTheme.typography.headlineMedium, color = MaterialTheme.colorScheme.onSurface) }
            shop?.let {
                Text(
                    text = stringResource(Res.string.account_shop) + ": " + it.name,
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        shop?.phone?.let { phone ->
            FilledTonalButton(
                onClick = { uriHandler.openUri("tel:$phone") },
                shapes = ButtonDefaults.shapesFor(medium),
                modifier = Modifier.fillMaxWidth().heightIn(min = medium),
            ) { Text(stringResource(Res.string.help_action_call_shop, phone), style = ButtonDefaults.textStyleFor(medium)) }
        }
        Spacer(Modifier.weight(1f))
        OutlinedButton(
            onClick = { confirm = true },
            shapes = ButtonDefaults.shapesFor(medium),
            modifier = Modifier.fillMaxWidth().heightIn(min = medium),
        ) { Text(stringResource(Res.string.sign_out), style = ButtonDefaults.textStyleFor(medium), color = MaterialTheme.colorScheme.error) }
    }

    if (confirm) {
        AlertDialog(
            onDismissRequest = { confirm = false },
            title = { Text(stringResource(Res.string.sign_out_title)) },
            text = { Text(stringResource(Res.string.sign_out_text)) },
            confirmButton = {
                TextButton({ confirm = false; onSignOut() }) { Text(stringResource(Res.string.sign_out), color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = { TextButton({ confirm = false }) { Text(stringResource(Res.string.sign_out_cancel)) } },
        )
    }
}
