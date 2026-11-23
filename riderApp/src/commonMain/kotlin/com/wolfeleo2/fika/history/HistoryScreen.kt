package com.wolfeleo2.fika.history

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ButtonGroupDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.ListItemShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.ScrollField
import androidx.compose.material3.ScrollFieldColors
import androidx.compose.material3.ScrollFieldDefaults
import androidx.compose.material3.ScrollFieldState
import androidx.compose.material3.SegmentedListItem
import androidx.compose.material3.SheetValue
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.ToggleButton
import androidx.compose.material3.ToggleButtonShapes
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.rememberBottomSheetState
import androidx.compose.material3.rememberScrollFieldState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.adamglin.PhosphorIcons
import com.adamglin.phosphoricons.Duotone
import com.adamglin.phosphoricons.duotone.CalendarBlank
import com.adamglin.phosphoricons.duotone.Receipt
import com.wolfeleo2.fika.orders.ClosedOrder
import com.wolfeleo2.fika.orders.HistoryPeriod
import com.wolfeleo2.fika.orders.HistoryUiState
import com.wolfeleo2.fika.orders.HistoryViewModel
import com.wolfeleo2.fika.orders.OrderStatus
import com.wolfeleo2.fika.orders.PaymentOutcome
import com.wolfeleo2.fika.orders.displayName
import com.wolfeleo2.fika.orders.formatKes
import com.wolfeleo2.fika.theme.FikaTheme
import com.wolfeleo2.fika.theme.ShimmerBox
import com.wolfeleo2.fika.theme.segmentedShapes
import com.wolfeleo2.fika.today.OfflineBanner
import fika.riderapp.generated.resources.*
import kotlinx.coroutines.launch
import kotlinx.datetime.LocalDate
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringArrayResource
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel

private val SegmentGap = 2.dp

/** Keeps rows readable on tablets, foldables and landscape. */
private val ContentMaxWidth = 600.dp

private enum class HistoryFilter(val label: StringResource) {
    All(Res.string.history_filter_all),
    Cash(Res.string.history_filter_cash),
    Mpesa(Res.string.history_filter_mpesa),
    Prepaid(Res.string.history_filter_prepaid),
}

private val LocalDateSaver = Saver<LocalDate, String>(
    save = { it.toString() },
    restore = { LocalDate.parse(it) },
)

private fun formatDate(date: LocalDate, months: List<String>): String =
    "${months[date.monthNumber - 1]} ${date.dayOfMonth}, ${date.year}"

