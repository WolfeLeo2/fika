package com.wolfeleo2.fika

import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.adamglin.PhosphorIcons
import com.adamglin.phosphoricons.Duotone
import com.adamglin.phosphoricons.duotone.User

/** The rider's avatar: initials in a circle (profiles has no photo), a person icon until they load. */
@Composable
fun RiderAvatar(initials: String?, size: Dp, modifier: Modifier = Modifier, ring: Boolean = false) {
    val scheme = MaterialTheme.colorScheme
    Surface(
        modifier = modifier.size(size).then(if (ring) Modifier.border(2.dp, scheme.primary, CircleShape) else Modifier),
        shape = CircleShape,
        color = scheme.primaryContainer,
        contentColor = scheme.onPrimaryContainer,
    ) {
        Box(contentAlignment = Alignment.Center) {
            if (initials != null) Text(initials, style = if (size < 40.dp) MaterialTheme.typography.labelSmall else MaterialTheme.typography.titleSmall)
            else Icon(PhosphorIcons.Duotone.User, null, Modifier.size(size / 2))
        }
    }
}
