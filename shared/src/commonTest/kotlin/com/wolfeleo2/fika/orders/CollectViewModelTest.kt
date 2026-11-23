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
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Clock
import kotlin.time.Instant

private fun onTheWay(mode: PaymentMode, kes: Int? = 3500) =
    Order("o1", "0001", OrderStatus.OnTheWay, "Wanjiru Mwangi", "0700", null, "dress", mode, mode == PaymentMode.Prepaid, kes, -0.3, 36.0, null, null, null)

private class Repo(var order: Order?) : OrdersRepository {
    var delivered: List<Any?>? = null
    var returned: String? = null
    var result: Result<Unit> = Result.success(Unit)
    override suspend fun openOrders() = Result.success(listOfNotNull(order))
    override suspend fun closedSince(since: Instant, until: Instant?) = Result.success(emptyList<ClosedOrder>())
    override fun changes(): Flow<Unit> = emptyFlow()
    override suspend fun statusOf(orderId: String) = Result.success<OrderStatus?>(null)
    override suspend fun markPickedUp(orderId: String) = result
    override suspend fun startDelivery(orderId: String) = result
    override suspend fun markDelivered(orderId: String, method: PayMethod?, mpesaCode: String?, unpaidReason: String?) =
        result.also { delivered = listOf(method, mpesaCode, unpaidReason) }
    override suspend fun markReturned(orderId: String, reason: String) = result.also { returned = reason }
}

private object Shops : ShopRepository {
    override suspend fun myShop() = Result.success(ShopInfo("Amani", "0700", "12345", null, null))
}

@OptIn(ExperimentalCoroutinesApi::class)
class CollectViewModelTest {
    private val clock = object : Clock { override fun now() = Instant.parse("2026-11-12T12:41:00Z") }

    private val tracker = FakeTracker()

    @BeforeTest fun setUp() = Dispatchers.setMain(UnconfinedTestDispatcher())
    @AfterTest fun tearDown() = Dispatchers.resetMain()

    private fun vm(repo: Repo) = CollectViewModel("o1", repo, Shops, clock, tracker)

    @Test
    fun payOnDeliveryNeedsAMethodBeforeConfirming() {
        val vm = vm(Repo(onTheWay(PaymentMode.PayOnDelivery)))
        assertFalse(vm.state.value.canConfirm)
        vm.selectMethod(PayMethod.Cash)
        assertTrue(vm.state.value.canConfirm)
    }

    @Test
    fun mpesaCodeIsOptionalButMustBeValidWhenGiven() {
        val vm = vm(Repo(onTheWay(PaymentMode.PayOnDelivery)))
        vm.selectMethod(PayMethod.Mpesa)
        assertTrue(vm.state.value.canConfirm)
        vm.onMpesaCodeChange("tkl3 x9")
        assertEquals("TKL3X9", vm.state.value.mpesaCode)
        assertFalse(vm.state.value.canConfirm)
        vm.onMpesaCodeChange("tkl3x9ab2c")
        assertTrue(vm.state.value.canConfirm)
    }

    @Test
    fun confirmSendsMethodAndCodeThenFinishes() {
        val repo = Repo(onTheWay(PaymentMode.PayOnDelivery))
        val vm = vm(repo)
        vm.selectMethod(PayMethod.Mpesa)
        vm.onMpesaCodeChange("TKL3X9AB2C")
        vm.confirm()
        assertEquals(listOf<Any?>(PayMethod.Mpesa, "TKL3X9AB2C", null), repo.delivered)
        val done = assertIs<CollectDone.Delivered>(vm.state.value.done)
        assertEquals(PaymentOutcome.Mpesa, done.outcome)
        assertEquals(3500, done.amountKes)
    }

    @Test
    fun cashNeverSendsAnMpesaCode() {
        val repo = Repo(onTheWay(PaymentMode.PayOnDelivery))
        val vm = vm(repo)
        vm.selectMethod(PayMethod.Mpesa)
        vm.onMpesaCodeChange("TKL3X9AB2C")
        vm.selectMethod(PayMethod.Cash)
        vm.confirm()
        assertEquals(listOf<Any?>(PayMethod.Cash, null, null), repo.delivered)
    }

    @Test
    fun prepaidConfirmsWithoutAnything() {
        val repo = Repo(onTheWay(PaymentMode.Prepaid, kes = null))
        val vm = vm(repo)
        assertTrue(vm.state.value.canConfirm)
        vm.confirm()
        assertEquals(listOf<Any?>(null, null, null), repo.delivered)
        assertEquals(PaymentOutcome.PaidUpfront, assertIs<CollectDone.Delivered>(vm.state.value.done).outcome)
    }

    @Test
    fun leaveUnpaidNeedsAReasonAndSendsOnlyTheReason() {
        val repo = Repo(onTheWay(PaymentMode.PayOnDelivery))
        val vm = vm(repo)
        vm.leaveUnpaid("  ")
        assertNull(repo.delivered)
        vm.leaveUnpaid(" Had no money ")
        assertEquals(listOf<Any?>(null, null, "Had no money"), repo.delivered)
        assertEquals(PaymentOutcome.NotPaid, assertIs<CollectDone.Delivered>(vm.state.value.done).outcome)
    }

    @Test
    fun prepaidCannotBeLeftUnpaid() {
        val repo = Repo(onTheWay(PaymentMode.Prepaid, kes = null))
        vm(repo).leaveUnpaid("reason")
        assertNull(repo.delivered)
    }

    @Test
    fun bringBackNeedsAReason() {
        val repo = Repo(onTheWay(PaymentMode.PayOnDelivery))
        val vm = vm(repo)
        vm.bringBack("")
        assertNull(repo.returned)
        vm.bringBack("Wrong size")
        assertEquals("Wrong size", repo.returned)
        assertEquals(CollectDone.Returned, vm.state.value.done)
    }

    @Test
    fun failureKeepsTheScreenAndShowsTheError() {
        val repo = Repo(onTheWay(PaymentMode.Prepaid, kes = null)).apply { result = Result.failure(RpcFailure(RpcError.InvalidStatus)) }
        val vm = vm(repo)
        vm.confirm()
        assertNull(vm.state.value.done)
        assertEquals(RpcError.InvalidStatus, vm.state.value.error)
        assertFalse(vm.state.value.busy)
    }

    @Test
    fun orderNotOnTheWayIsGone() {
        val vm = vm(Repo(null))
        assertTrue(vm.state.value.gone)
    }

    @Test
    fun deliveringStopsTracking() {
        val vm = vm(Repo(onTheWay(PaymentMode.Prepaid, kes = null)))
        vm.confirm()
        assertEquals(1, tracker.stops)
    }

    @Test
    fun bringingItemsBackStopsTracking() {
        val vm = vm(Repo(onTheWay(PaymentMode.PayOnDelivery)))
        vm.bringBack("Wrong size")
        assertEquals(1, tracker.stops)
    }

    @Test
    fun aFailedDeliveryKeepsTracking() {
        val repo = Repo(onTheWay(PaymentMode.Prepaid, kes = null)).apply { result = Result.failure(RpcFailure(RpcError.InvalidStatus)) }
        vm(repo).confirm()
        assertEquals(0, tracker.stops)
    }
}
