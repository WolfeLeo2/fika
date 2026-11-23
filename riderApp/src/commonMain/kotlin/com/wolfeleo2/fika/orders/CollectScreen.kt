package com.wolfeleo2.fika.orders

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ButtonGroupDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MediumFlexibleTopAppBar
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.ToggleButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.adamglin.PhosphorIcons
import com.adamglin.phosphoricons.Duotone
import com.adamglin.phosphoricons.Regular
import com.adamglin.phosphoricons.duotone.CheckCircle
import com.adamglin.phosphoricons.duotone.DeviceMobile
import com.adamglin.phosphoricons.duotone.Money
import com.adamglin.phosphoricons.duotone.Phone
import com.adamglin.phosphoricons.duotone.XCircle
import com.adamglin.phosphoricons.regular.CaretLeft
import com.wolfeleo2.fika.auth.FieldBox
import com.wolfeleo2.fika.theme.FikaTheme
import fika.riderapp.generated.resources.*
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.parameter.parametersOf

@Composable
fun CollectRoute(
    orderId: String,
    onClose: () -> Unit,
    onDelivered: (CollectDone.Delivered) -> Unit,
    onReturned: () -> Unit,
) {
    val viewModel: CollectViewModel = koinViewModel(key = "collect-$orderId", parameters = { parametersOf(orderId) })
    val state by viewModel.state.collectAsStateWithLifecycle()
    LaunchedEffect(state.done) {
        when (val done = state.done) {
            is CollectDone.Delivered -> onDelivered(done)
            CollectDone.Returned -> onReturned()
            null -> {}
        }
    }
    CollectScreen(
        state = state,
        onClose = onClose,
        onSelectMethod = viewModel::selectMethod,
        onMpesaCodeChange = viewModel::onMpesaCodeChange,
        onConfirm = viewModel::confirm,
        onLeaveUnpaid = viewModel::leaveUnpaid,
        onBringBack = viewModel::bringBack,
    )
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class, ExperimentalMaterial3Api::class)
@Composable
fun CollectScreen(
    state: CollectUiState,
    onClose: () -> Unit,
    onSelectMethod: (PayMethod) -> Unit,
    onMpesaCodeChange: (String) -> Unit,
    onConfirm: () -> Unit,
    onLeaveUnpaid: (String) -> Unit,
    onBringBack: (String) -> Unit,
) {
    val order = state.order
    var showSheet by rememberSaveable { mutableStateOf(false) }
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()

    Scaffold(
        modifier = Modifier
            .fillMaxSize()
            .nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            MediumFlexibleTopAppBar(
                title = {
                    Text(
                        text = if (order != null) stringResource(Res.string.order_code, order.code) else stringResource(Res.string.collect_screen_title),
                        style = MaterialTheme.typography.titleLarge,
                    )
                },
                subtitle = order?.let {
                    {
                        Text(
                            text = displayName(it.customerName),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                },
                navigationIcon = {
                    IconButton(
                        onClick = onClose,
                        modifier = Modifier.size(48.dp),
                    ) {
                        Icon(
                            PhosphorIcons.Regular.CaretLeft,
                            contentDescription = stringResource(Res.string.close),
                            tint = MaterialTheme.colorScheme.onSurface,
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                    scrolledContainerColor = MaterialTheme.colorScheme.surfaceContainer,
                ),
                scrollBehavior = scrollBehavior,
            )
        },
        bottomBar = {
            BottomCollectSection(
                state = state,
                onConfirm = onConfirm,
                onOpenProblemSheet = { showSheet = true },
            )
        },
    ) { innerPadding ->
        if (order == null) {
            Box(
                Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
                Alignment.Center,
            ) {
                if (state.loading) {
                    LoadingIndicator()
                } else {
                    Text(stringResource(Res.string.collect_gone), style = MaterialTheme.typography.titleLarge)
                }
            }
        } else {
            Column(
                Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .consumeWindowInsets(innerPadding)
                    .imePadding()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                if (state.prepaid) {
                    PrepaidCard(order)
                } else {
                    AmountCard(order)
                    MethodPicker(state, onSelectMethod, onMpesaCodeChange)
                }

                Spacer(Modifier.height(8.dp))
            }
        }
    }

    if (showSheet) {
        ProblemSheet(
            prepaid = state.prepaid,
            shopPhone = state.shop?.phone,
            busy = state.busy,
            onDismiss = { showSheet = false },
            onLeaveUnpaid = onLeaveUnpaid,
            onBringBack = onBringBack,
        )
    }
}

@Composable
private fun AmountCard(order: Order) {
    val scheme = MaterialTheme.colorScheme
    val amount = order.amountKes ?: 0

    Surface(
        shape = RoundedCornerShape(16.dp),
        color = scheme.surfaceContainer,
        contentColor = scheme.onSurface,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(
            modifier = Modifier.padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            // Eyebrow
            Text(
                text = stringResource(Res.string.collect_amount_label),
                style = MaterialTheme.typography.labelLarge,
                color = scheme.onSurfaceVariant,
            )

            // High-contrast large numeric display
            Row(verticalAlignment = Alignment.Bottom) {
                Text(
                    text = "KES",
                    style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                    modifier = Modifier.padding(bottom = 6.dp),
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    text = formatKes(amount).removePrefix("KES "),
                    style = MaterialTheme.typography.displayLarge.copy(fontWeight = FontWeight.Bold),
                    color = scheme.tertiary,
                )
            }

            HorizontalDivider(color = scheme.outlineVariant.copy(alpha = 0.35f))

            // Order items breakdown
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    text = stringResource(Res.string.collect_items_label),
                    style = MaterialTheme.typography.labelLarge,
                    color = scheme.onSurfaceVariant,
                )
                Text(
                    text = order.items,
                    style = MaterialTheme.typography.bodyLarge,
                    color = scheme.onSurface,
                )
            }
        }
    }
}

@Composable
private fun PrepaidCard(order: Order) {
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = FikaTheme.success.successContainer,
        contentColor = FikaTheme.success.onSuccessContainer,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(
            modifier = Modifier.padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Icon(
                    PhosphorIcons.Duotone.CheckCircle,
                    contentDescription = null,
                    tint = FikaTheme.success.onSuccessContainer,
                    modifier = Modifier.size(24.dp),
                )
                Text(
                    text = stringResource(Res.string.prepaid_title),
                    style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                    color = FikaTheme.success.onSuccessContainer,
                )
            }

            Text(
                text = stringResource(Res.string.collect_prepaid_note),
                style = MaterialTheme.typography.bodyMedium,
                color = FikaTheme.success.onSuccessContainer.copy(alpha = 0.85f),
            )

            HorizontalDivider(color = FikaTheme.success.onSuccessContainer.copy(alpha = 0.2f))

            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    text = stringResource(Res.string.collect_delivering_items),
                    style = MaterialTheme.typography.labelLarge,
                    color = FikaTheme.success.onSuccessContainer.copy(alpha = 0.85f),
                )
                Text(
                    text = order.items,
                    style = MaterialTheme.typography.bodyLarge,
                    color = FikaTheme.success.onSuccessContainer,
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun MethodPicker(
    state: CollectUiState,
    onSelect: (PayMethod) -> Unit,
    onCodeChange: (String) -> Unit,
) {
    val scheme = MaterialTheme.colorScheme

    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(
            text = stringResource(Res.string.collect_how_paid),
            style = MaterialTheme.typography.labelLarge,
            color = scheme.onSurfaceVariant,
        )

        // Connected Toggle Buttons
        Row(horizontalArrangement = Arrangement.spacedBy(ButtonGroupDefaults.ConnectedSpaceBetween)) {
            ToggleButton(
                checked = state.method == PayMethod.Mpesa,
                onCheckedChange = { onSelect(PayMethod.Mpesa) },
                shapes = ButtonGroupDefaults.connectedLeadingButtonShapes(),
                modifier = Modifier
                    .weight(1f)
                    .height(ButtonDefaults.MediumContainerHeight),
            ) {
                Icon(PhosphorIcons.Duotone.DeviceMobile, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text(
                    stringResource(Res.string.pay_mpesa),
                    style = ButtonDefaults.textStyleFor(ButtonDefaults.MediumContainerHeight).copy(fontWeight = FontWeight.SemiBold),
                )
            }
            ToggleButton(
                checked = state.method == PayMethod.Cash,
                onCheckedChange = { onSelect(PayMethod.Cash) },
                shapes = ButtonGroupDefaults.connectedTrailingButtonShapes(),
                modifier = Modifier
                    .weight(1f)
                    .height(ButtonDefaults.MediumContainerHeight),
            ) {
                Icon(PhosphorIcons.Duotone.Money, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text(
                    stringResource(Res.string.pay_cash),
                    style = ButtonDefaults.textStyleFor(ButtonDefaults.MediumContainerHeight).copy(fontWeight = FontWeight.SemiBold),
                )
            }
        }

        // Method Details Panel
        when (state.method) {
            PayMethod.Mpesa -> {
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = scheme.surfaceContainer,
                    contentColor = scheme.onSurface,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Column(
                        Modifier
                            .fillMaxWidth()
                            .padding(18.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        val till = state.shop?.tillNumber
                        val paybill = state.shop?.paybillNumber

                        if (till != null) {
                            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                Text(
                                    text = stringResource(Res.string.collect_till),
                                    style = MaterialTheme.typography.labelLarge,
                                    color = scheme.onSurfaceVariant,
                                )
                                Text(
                                    text = till,
                                    style = MaterialTheme.typography.headlineMedium.copy(
                                        fontWeight = FontWeight.Bold,
                                        letterSpacing = 2.sp,
                                    ),
                                    color = scheme.primary,
                                )
                            }
                        } else if (paybill != null) {
                            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                Text(
                                    text = stringResource(Res.string.collect_paybill),
                                    style = MaterialTheme.typography.labelLarge,
                                    color = scheme.onSurfaceVariant,
                                )
                                Text(
                                    text = paybill,
                                    style = MaterialTheme.typography.headlineMedium.copy(
                                        fontWeight = FontWeight.Bold,
                                        letterSpacing = 2.sp,
                                    ),
                                    color = scheme.primary,
                                )
                                state.shop?.paybillAccount?.let { account ->
                                    Text(
                                        text = stringResource(Res.string.collect_paybill_account, account),
                                        style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                                        color = scheme.onSurface,
                                    )
                                }
                            }
                        }

                        HorizontalDivider(color = scheme.outlineVariant.copy(alpha = 0.5f))

                        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text(
                                text = stringResource(Res.string.mpesa_code_label),
                                style = MaterialTheme.typography.labelLarge,
                                color = scheme.onSurfaceVariant,
                            )
                            FieldBox(
                                value = state.mpesaCode,
                                onValueChange = onCodeChange,
                                keyboardOptions = KeyboardOptions(
                                    capitalization = KeyboardCapitalization.Characters,
                                    keyboardType = KeyboardType.Ascii,
                                ),
                                textStyle = MaterialTheme.typography.titleLarge.copy(
                                    fontWeight = FontWeight.Bold,
                                    letterSpacing = 1.sp,
                                ),
                            )
                            if (!state.mpesaCodeValid) {
                                Text(
                                    stringResource(Res.string.mpesa_code_invalid),
                                    color = scheme.error,
                                    style = MaterialTheme.typography.bodySmall,
                                )
                            }
                            Text(
                                stringResource(Res.string.mpesa_code_hint),
                                style = MaterialTheme.typography.bodySmall,
                                color = scheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }
            PayMethod.Cash -> {
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = scheme.tertiaryContainer,
                    contentColor = scheme.onTertiaryContainer,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Row(
                        modifier = Modifier.padding(18.dp),
                        verticalAlignment = Alignment.Top,
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        Icon(
                            PhosphorIcons.Duotone.Money,
                            contentDescription = null,
                            tint = scheme.onTertiaryContainer,
                            modifier = Modifier.size(24.dp).padding(top = 2.dp),
                        )
                        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text(
                                text = stringResource(Res.string.cash_title),
                                style = MaterialTheme.typography.labelLarge,
                                color = scheme.onTertiaryContainer,
                            )
                            Text(
                                text = stringResource(Res.string.cash_reminder, formatKes(state.order?.amountKes ?: 0)),
                                style = MaterialTheme.typography.bodyLarge,
                                color = scheme.onTertiaryContainer,
                            )
                        }
                    }
                }
            }
            null -> {}
        }
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun BottomCollectSection(
    state: CollectUiState,
    onConfirm: () -> Unit,
    onOpenProblemSheet: () -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    val medium = ButtonDefaults.MediumContainerHeight

    Surface(
        color = scheme.surfaceContainer,
        contentColor = scheme.onSurface,
        modifier = Modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .imePadding(),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            state.error?.let {
                Text(
                    text = rpcErrorText(it),
                    color = scheme.error,
                    style = MaterialTheme.typography.bodyMedium,
                )
            }

            // Primary Medium CTA button with native M3 Expressive button shapes and no icon
            Button(
                onClick = onConfirm,
                enabled = state.canConfirm,
                shapes = ButtonDefaults.shapesFor(medium),
                colors = ButtonDefaults.buttonColors(
                    containerColor = FikaTheme.success.success,
                    contentColor = FikaTheme.success.onSuccess,
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(medium),
            ) {
                if (state.busy) {
                    LoadingIndicator(Modifier.size(24.dp))
                } else {
                    Text(
                        text = stringResource(
                            if (state.prepaid) Res.string.action_delivered else Res.string.action_paid_delivered,
                        ),
                        style = ButtonDefaults.textStyleFor(medium).copy(fontWeight = FontWeight.Bold),
                    )
                }
            }

            // Secondary problem button
            TextButton(
                onClick = onOpenProblemSheet,
                enabled = !state.busy,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(
                    text = stringResource(
                        if (state.prepaid) Res.string.action_didnt_take else Res.string.action_cant_pay,
                    ),
                    color = scheme.error,
                    style = MaterialTheme.typography.titleMedium,
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class, ExperimentalLayoutApi::class)
@Composable
private fun ProblemSheet(
    prepaid: Boolean,
    shopPhone: String?,
    busy: Boolean,
    onDismiss: () -> Unit,
    onLeaveUnpaid: (String) -> Unit,
    onBringBack: (String) -> Unit,
) {
    var reason by rememberSaveable { mutableStateOf("") }
    val uriHandler = LocalUriHandler.current
    val medium = ButtonDefaults.MediumContainerHeight
    val ready = reason.isNotBlank() && !busy

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
    ) {
        Column(
            Modifier
                .padding(horizontal = 16.dp)
                .padding(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Text(
                stringResource(if (prepaid) Res.string.action_didnt_take else Res.string.action_cant_pay),
                style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold),
            )
            Text(stringResource(Res.string.sheet_reason_label), style = MaterialTheme.typography.labelLarge)
            FieldBox(
                value = reason,
                onValueChange = { reason = it.take(280) },
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                textStyle = MaterialTheme.typography.titleMedium,
            )
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(Res.string.reason_no_money, Res.string.reason_wrong_size, Res.string.reason_changed_mind)
                    .filter { !prepaid || it != Res.string.reason_no_money }
                    .forEach { res ->
                        val text = stringResource(res)
                        AssistChip(
                            onClick = { reason = text },
                            label = { Text(text) },
                        )
                    }
            }
            Text(
                stringResource(Res.string.sheet_call_shop_hint),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            FilledTonalButton(
                onClick = { shopPhone?.let { uriHandler.openUri("tel:$it") } },
                enabled = shopPhone != null,
                shapes = ButtonDefaults.shapesFor(medium),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(medium),
            ) {
                Icon(PhosphorIcons.Duotone.Phone, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text(stringResource(Res.string.sheet_call_shop), style = ButtonDefaults.textStyleFor(medium))
            }
            if (!prepaid) {
                FilledTonalButton(
                    onClick = { onLeaveUnpaid(reason) },
                    enabled = ready,
                    shapes = ButtonDefaults.shapesFor(medium),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(medium),
                ) {
                    Text(stringResource(Res.string.sheet_leave_unpaid), style = ButtonDefaults.textStyleFor(medium))
                }
            }
            Button(
                onClick = { onBringBack(reason) },
                enabled = ready,
                shapes = ButtonDefaults.shapesFor(medium),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.error,
                    contentColor = MaterialTheme.colorScheme.onError,
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(medium),
            ) {
                Text(stringResource(Res.string.sheet_bring_back), style = ButtonDefaults.textStyleFor(medium))
            }
        }
    }
}

@Composable
internal fun rpcErrorText(error: RpcError): String = stringResource(
    when (error) {
        RpcError.AlreadyOnTheWay -> Res.string.error_already_on_the_way
        RpcError.InvalidStatus -> Res.string.error_invalid_status
        RpcError.NotYourOrder -> Res.string.error_not_your_order
        RpcError.ShopSuspended -> Res.string.error_shop_suspended
        RpcError.RiderInactive, RpcError.NotARider -> Res.string.error_user_banned
        RpcError.NoConnection -> Res.string.error_no_connection
        else -> Res.string.error_unknown
    },
)

@Preview
@Composable
private fun CollectScreenMpesaPreview() {
    FikaTheme {
        CollectScreen(
            state = CollectUiState(
                loading = false,
                order = Order(
                    id = "ord-1",
                    code = "FK-104",
                    status = OrderStatus.OnTheWay,
                    customerName = "Wanjiku Mwangi",
                    customerPhone = "+254712345678",
                    landmark = "Delta Towers",
                    items = "2x Flat White, 1x Croissant",
                    paymentMode = PaymentMode.PayOnDelivery,
                    paid = false,
                    amountKes = 850,
                    destLat = -1.268,
                    destLng = 36.811,
                    riderNotes = null,
                    etaSeconds = null,
                    distanceM = null,
                ),
                shop = ShopInfo(
                    name = "Fika Coffee Westlands",
                    phone = "+254700000000",
                    tillNumber = "543210",
                    paybillNumber = null,
                    paybillAccount = null,
                ),
                method = PayMethod.Mpesa,
                mpesaCode = "QWE891234K",
            ),
            onClose = {},
            onSelectMethod = {},
            onMpesaCodeChange = {},
            onConfirm = {},
            onLeaveUnpaid = {},
            onBringBack = {},
        )
    }
}

@Preview
@Composable
private fun CollectScreenPrepaidPreview() {
    FikaTheme {
        CollectScreen(
            state = CollectUiState(
                loading = false,
                order = Order(
                    id = "ord-2",
                    code = "FK-105",
                    status = OrderStatus.OnTheWay,
                    customerName = "Brian Ochieng",
                    customerPhone = "+254722334455",
                    landmark = "Riverside Square",
                    items = "1x Iced Latte, 1x Blueberry Muffin",
                    paymentMode = PaymentMode.Prepaid,
                    paid = true,
                    amountKes = 650,
                    destLat = -1.261,
                    destLng = 36.804,
                    riderNotes = null,
                    etaSeconds = null,
                    distanceM = null,
                ),
                method = null,
            ),
            onClose = {},
            onSelectMethod = {},
            onMpesaCodeChange = {},
            onConfirm = {},
            onLeaveUnpaid = {},
            onBringBack = {},
        )
    }
}
