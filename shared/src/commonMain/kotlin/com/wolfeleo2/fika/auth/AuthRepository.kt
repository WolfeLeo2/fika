package com.wolfeleo2.fika.auth

import kotlinx.coroutines.flow.Flow

enum class ShopStatus { Pending, Active, Suspended }

class Shop(val name: String, val logoUrl: String?, val status: ShopStatus, val phone: String? = null)

sealed interface ShopLookup {
    class Found(val shop: Shop) : ShopLookup
    data object NotFound : ShopLookup
    data object RateLimited : ShopLookup
    data object NetworkError : ShopLookup
}

sealed interface SignInResult {
    data object Success : SignInResult
    data object InvalidCredentials : SignInResult
    data object UserBanned : SignInResult
    data object NetworkError : SignInResult
    data object Unknown : SignInResult
}

enum class SessionState { Loading, SignedIn, SignedOut }

/** Repositories return result types and never throw into the UI. */
interface AuthRepository {
    val sessionState: Flow<SessionState>
    suspend fun lookupShop(shopCode: String): ShopLookup
    suspend fun signIn(shopCode: String, phone254: String, pin: String): SignInResult

    /** Ends the session on this device even when the server can't be reached. */
    suspend fun signOut()
}

/** Remembers the shop code on the device between launches. */
interface ShopCodeStore {
    var code: String?
}
