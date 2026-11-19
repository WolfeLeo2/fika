package com.wolfeleo2.fika.auth

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

private class FakeAuth(
    var lookup: ShopLookup = ShopLookup.Found(Shop("Amani", null, ShopStatus.Active)),
    var signInResult: SignInResult = SignInResult.Success,
) : AuthRepository {
    override val sessionState: Flow<SessionState> = flowOf(SessionState.SignedOut)
    val lookups = mutableListOf<String>()
    var lastSignIn: Triple<String, String, String>? = null
    override suspend fun lookupShop(shopCode: String): ShopLookup = lookups.add(shopCode).let { lookup }
    override suspend fun signIn(shopCode: String, phone254: String, pin: String): SignInResult {
        lastSignIn = Triple(shopCode, phone254, pin)
        return signInResult
    }
    override suspend fun signOut() {}
}

private class FakeStore(override var code: String? = null) : ShopCodeStore

@OptIn(ExperimentalCoroutinesApi::class)
class SignInViewModelTest {
    @BeforeTest fun setUp() = Dispatchers.setMain(UnconfinedTestDispatcher())
    @AfterTest fun tearDown() = Dispatchers.resetMain()

    private fun vm(auth: FakeAuth = FakeAuth(), store: FakeStore = FakeStore()) = SignInViewModel(auth, store)

    @Test
    fun lookupRunsOnlyWhenSixCharactersTyped() {
        val auth = FakeAuth()
        val vm = vm(auth)
        vm.onShopCodeChange("k7q2m")
        assertTrue(auth.lookups.isEmpty())
        vm.onShopCodeChange("k7q2mx")
        assertEquals(listOf("K7Q2MX"), auth.lookups)
        assertIs<ShopState.Found>(vm.state.value.shop)
    }

    @Test
    fun unknownShopCode() {
        val vm = vm(FakeAuth(lookup = ShopLookup.NotFound))
        vm.onShopCodeChange("K7Q2MX")
        assertEquals(ShopState.NotFound, vm.state.value.shop)
        assertFalse(vm.state.value.canSubmit)
    }

    @Test
    fun suspendedShopBlocksSignIn() {
        val vm = vm(FakeAuth(lookup = ShopLookup.Found(Shop("Amani", null, ShopStatus.Suspended))))
        vm.onShopCodeChange("K7Q2MX")
        vm.onPhoneChange("0712345678")
        vm.onPinChange("123456")
        assertTrue(vm.state.value.shopSuspended)
        assertFalse(vm.state.value.canSubmit)
    }

    @Test
    fun rememberedShopIsLookedUpAndCanBeChanged() {
        val store = FakeStore("K7Q2MX")
        val auth = FakeAuth()
        val vm = vm(auth, store)
        assertEquals(listOf("K7Q2MX"), auth.lookups)
        assertTrue(vm.state.value.rememberedShop)
        vm.onChangeShop()
        assertNull(store.code)
        assertEquals("", vm.state.value.shopCode)
    }

    @Test
    fun successfulSignInNormalisesPhoneAndRemembersShop() {
        val auth = FakeAuth()
        val store = FakeStore()
        val vm = vm(auth, store)
        vm.onShopCodeChange("K7Q2MX")
        vm.onPhoneChange("0712 345 678")
        vm.onPinChange("123456")
        vm.onSubmit()
        assertEquals(Triple("K7Q2MX", "254712345678", "123456"), auth.lastSignIn)
        assertEquals("K7Q2MX", store.code)
        assertNull(vm.state.value.error)
    }

    @Test
    fun eachAuthFailureHasItsOwnError() {
        mapOf(
            SignInResult.InvalidCredentials to SignInError.InvalidCredentials,
            SignInResult.UserBanned to SignInError.UserBanned,
            SignInResult.NetworkError to SignInError.NoConnection,
            SignInResult.Unknown to SignInError.Unknown,
        ).forEach { (result, error) ->
            val store = FakeStore()
            val vm = vm(FakeAuth(signInResult = result), store)
            vm.onShopCodeChange("K7Q2MX")
            vm.onPhoneChange("0712345678")
            vm.onPinChange("123456")
            vm.onSubmit()
            assertEquals(error, vm.state.value.error)
            assertNull(store.code, "failed sign-in must not remember the shop")
        }
    }

    @Test
    fun badPhoneNeverCallsAuth() {
        val auth = FakeAuth()
        val vm = vm(auth)
        vm.onShopCodeChange("K7Q2MX")
        vm.onPhoneChange("12345")
        vm.onPinChange("123456")
        vm.onSubmit()
        assertEquals(SignInError.InvalidPhone, vm.state.value.error)
        assertNull(auth.lastSignIn)
    }
}
