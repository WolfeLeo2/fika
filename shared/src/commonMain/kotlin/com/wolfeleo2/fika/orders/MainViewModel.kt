package com.wolfeleo2.fika.orders

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.wolfeleo2.fika.auth.AuthRepository
import com.wolfeleo2.fika.tracking.LocationTracker
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/** Signed-in shell state: who the rider is (initials, name) and which shop they work for. */
class MainViewModel(
    profiles: ProfileRepository,
    shops: ShopRepository,
    private val auth: AuthRepository,
    private val tracker: LocationTracker,
) : ViewModel() {
    private val _name = MutableStateFlow<String?>(null)
    val riderName: StateFlow<String?> = _name.asStateFlow()
    private val _shop = MutableStateFlow<ShopInfo?>(null)
    val shop: StateFlow<ShopInfo?> = _shop.asStateFlow()

    init {
        viewModelScope.launch { _name.value = profiles.riderName()?.trim()?.takeIf { it.isNotEmpty() } }
        viewModelScope.launch { _shop.value = shops.myShop().getOrNull() }
    }

    /** Stops tracking first: a signed-out rider must never keep reporting a position. */
    fun signOut() {
        viewModelScope.launch {
            tracker.stop()
            auth.signOut()
        }
    }
}

/** `Leo` for `Leo Wolfe`. */
fun firstName(fullName: String?): String? = fullName?.trim()?.substringBefore(' ')?.takeIf { it.isNotEmpty() }