@Composable
fun HistoryRoute(
    contentPadding: PaddingValues,
    viewModel: HistoryViewModel = koinViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    LifecycleResumeEffect(Unit) {
        viewModel.onResume()
        onPauseOrDispose { }
    }
    HistoryScreen(
        state = state,
        onSelectPeriod = viewModel::selectPeriod,
        onRefresh = viewModel::refresh,
        contentPadding = contentPadding,
    )
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun HistoryScreen(
    state: HistoryUiState,
    onSelectPeriod: (HistoryPeriod) -> Unit,
    onRefresh: () -> Unit,
    contentPadding: PaddingValues,
) {
    val spacing = FikaTheme.spacing
    var filter by rememberSaveable { mutableStateOf(HistoryFilter.All) }

    val orders = state.orders
    val filtered = when (filter) {
        HistoryFilter.All -> orders
        HistoryFilter.Cash -> orders.filter { it.outcome == PaymentOutcome.Cash }
        HistoryFilter.Mpesa -> orders.filter { it.outcome == PaymentOutcome.Mpesa }
        HistoryFilter.Prepaid -> orders.filter { it.outcome == PaymentOutcome.PaidUpfront }
    }

    PullToRefreshBox(isRefreshing = state.refreshing, onRefresh = onRefresh) {
        Box(Modifier.fillMaxHeight().fillMaxWidth(), contentAlignment = Alignment.TopCenter) {
            LazyColumn(
                modifier = Modifier
                    .fillMaxHeight()
                    .widthIn(max = ContentMaxWidth)
                    .fillMaxWidth(),
                contentPadding = PaddingValues(
                    start = spacing.md,
                    end = spacing.md,
                    top = contentPadding.calculateTopPadding() + spacing.sm,
                    bottom = contentPadding.calculateBottomPadding() + spacing.lg,
                ),
                verticalArrangement = Arrangement.spacedBy(SegmentGap),
            ) {
                item(key = "header") {
                    Text(
                        text = stringResource(Res.string.history_title),
                        style = MaterialTheme.typography.headlineSmallEmphasized,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                }

                item(key = "period-selector") {
                    PeriodSelector(
                        selected = state.period,
                        today = state.today,
                        onSelect = onSelectPeriod,
                        modifier = Modifier.padding(top = spacing.sm),
                    )
                }

                if (state.offline) {
                    item(key = "offline") { OfflineBanner(Modifier.padding(top = spacing.sm)) }
                }

                if (state.loading) {
                    item(key = "skeleton") {
                        HistorySkeleton(Modifier.padding(top = spacing.md))
                    }
                } else {
                    item(key = "summary") {
                        HistorySummary(
                            deliveredCount = state.deliveredCount,
                            cashKes = state.cashTotalKes,
                            modifier = Modifier.padding(top = spacing.md),
                        )
                    }

                    item(key = "filters") {
                        LazyRow(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = spacing.sm, bottom = spacing.sm),
                            horizontalArrangement = Arrangement.spacedBy(spacing.xs),
                        ) {
                            items(HistoryFilter.entries) { option ->
                                FilterChip(
                                    selected = filter == option,
                                    onClick = { filter = option },
                                    label = { Text(stringResource(option.label)) },
                                )
                            }
                        }
                    }

                    if (filtered.isEmpty()) {
                        item(key = "empty") {
                            HistoryEmptyState(
                                hasAnyInPeriod = orders.isNotEmpty(),
                                period = state.period,
                                modifier = Modifier.padding(top = spacing.xl),
                            )
                        }
                    } else {
                        itemsIndexed(filtered, key = { _, order -> order.id }) { index, order ->
                            HistoryRow(
                                order = order,
                                shapes = segmentedShapes(index, filtered.size),
                                modifier = Modifier.animateItem(),
                            )
                        }
                    }
                }
            }
        }
    }
}

/**
 * Month / day / year wheels. [ScrollField] is an endless pager and derives its value as
 * `currentPage % itemCount`, so the day wheel is rebuilt (keyed on [maxDays]) with the current
 * day carried over; otherwise changing month would silently change the selected day.
 * The wheels cannot hide future values, so a future pick snaps back to [today].
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun DateScrollPicker(
    date: LocalDate,
    today: LocalDate,
    onDateChange: (LocalDate) -> Unit,
    modifier: Modifier = Modifier,
) {
    val months = stringArrayResource(Res.array.months_short)
    val years = remember(today) { ((today.year - 2)..today.year).toList() }

    val monthState = rememberScrollFieldState(itemCount = 12, index = date.monthNumber - 1)
    val yearState = rememberScrollFieldState(
        itemCount = years.size,
        index = years.indexOf(date.year).coerceAtLeast(0),
    )
    val year = years[yearState.selectedOption]
    val month = monthState.selectedOption + 1
    val maxDays = remember(month, year) { daysInMonth(year, month) }

    var day by remember { mutableIntStateOf(date.dayOfMonth) }
    val dayState = key(maxDays) {
        rememberScrollFieldState(itemCount = maxDays, index = day.coerceAtMost(maxDays) - 1)
    }
    LaunchedEffect(dayState) {
        snapshotFlow { dayState.selectedOption + 1 }.collect { day = it }
    }

    LaunchedEffect(year, month, dayState.selectedOption) {
        val picked = LocalDate(year, month, dayState.selectedOption + 1)
        if (picked > today) {
            yearState.animateScrollToOption(years.indexOf(today.year))
            monthState.animateScrollToOption(today.monthNumber - 1)
            dayState.animateScrollToOption(today.dayOfMonth - 1)
        } else {
            onDateChange(picked)
        }
    }

    val colors = ScrollFieldDefaults.colors(
        containerColor = MaterialTheme.colorScheme.surfaceContainerLowest,
        selectedContentColor = MaterialTheme.colorScheme.onSurface,
        unselectedContentColor = MaterialTheme.colorScheme.outline,
    )

    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(ScrollFieldDefaults.ScrollFieldHeight * 0.8f),
        horizontalArrangement = Arrangement.spacedBy(FikaTheme.spacing.xs),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Wheel(
            state = monthState,
            description = stringResource(Res.string.history_field_month),
            colors = colors,
            modifier = Modifier.weight(1.2f),
        ) { months[it] }
        Wheel(
            state = dayState,
            description = stringResource(Res.string.history_field_day),
            colors = colors,
            modifier = Modifier.weight(1f),
        ) { (it + 1).toString().padStart(2, '0') }
        Wheel(
            state = yearState,
            description = stringResource(Res.string.history_field_year),
            colors = colors,
            modifier = Modifier.weight(1f),
        ) { years[it].toString() }
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun Wheel(
    state: ScrollFieldState,
    description: String,
    colors: ScrollFieldColors,
    modifier: Modifier = Modifier,
    label: (index: Int) -> String,
) {
    ScrollField(
        state = state,
        colors = colors,
        modifier = modifier
            .fillMaxHeight()
            .semantics { contentDescription = description },
    ) { index, selected ->
        Text(
            text = label(index),
            style = if (selected) {
                MaterialTheme.typography.headlineMedium
            } else {
                MaterialTheme.typography.titleMedium
            },
            color = if (selected) colors.selectedContentColor else colors.unselectedContentColor,
            maxLines = 1,
        )
    }
}

private fun daysInMonth(year: Int, month: Int): Int = when (month) {
    2 -> if (year % 4 == 0 && (year % 100 != 0 || year % 400 == 0)) 29 else 28
    4, 6, 9, 11 -> 30
    else -> 31
}

@Composable
private fun DatePill(text: String, selected: Boolean, modifier: Modifier = Modifier) {
    val scheme = MaterialTheme.colorScheme
    Surface(
        modifier = modifier,
        shape = CircleShape,
        color = if (selected) scheme.secondaryContainer else scheme.surfaceContainerHighest,
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.SemiBold),
            color = if (selected) scheme.onSecondaryContainer else scheme.onSurface,
            maxLines = 1,
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp),
        )
    }
}

/**
 * One segment: a header row (icon, label, date pill) with the wheels revealed below it inside the
 * same container, so the segment itself grows when [expanded]. The header is a flat, transparent
 * list item; the outer [Surface] supplies the segmented shape and clips the ripple to it.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun DateRow(
    label: String,
    dateText: String,
    expanded: Boolean,
    shapes: ListItemShapes,
    onClick: () -> Unit,
    picker: @Composable () -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    val spacing = FikaTheme.spacing
    val flat = RectangleShape
    Surface(shape = shapes.shape, color = scheme.surfaceContainerHigh) {
        Column {
            SegmentedListItem(
                onClick = onClick,
                shapes = ListItemDefaults.shapes(flat, flat, flat, flat, flat, flat),
                colors = ListItemDefaults.segmentedColors(containerColor = Color.Transparent),
                leadingContent = {
                    Icon(
                        imageVector = PhosphorIcons.Duotone.CalendarBlank,
                        contentDescription = null,
                        tint = scheme.onSurfaceVariant,
                    )
                },
                trailingContent = { DatePill(text = dateText, selected = expanded) },
            ) {
                Text(text = label, style = MaterialTheme.typography.titleMedium, color = scheme.onSurface)
            }
            // The picker leaves composition when collapsed and is rebuilt from the date on expand.
            AnimatedVisibility(
                visible = expanded,
                enter = expandVertically() + fadeIn(),
                exit = shrinkVertically() + fadeOut(),
            ) {
                Box(Modifier.padding(start = spacing.md, end = spacing.md, bottom = spacing.md)) {
                    picker()
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun CustomDateRangeContent(
    initialFrom: LocalDate,
    initialTo: LocalDate,
    today: LocalDate,
    onApply: (from: LocalDate, to: LocalDate) -> Unit,
    modifier: Modifier = Modifier,
) {
    // 0 = From open, 1 = To open, -1 = both collapsed.
    var expanded by rememberSaveable { mutableIntStateOf(0) }
    var fromDate by rememberSaveable(stateSaver = LocalDateSaver) { mutableStateOf(initialFrom) }
    var toDate by rememberSaveable(stateSaver = LocalDateSaver) { mutableStateOf(initialTo) }
    val months = stringArrayResource(Res.array.months_short)

    val isInvalidRange = fromDate > toDate
    val spacing = FikaTheme.spacing
    val medium = ButtonDefaults.MediumContainerHeight

    Column(
        modifier = modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = spacing.md)
            .padding(bottom = spacing.xl),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(spacing.md),
    ) {
        Text(
            text = stringResource(Res.string.history_custom_title),
            style = MaterialTheme.typography.titleLarge,
            color = MaterialTheme.colorScheme.onSurface,
        )

        Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(SegmentGap)) {
            DateRow(
                label = stringResource(Res.string.history_range_from),
                dateText = formatDate(fromDate, months),
                expanded = expanded == 0,
                shapes = segmentedShapes(0, 2),
                onClick = { expanded = if (expanded == 0) -1 else 0 },
            ) {
                DateScrollPicker(date = fromDate, today = today, onDateChange = { fromDate = it })
            }
            DateRow(
                label = stringResource(Res.string.history_range_to),
                dateText = formatDate(toDate, months),
                expanded = expanded == 1,
                shapes = segmentedShapes(1, 2),
                onClick = { expanded = if (expanded == 1) -1 else 1 },
            ) {
                DateScrollPicker(date = toDate, today = today, onDateChange = { toDate = it })
            }
        }

        if (isInvalidRange) {
            Text(
                text = stringResource(Res.string.history_range_invalid),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier
                    .align(Alignment.Start)
                    .padding(horizontal = spacing.xxs),
            )
        }

        Button(
            onClick = { onApply(fromDate, toDate) },
            enabled = !isInvalidRange,
            shapes = ButtonDefaults.shapesFor(medium),
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = medium),
        ) {
            Text(
                text = stringResource(Res.string.history_custom_apply),
                style = ButtonDefaults.textStyleFor(medium),
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CustomTimeframeBottomSheet(
    initialFrom: LocalDate,
    initialTo: LocalDate,
    today: LocalDate,
    onDismiss: () -> Unit,
    onApply: (from: LocalDate, to: LocalDate) -> Unit,
) {
    val sheetState = rememberBottomSheetState(
        initialValue = SheetValue.Hidden,
        enabledValues = setOf(SheetValue.Hidden, SheetValue.Expanded),
    )
    val scope = rememberCoroutineScope()

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        CustomDateRangeContent(
            initialFrom = initialFrom,
            initialTo = initialTo,
            today = today,
            onApply = { from, to ->
                onApply(from, to)
                // Animate out first; removing the sheet right away skips the hide animation.
                scope.launch { sheetState.hide() }.invokeOnCompletion {
                    if (!sheetState.isVisible) onDismiss()
                }
            },
        )
    }
}

/** One connected-group segment: radio semantics, single line, weighted to share the width. */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun PeriodToggle(
    checked: Boolean,
    text: String,
    shapes: ToggleButtonShapes,
    onCheckedChange: () -> Unit,
    modifier: Modifier = Modifier,
) {
    ToggleButton(
        checked = checked,
        onCheckedChange = { onCheckedChange() },
        shapes = shapes,
        modifier = modifier.semantics { role = Role.RadioButton },
    ) {
        Text(text = text, maxLines = 1, softWrap = false, overflow = TextOverflow.Ellipsis)
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class, ExperimentalLayoutApi::class)
@Composable
private fun PeriodSelector(
    selected: HistoryPeriod,
    today: LocalDate,
    onSelect: (HistoryPeriod) -> Unit,
    modifier: Modifier = Modifier,
) {
    var showCustomSheet by rememberSaveable { mutableStateOf(false) }
    val months = stringArrayResource(Res.array.months_short)

    val customLabel = if (selected is HistoryPeriod.DateRange) {
        val from = selected.fromDate
        val to = selected.toDate
        val fromText = "${months[from.monthNumber - 1]} ${from.dayOfMonth}"
        val toText = if (from.monthNumber == to.monthNumber && from.year == to.year) {
            "${to.dayOfMonth}"
        } else {
            "${months[to.monthNumber - 1]} ${to.dayOfMonth}"
        }
        "$fromText–$toText"
    } else {
        stringResource(Res.string.history_period_custom)
    }

    // FlowRow sizes weighted items by their intrinsic width, so labels that don't fit on one line
    // wrap to the next row (large font scale, long custom range) instead of truncating.
    FlowRow(
        modifier = modifier.fillMaxWidth().selectableGroup(),
        horizontalArrangement = Arrangement.spacedBy(ButtonGroupDefaults.ConnectedSpaceBetween),
        verticalArrangement = Arrangement.spacedBy(SegmentGap),
    ) {
        PeriodToggle(
            checked = selected == HistoryPeriod.Today,
            text = stringResource(Res.string.history_period_today),
            shapes = ButtonGroupDefaults.connectedLeadingButtonShapes(),
            onCheckedChange = { onSelect(HistoryPeriod.Today) },
            modifier = Modifier.weight(1f),
        )
        PeriodToggle(
            checked = selected == HistoryPeriod.Week,
            text = stringResource(Res.string.history_period_week),
            shapes = ButtonGroupDefaults.connectedMiddleButtonShapes(),
            onCheckedChange = { onSelect(HistoryPeriod.Week) },
            modifier = Modifier.weight(1f),
        )
        PeriodToggle(
            checked = selected is HistoryPeriod.DateRange,
            text = customLabel,
            shapes = ButtonGroupDefaults.connectedTrailingButtonShapes(),
            onCheckedChange = { showCustomSheet = true },
            modifier = Modifier.weight(1f),
        )
    }

    if (showCustomSheet) {
        val (initFrom, initTo) = (selected as? HistoryPeriod.DateRange)
            ?.let { it.fromDate to it.toDate }
            ?: (today to today)
        CustomTimeframeBottomSheet(
            initialFrom = initFrom,
            initialTo = initTo,
            today = today,
            onDismiss = { showCustomSheet = false },
            onApply = { from, to -> onSelect(HistoryPeriod.DateRange(from, to)) },
        )
    }
}

@Preview
@Composable
private fun CustomTimeframeBottomSheetPreview() {
    val sampleDate = LocalDate(2026, 11, 12)
    FikaTheme {
        Surface {
            CustomDateRangeContent(
                initialFrom = sampleDate,
                initialTo = sampleDate,
                today = sampleDate,
                onApply = { _, _ -> },
            )
        }
    }
}

/** Matches HistorySummary + FilterChips + 3 segmented HistoryRows while loading. */
@Composable
private fun HistorySkeleton(modifier: Modifier = Modifier) {
    val spacing = FikaTheme.spacing

    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(spacing.md)) {
        // Summary placeholder
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = spacing.xxs),
            verticalArrangement = Arrangement.spacedBy(spacing.xxs),
        ) {
            ShimmerBox(Modifier.width(64.dp).height(14.dp))
            ShimmerBox(Modifier.width(120.dp).height(36.dp))
            ShimmerBox(Modifier.width(96.dp).height(16.dp))
        }

        // Filter chip placeholders
        Row(horizontalArrangement = Arrangement.spacedBy(spacing.xs)) {
            ShimmerBox(Modifier.width(52.dp).height(32.dp), shape = FikaTheme.shapes.small)
            ShimmerBox(Modifier.width(68.dp).height(32.dp), shape = FikaTheme.shapes.small)
            ShimmerBox(Modifier.width(76.dp).height(32.dp), shape = FikaTheme.shapes.small)
            ShimmerBox(Modifier.width(76.dp).height(32.dp), shape = FikaTheme.shapes.small)
        }

        // 3 segmented history row placeholders
        Column(verticalArrangement = Arrangement.spacedBy(SegmentGap)) {
            HistoryRowSkeleton(RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp, bottomStart = 4.dp, bottomEnd = 4.dp))
            HistoryRowSkeleton(RoundedCornerShape(4.dp))
            HistoryRowSkeleton(RoundedCornerShape(topStart = 4.dp, topEnd = 4.dp, bottomStart = 16.dp, bottomEnd = 16.dp))
        }
    }
}

