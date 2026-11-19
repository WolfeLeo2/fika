package com.wolfeleo2.fika.auth

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

sealed interface ShopState {
    data object Idle : ShopState
    data object Checking : ShopState
    class Found(val shop: Shop) : ShopState
    data object NotFound : ShopState
    data object RateLimited : ShopState
    data object NetworkError : ShopState
}

enum class SignInError { InvalidPhone, InvalidCredentials, UserBanned, NoConnection, Unknown }

data class SignInUiState(
    val shopCode: String = "",
    val shop: ShopState = ShopState.Idle,
    /** True while the field holds the code remembered from a previous sign-in (shows "Change shop"). */
    val rememberedShop: Boolean = false,
    val phone: String = "",
    val pin: String = "",
    val submitting: Boolean = false,
    val error: SignInError? = null,
) {
    val shopSuspended get() = (shop as? ShopState.Found)?.shop?.status == ShopStatus.Suspended
    val canSubmit
        get() = shop is ShopState.Found && !shopSuspended &&
            phone.isNotBlank() && pin.length == PIN_LENGTH && !submitting
}

class SignInViewModel(
    private val auth: AuthRepository,
    private val store: ShopCodeStore,
) : ViewModel() {
    private val remembered = store.code
    private val _state = MutableStateFlow(
        SignInUiState(shopCode = remembered.orEmpty(), rememberedShop = remembered != null),
    )
    val state: StateFlow<SignInUiState> = _state.asStateFlow()
    private var lookupJob: Job? = null

    init {
        if (remembered?.length == SHOP_CODE_LENGTH) lookup(remembered)
    }

    fun onShopCodeChange(raw: String) {
        val code = normalizeShopCode(raw)
        if (code == _state.value.shopCode) return
        _state.update { it.copy(shopCode = code, shop = ShopState.Idle, rememberedShop = false, error = null) }
        lookupJob?.cancel()
        if (code.length == SHOP_CODE_LENGTH) lookup(code)
    }

    fun onChangeShop() {
        store.code = null
        lookupJob?.cancel()
        _state.update { it.copy(shopCode = "", shop = ShopState.Idle, rememberedShop = false, error = null) }
    }

    fun onPhoneChange(raw: String) =
        _state.update { it.copy(phone = raw.filter { c -> c.isDigit() || c == '+' }.take(13), error = null) }

    fun onPinChange(raw: String) =
        _state.update { it.copy(pin = raw.filter(Char::isDigit).take(PIN_LENGTH), error = null) }

    fun onSubmit() {
        val s = _state.value
        if (!s.canSubmit) return
        val phone = normalizeKenyanPhone(s.phone)
            ?: return _state.update { it.copy(error = SignInError.InvalidPhone) }
        _state.update { it.copy(submitting = true, error = null) }
        viewModelScope.launch {
            val result = auth.signIn(s.shopCode, phone, s.pin)
            if (result == SignInResult.Success) store.code = s.shopCode
            _state.update {
                it.copy(
                    submitting = false,
                    error = when (result) {
                        SignInResult.Success -> null
                        SignInResult.InvalidCredentials -> SignInError.InvalidCredentials
                        SignInResult.UserBanned -> SignInError.UserBanned
                        SignInResult.NetworkError -> SignInError.NoConnection
                        SignInResult.Unknown -> SignInError.Unknown
                    },
                )
            }
        }
    }

    private fun lookup(code: String) {
        lookupJob?.cancel()
        _state.update { it.copy(shop = ShopState.Checking) }
        lookupJob = viewModelScope.launch {
            val shop = when (val r = auth.lookupShop(code)) {
                is ShopLookup.Found -> ShopState.Found(r.shop)
                ShopLookup.NotFound -> ShopState.NotFound
                ShopLookup.RateLimited -> ShopState.RateLimited
                ShopLookup.NetworkError -> ShopState.NetworkError
            }
            _state.update { it.copy(shop = shop) }
        }
    }
}
