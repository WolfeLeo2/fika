package com.wolfeleo2.fika.orders

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import com.wolfeleo2.fika.tracking.FakeTracker
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Instant

private fun o(id: String, status: OrderStatus) =
    Order(id, id, status, "Ann Lee", "0700", null, "shirt", PaymentMode.Prepaid, true, null, null, null, null, null, null)

private class FakeRepo : OrdersRepository {
    var open = listOf<Order>()
    var status: OrderStatus? = null
    var actionResult: Result<Unit> = Result.success(Unit)
    val calls = mutableListOf<String>()
    override suspend fun openOrders() = Result.success(open)
    override suspend fun closedSince(since: Instant, until: Instant?) = Result.success(emptyList<ClosedOrder>())
    override fun changes(): Flow<Unit> = emptyFlow()
    override suspend fun statusOf(orderId: String) = Result.success(status)
    override suspend fun markPickedUp(orderId: String) = actionResult.also { calls += "pickedUp:$orderId" }
    override suspend fun startDelivery(orderId: String) = actionResult.also { calls += "start:$orderId" }
    override suspend fun markDelivered(orderId: String, method: PayMethod?, mpesaCode: String?, unpaidReason: String?) = actionResult
    override suspend fun markReturned(orderId: String, reason: String) = actionResult
}

@OptIn(ExperimentalCoroutinesApi::class)
class OrderDetailViewModelTest {
    private val tracker = FakeTracker()

    @BeforeTest fun setUp() = Dispatchers.setMain(UnconfinedTestDispatcher())
    @AfterTest fun tearDown() = Dispatchers.resetMain()

    @Test
    fun startIsBlockedWhileAnotherOrderIsOnTheWay() {
        val repo = FakeRepo().apply { open = listOf(o("a", OrderStatus.PickedUp), o("b", OrderStatus.OnTheWay)) }
        val vm = OrderDetailViewModel("a", repo, tracker)
        vm.onResume()
        assertTrue(vm.state.value.otherOnTheWay)
        assertFalse(vm.state.value.canStart)
        vm.startDelivery()
        assertTrue(repo.calls.isEmpty())
    }

    @Test
    fun startCallsRpcAndReloads() {
        val repo = FakeRepo().apply { open = listOf(o("a", OrderStatus.PickedUp)) }
        val vm = OrderDetailViewModel("a", repo, tracker)
        vm.onResume()
        assertTrue(vm.state.value.canStart)
        repo.open = listOf(o("a", OrderStatus.OnTheWay))
        vm.startDelivery()
        assertEquals(listOf("start:a"), repo.calls)
        assertEquals(OrderStatus.OnTheWay, vm.state.value.order?.status)
        assertFalse(vm.state.value.busy)
    }

    @Test
    fun rpcFailureSurfacesItsCode() {
        val repo = FakeRepo().apply {
            open = listOf(o("a", OrderStatus.PickedUp))
            actionResult = Result.failure(RpcFailure(RpcError.AlreadyOnTheWay))
        }
        val vm = OrderDetailViewModel("a", repo, tracker)
        vm.onResume()
        vm.startDelivery()
        assertEquals(RpcError.AlreadyOnTheWay, vm.state.value.error)
        vm.dismissError()
        assertNull(vm.state.value.error)
    }

    @Test
    fun missingOrderIsReassignedCancelledOrFinished() {
        mapOf(null to GoneReason.Reassigned, OrderStatus.Cancelled to GoneReason.Cancelled, OrderStatus.Delivered to GoneReason.Finished)
            .forEach { (status, reason) ->
                val repo = FakeRepo().apply { this.status = status }
                val vm = OrderDetailViewModel("a", repo, tracker)
                vm.onResume()
                assertEquals(reason, vm.state.value.gone, "status=$status")
            }
    }

    @Test
    fun rpcCodesAreFoundInMessageOrHint() {
        assertEquals(RpcError.AlreadyOnTheWay, mapRpcError("already_on_the_way", null))
        assertEquals(RpcError.ShopSuspended, mapRpcError("P0001", "shop_suspended"))
        assertEquals(RpcError.Unknown, mapRpcError("boom", null))
    }

    @Test
    fun startingADeliveryStartsTracking() {
        val repo = FakeRepo().apply { open = listOf(o("a", OrderStatus.PickedUp)) }
        val vm = OrderDetailViewModel("a", repo, tracker)
        vm.onResume()
        repo.open = listOf(o("a", OrderStatus.OnTheWay))
        vm.startDelivery()
        assertEquals(listOf("a" to "Ann"), tracker.started)
        assertTrue(vm.state.value.justStarted)
        vm.onStartedHandled()
        assertFalse(vm.state.value.justStarted)
    }

    @Test
    fun deliveryStillStartsWithoutLocationPermission() {
        tracker.permission = false
        val repo = FakeRepo().apply { open = listOf(o("a", OrderStatus.PickedUp)) }
        val vm = OrderDetailViewModel("a", repo, tracker)
        vm.onResume()
        repo.open = listOf(o("a", OrderStatus.OnTheWay))
        vm.startDelivery()
        assertEquals(OrderStatus.OnTheWay, vm.state.value.order?.status)
        assertTrue(tracker.started.isEmpty())
        assertFalse(vm.state.value.locationPermission)
        assertFalse(vm.state.value.tracking)
    }

    @Test
    fun failedStartDoesNotStartTracking() {
        val repo = FakeRepo().apply {
            open = listOf(o("a", OrderStatus.PickedUp))
            actionResult = Result.failure(RpcFailure(RpcError.AlreadyOnTheWay))
        }
        val vm = OrderDetailViewModel("a", repo, tracker)
        vm.onResume()
        vm.startDelivery()
        assertTrue(tracker.started.isEmpty())
        assertFalse(vm.state.value.justStarted)
    }

    @Test
    fun resumeTrackingOnlyForAnOnTheWayOrder() {
        val repo = FakeRepo().apply { open = listOf(o("a", OrderStatus.PickedUp)) }
        val vm = OrderDetailViewModel("a", repo, tracker)
        vm.onResume()
        vm.resumeTracking()
        assertTrue(tracker.started.isEmpty())
        repo.open = listOf(o("a", OrderStatus.OnTheWay))
        vm.onResume()
        vm.resumeTracking()
        assertEquals(1, tracker.started.size)
    }
}