@Composable
private fun HistoryRowSkeleton(shape: RoundedCornerShape) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = shape,
        color = MaterialTheme.colorScheme.surfaceContainerLow,
    ) {
        Row(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = FikaTheme.spacing.md, vertical = FikaTheme.spacing.sm),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                ShimmerBox(Modifier.width(104.dp).height(16.dp))
                ShimmerBox(Modifier.width(140.dp).height(13.dp))
            }
            ShimmerBox(Modifier.width(76.dp).height(15.dp))
        }
    }
}

@Composable
private fun HistorySummary(
    deliveredCount: Int,
    cashKes: Int,
    modifier: Modifier = Modifier,
) {
    val scheme = MaterialTheme.colorScheme
    val spacing = FikaTheme.spacing

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = spacing.xxs),
        verticalArrangement = Arrangement.spacedBy(spacing.xxs),
    ) {
        if (cashKes > 0) {
            Text(
                text = stringResource(Res.string.history_stat_cash),
                style = MaterialTheme.typography.labelLarge,
                color = scheme.onSurfaceVariant,
            )
            Text(
                text = formatKes(cashKes),
                style = MaterialTheme.typography.displaySmall,
                color = scheme.tertiary,
            )
            Text(
                text = stringResource(Res.string.history_delivered_summary, deliveredCount),
                style = MaterialTheme.typography.bodyMedium,
                color = scheme.onSurfaceVariant,
                modifier = Modifier.padding(top = spacing.xs),
            )
        } else {
            Text(
                text = stringResource(Res.string.history_delivered_summary, deliveredCount),
                style = MaterialTheme.typography.headlineLarge,
                color = scheme.onSurface,
            )
            Text(
                text = stringResource(Res.string.history_no_cash),
                style = MaterialTheme.typography.bodyMedium,
                color = scheme.onSurfaceVariant,
                modifier = Modifier.padding(top = spacing.xxs),
            )
        }
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun HistoryRow(order: ClosedOrder, shapes: ListItemShapes, modifier: Modifier = Modifier) {
    val scheme = MaterialTheme.colorScheme
    val amount = formatKes(order.amountKes ?: 0)
    val outcomeText = when (order.outcome) {
        PaymentOutcome.PaidUpfront -> stringResource(Res.string.outcome_paid_upfront)
        PaymentOutcome.Mpesa -> stringResource(Res.string.outcome_mpesa, amount)
        PaymentOutcome.Cash -> stringResource(Res.string.outcome_cash, amount)
        PaymentOutcome.NotPaid -> stringResource(Res.string.outcome_not_paid)
        PaymentOutcome.Returned -> stringResource(Res.string.outcome_returned)
        PaymentOutcome.Cancelled -> stringResource(Res.string.outcome_cancelled)
    }

    SegmentedListItem(
        onClick = {},
        shapes = shapes,
        modifier = modifier,
        colors = ListItemDefaults.segmentedColors(containerColor = scheme.surfaceContainer),
        supportingContent = order.landmark?.let { landmark ->
            { Text(landmark) }
        },
        trailingContent = {
            Text(
                text = outcomeText,
                style = MaterialTheme.typography.titleSmall,
                color = if (order.outcome == PaymentOutcome.Cash) scheme.tertiary else scheme.onSurface,
            )
        },
    ) {
        Text(displayName(order.customerFirstName), style = MaterialTheme.typography.titleMedium)
    }
}

@Composable
private fun HistoryEmptyState(
    hasAnyInPeriod: Boolean,
    period: HistoryPeriod,
    modifier: Modifier = Modifier,
) {
    val hintRes = if (period == HistoryPeriod.Today) {
        Res.string.history_empty_hint
    } else {
        Res.string.history_empty_period_hint
    }
    Column(
        modifier = modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(FikaTheme.spacing.xs),
    ) {
        Icon(
            imageVector = PhosphorIcons.Duotone.Receipt,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(40.dp),
        )
        Text(
            text = stringResource(if (hasAnyInPeriod) Res.string.history_empty_filter_title else Res.string.history_empty_title),
            style = MaterialTheme.typography.titleLarge,
            color = MaterialTheme.colorScheme.onSurface,
        )
        Text(
            text = stringResource(hintRes),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

private val sampleClosed = listOf(
    ClosedOrder(
        id = "ord-00",
        status = OrderStatus.Delivered,
        customerFirstName = "Mercy",
        landmark = "Westgate, 2nd Floor",
        outcome = PaymentOutcome.Cash,
        amountKes = 900,
    ),
    ClosedOrder(
        id = "ord-01",
        status = OrderStatus.Delivered,
        customerFirstName = "Faith",
        landmark = "Sarit Centre",
        outcome = PaymentOutcome.Mpesa,
        amountKes = 750,
    ),
    ClosedOrder(
        id = "ord-02",
        status = OrderStatus.Delivered,
        customerFirstName = "Kevin",
        landmark = "ABC Place",
        outcome = PaymentOutcome.PaidUpfront,
        amountKes = 500,
    ),
)

@Preview
@Composable
private fun HistoryScreenPreview() {
    FikaTheme {
        HistoryScreen(
            state = HistoryUiState(
                today = LocalDate(2026, 11, 12),
                loading = false,
                orders = sampleClosed,
            ),
            onSelectPeriod = {},
            onRefresh = {},
            contentPadding = PaddingValues(0.dp),
        )
    }
}

@Preview
@Composable
private fun HistoryScreenLoadingPreview() {
    FikaTheme {
        HistoryScreen(
            state = HistoryUiState(
                today = LocalDate(2026, 11, 12),
                loading = true,
                period = HistoryPeriod.Week,
            ),
            onSelectPeriod = {},
            onRefresh = {},
            contentPadding = PaddingValues(0.dp),
        )
    }
}