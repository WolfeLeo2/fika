package com.wolfeleo2.fika.auth

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.adamglin.PhosphorIcons
import com.adamglin.phosphoricons.Duotone
import com.adamglin.phosphoricons.Fill
import com.adamglin.phosphoricons.duotone.CheckCircle
import com.adamglin.phosphoricons.duotone.Phone
import com.adamglin.phosphoricons.fill.NavigationArrow
import com.wolfeleo2.fika.theme.FikaTheme
import fika.riderapp.generated.resources.*
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel

@Composable
fun SignInRoute(viewModel: SignInViewModel = koinViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    SignInScreen(
        state = state,
        onShopCodeChange = viewModel::onShopCodeChange,
        onChangeShop = viewModel::onChangeShop,
        onPhoneChange = viewModel::onPhoneChange,
        onPinChange = viewModel::onPinChange,
        onSubmit = viewModel::onSubmit,
    )
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun SignInScreen(
    state: SignInUiState,
    onShopCodeChange: (String) -> Unit,
    onChangeShop: () -> Unit,
    onPhoneChange: (String) -> Unit,
    onPinChange: (String) -> Unit,
    onSubmit: () -> Unit,
) {
    var showHelpSheet by rememberSaveable { mutableStateOf(value = false) }

    Column(
        Modifier
            .fillMaxSize()
            .safeDrawingPadding()
            .imePadding()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = FikaTheme.spacing.lg, vertical = FikaTheme.spacing.lg),
        verticalArrangement = Arrangement.spacedBy(FikaTheme.spacing.md),
    ) {
        BrandHeader()

        Column(verticalArrangement = Arrangement.spacedBy(FikaTheme.spacing.xxs)) {
            Text(
                text = stringResource(Res.string.sign_in_title),
                style = MaterialTheme.typography.headlineLarge,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Text(
                text = stringResource(Res.string.sign_in_subtitle),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        Spacer(Modifier.height(FikaTheme.spacing.xs))

        Column(verticalArrangement = Arrangement.spacedBy(FikaTheme.spacing.xxs)) {
            Label(stringResource(Res.string.shop_code_label))
            FieldBox(
                value = state.shopCode,
                onValueChange = onShopCodeChange,
                keyboardOptions = KeyboardOptions(
                    capitalization = KeyboardCapitalization.Characters,
                    keyboardType = KeyboardType.Ascii,
                ),
                textStyle = MaterialTheme.typography.headlineSmall,
            )
            ShopStatusLine(state, onChangeShop)
        }

        Column(verticalArrangement = Arrangement.spacedBy(FikaTheme.spacing.xxs)) {
            Label(stringResource(Res.string.phone_label))
            FieldBox(
                value = state.phone,
                onValueChange = onPhoneChange,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                textStyle = MaterialTheme.typography.titleLarge,
                placeholder = stringResource(Res.string.phone_placeholder),
                prefix = stringResource(Res.string.phone_prefix),
            )
        }

        Column(verticalArrangement = Arrangement.spacedBy(FikaTheme.spacing.xxs)) {
            Label(stringResource(Res.string.pin_label))
            PinField(pin = state.pin, onPinChange = onPinChange)
        }

        state.error?.let {
            Text(
                text = errorText(it),
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodyLarge,
                modifier = Modifier.padding(top = FikaTheme.spacing.xxs),
            )
        }

        Spacer(Modifier.height(FikaTheme.spacing.xs))

        Button(
            onClick = onSubmit,
            enabled = state.canSubmit,
            shapes = ButtonDefaults.shapesFor(ButtonDefaults.MediumContainerHeight),
            modifier = Modifier
                .fillMaxWidth()
                .height(ButtonDefaults.MediumContainerHeight),
        ) {
            if (state.submitting) {
                LoadingIndicator(Modifier.size(28.dp))
            } else {
                Text(
                    text = stringResource(Res.string.sign_in_button),
                    style = ButtonDefaults.textStyleFor(ButtonDefaults.MediumContainerHeight),
                )
            }
        }

        TextButton(
            onClick = { showHelpSheet = true },
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(
                text = stringResource(Res.string.sign_in_help_button),
                textAlign = TextAlign.Center,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }

    if (showHelpSheet) {
        SignInHelpSheet(
            state = state,
            onDismiss = { showHelpSheet = false },
        )
    }
}

@Composable
private fun BrandHeader() {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(FikaTheme.spacing.sm),
    ) {
        Box(
            modifier = Modifier
                .size(44.dp)
                .background(MaterialTheme.colorScheme.primaryContainer, FikaTheme.shapes.surface),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                PhosphorIcons.Fill.NavigationArrow,
                contentDescription = null,
                modifier = Modifier.size(22.dp),
                tint = MaterialTheme.colorScheme.primary,
            )
        }
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(FikaTheme.spacing.xs),
        ) {
            Text(
                text = "Fika",
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Surface(
                color = MaterialTheme.colorScheme.secondaryContainer,
                shape = FikaTheme.shapes.small,
            ) {
                Text(
                    text = "Rider",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSecondaryContainer,
                    modifier = Modifier.padding(horizontal = FikaTheme.spacing.xs, vertical = FikaTheme.spacing.xxs),
                )
            }
        }
    }
}

@Composable
private fun Label(text: String) = Text(text, style = MaterialTheme.typography.labelLarge)

@Composable
internal fun FieldBox(
    value: String,
    onValueChange: (String) -> Unit,
    keyboardOptions: KeyboardOptions,
    textStyle: androidx.compose.ui.text.TextStyle,
    placeholder: String? = null,
    prefix: String? = null,
) {
    TextField(
        value = value,
        onValueChange = onValueChange,
        modifier = Modifier.fillMaxWidth(),
        singleLine = true,
        textStyle = textStyle,
        keyboardOptions = keyboardOptions,
        placeholder = placeholder?.let { { Text(it, style = textStyle) } },
        prefix = prefix?.let { { Text("$it ", style = textStyle) } },
        shape = FikaTheme.shapes.surface,
        colors = TextFieldDefaults.colors(
            focusedIndicatorColor = androidx.compose.ui.graphics.Color.Transparent,
            unfocusedIndicatorColor = androidx.compose.ui.graphics.Color.Transparent,
            focusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
            unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
        ),
    )
}

@Composable
private fun ShopStatusLine(state: SignInUiState, onChangeShop: () -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        val (text, color) = when (val shop = state.shop) {
            ShopState.Idle -> null to MaterialTheme.colorScheme.onSurface
            ShopState.Checking -> stringResource(Res.string.shop_checking) to MaterialTheme.colorScheme.onSurfaceVariant
            is ShopState.Found ->
                if (state.shopSuspended) stringResource(Res.string.error_shop_suspended) to MaterialTheme.colorScheme.error
                else shop.shop.name to FikaTheme.success.success
            ShopState.NotFound -> stringResource(Res.string.error_shop_not_found) to MaterialTheme.colorScheme.error
            ShopState.RateLimited -> stringResource(Res.string.error_shop_rate_limited) to MaterialTheme.colorScheme.error
            ShopState.NetworkError -> stringResource(Res.string.error_no_connection) to MaterialTheme.colorScheme.error
        }
        if ((state.shop is ShopState.Found) && !state.shopSuspended) {
            Icon(PhosphorIcons.Duotone.CheckCircle, null, Modifier.size(20.dp), tint = color)
            Spacer(Modifier.size(FikaTheme.spacing.xs))
        }
        if (text != null) Text(text, color = color, style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
        else Spacer(Modifier.weight(1f))
        if (state.rememberedShop) TextButton(onChangeShop) { Text(stringResource(Res.string.change_shop)) }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SignInHelpSheet(
    state: SignInUiState,
    onDismiss: () -> Unit,
) {
    val uriHandler = LocalUriHandler.current
    val foundShop = (state.shop as? ShopState.Found)?.shop
    val shopPhone = foundShop?.phone

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        shape = FikaTheme.shapes.large,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = FikaTheme.spacing.lg)
                .padding(bottom = FikaTheme.spacing.xl),
            verticalArrangement = Arrangement.spacedBy(FikaTheme.spacing.md),
        ) {
            Text(
                text = stringResource(Res.string.help_sheet_title),
                style = MaterialTheme.typography.titleLarge,
            )

            if (foundShop != null) {
                Text(
                    text = stringResource(Res.string.help_sheet_shop_desc, foundShop.name),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                if (shopPhone != null) {
                    FilledTonalButton(
                        onClick = { uriHandler.openUri("tel:$shopPhone") },
                        shapes = ButtonDefaults.shapesFor(ButtonDefaults.MediumContainerHeight),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(ButtonDefaults.MediumContainerHeight),
                    ) {
                        Icon(PhosphorIcons.Duotone.Phone, contentDescription = null)
                        Spacer(Modifier.width(FikaTheme.spacing.xs))
                        Text(
                            text = stringResource(Res.string.help_action_call_shop, shopPhone),
                            style = ButtonDefaults.textStyleFor(ButtonDefaults.MediumContainerHeight),
                        )
                    }
                }
            } else {
                Text(
                    text = stringResource(Res.string.help_sheet_generic_desc),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            Button(
                onClick = onDismiss,
                shapes = ButtonDefaults.shapesFor(ButtonDefaults.MediumContainerHeight),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(ButtonDefaults.MediumContainerHeight),
            ) {
                Text(
                    text = stringResource(Res.string.close),
                    style = ButtonDefaults.textStyleFor(ButtonDefaults.MediumContainerHeight),
                )
            }
        }
    }
}

@Composable
private fun errorText(error: SignInError): String = stringResource(
    when (error) {
        SignInError.InvalidPhone -> Res.string.error_invalid_phone
        SignInError.InvalidCredentials -> Res.string.error_invalid_credentials
        SignInError.UserBanned -> Res.string.error_user_banned
        SignInError.NoConnection -> Res.string.error_no_connection
        SignInError.Unknown -> Res.string.error_unknown
    },
)

@Preview
@Composable
private fun SignInScreenPreview() {
    FikaTheme {
        SignInScreen(
            state = SignInUiState(
                shopCode = "FIKA01",
                shop = ShopState.Found(Shop(name = "Fika Roastery", logoUrl = null, status = ShopStatus.Active, phone = "+254700000000")),
                rememberedShop = true,
                phone = "712345678",
                pin = "1234",
            ),
            onShopCodeChange = {},
            onChangeShop = {},
            onPhoneChange = {},
            onPinChange = {},
            onSubmit = {},
        )
    }
}
