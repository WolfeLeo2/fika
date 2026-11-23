package com.wolfeleo2.fika.orders

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import com.wolfeleo2.fika.tracking.FakeTracker
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.time.Clock
import kotlin.time.Instant

private fun order(id: String, status: OrderStatus, mode: PaymentMode = PaymentMode.PayOnDelivery, paid: Boolean = false, kes: Int? = 1000) =
    Order(id, id, status, "Ann Lee", "0700", "Near the shop", "shirt", mode, paid, kes, -0.3, 36.0, null, null, null)

private class FakeOrders : OrdersRepository, ProfileRepository {
    var open: Result<List<Order>> = Result.success(emptyList())
    var closed: Result<List<ClosedOrder>> = Result.success(emptyList())
    val events = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    var openCalls = 0
    override suspend fun openOrders() = open.also { openCalls++ }
    override suspend fun closedSince(since: Instant, until: Instant?) = closed
    override fun changes(): Flow<Unit> = events
    override suspend fun statusOf(orderId: String): Result<OrderStatus?> = Result.success(null)
    override suspend fun markPickedUp(orderId: String): Result<Unit> = Result.success(Unit)
    override suspend fun startDelivery(orderId: String): Result<Unit> = Result.success(Unit)
    override suspend fun markDelivered(orderId: String, method: PayMethod?, mpesaCode: String?, unpaidReason: String?): Result<Unit> = Result.success(Unit)
    override suspend fun markReturned(orderId: String, reason: String): Result<Unit> = Result.success(Unit)
    override suspend fun riderName() = "Oliver Kamau"
}

@OptIn(ExperimentalCoroutinesApi::class)
class TodayViewModelTest {
    private val clock = object : Clock { override fun now() = Instant.parse("2026-11-12T12:00:00Z") }

    private val tracker = FakeTracker()

    @BeforeTest fun setUp() = Dispatchers.setMain(UnconfinedTestDispatcher())
    @AfterTest fun tearDown() = Dispatchers.resetMain()

    @Test
    fun onTheWayIsPinnedAndTotalsCountOnlyUnpaidPayOnDelivery() {
        val repo = FakeOrders().apply {
            open = Result.success(
                listOf(
                    order("a", OrderStatus.Assigned, kes = 2200),
                    order("b", OrderStatus.OnTheWay, kes = 3500),
                    order("c", OrderStatus.PickedUp, mode = PaymentMode.Prepaid, paid = true, kes = null),
                ),
            )
        }
        val vm = TodayViewModel(repo, clock, tracker)
        vm.onResume()
        val s = vm.state.value
        assertEquals("b", s.onTheWay?.id)
        assertEquals(listOf("a", "c"), s.upNext.map { it.id })
        assertEquals(3, s.toDeliver)
        assertEquals(5700, s.toCollectKes)
        assertFalse(s.loading)
    }

    @Test
    fun realtimeEventRefetches() {
        val repo = FakeOrders()
        val vm = TodayViewModel(repo, clock, tracker)
        vm.onResume()
        val before = repo.openCalls
        repo.open = Result.success(listOf(order("new", OrderStatus.Assigned)))
        repo.events.tryEmit(Unit)
        assertEquals(before + 1, repo.openCalls)
        assertEquals(listOf("new"), vm.state.value.orders.map { it.id })
    }

    @Test
    fun failedRefetchKeepsOldOrdersAndFlagsOffline() {
        val repo = FakeOrders().apply { open = Result.success(listOf(order("a", OrderStatus.Assigned))) }
        val vm = TodayViewModel(repo, clock, tracker)
        vm.onResume()
        repo.open = Result.failure(RuntimeException("no network"))
        vm.refresh()
        assertTrue(vm.state.value.offline)
        assertEquals(listOf("a"), vm.state.value.orders.map { it.id })
        assertFalse(vm.state.value.refreshing)
    }

    @Test
    fun deliveredCountIgnoresReturns() {
        val repo = FakeOrders().apply {
            closed = Result.success(
                listOf(
                    ClosedOrder("1", OrderStatus.Delivered, "A", null, PaymentOutcome.Cash, 100),
                    ClosedOrder("2", OrderStatus.Returned, "B", null, PaymentOutcome.Returned, null),
                ),
            )
        }
        val vm = TodayViewModel(repo, clock, tracker)
        vm.onResume()
        assertEquals(1, vm.state.value.deliveredCount)
        assertEquals(2, vm.state.value.closedToday.size)
    }

    @Test
    fun trackingStopsWhenNothingIsOnTheWay() {
        val repo = FakeOrders().apply { open = Result.success(listOf(order("a", OrderStatus.Assigned))) }
        TodayViewModel(repo, clock, tracker).onResume()
        assertEquals(1, tracker.stops)
    }

    @Test
    fun trackingKeepsGoingWhileAnOrderIsOnTheWay() {
        val repo = FakeOrders().apply { open = Result.success(listOf(order("a", OrderStatus.OnTheWay))) }
        TodayViewModel(repo, clock, tracker).onResume()
        assertEquals(0, tracker.stops)
    }

    @Test
    fun trackingIsNotStoppedByAFailedRefetch() {
        val repo = FakeOrders().apply { open = Result.failure(RuntimeException("offline")) }
        TodayViewModel(repo, clock, tracker).onResume()
        assertEquals(0, tracker.stops)
    }

    @Test
    fun resumeTrackingRestartsForTheOnTheWayOrder() {
        val repo = FakeOrders().apply { open = Result.success(listOf(order("a", OrderStatus.OnTheWay))) }
        val vm = TodayViewModel(repo, clock, tracker)
        vm.onResume()
        vm.resumeTracking()
        assertEquals(listOf("a" to "Ann"), tracker.started)
        assertTrue(vm.state.value.tracking)
    }

    @Test
    fun missingPermissionIsReported() {
        val repo = FakeOrders().apply { open = Result.success(listOf(order("a", OrderStatus.OnTheWay))) }
        tracker.permission = false
        val vm = TodayViewModel(repo, clock, tracker)
        vm.onResume()
        assertFalse(vm.state.value.locationPermission)
    }
}
