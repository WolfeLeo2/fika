package com.wolfeleo2.fika.data

import com.wolfeleo2.fika.auth.AuthRepository
import com.wolfeleo2.fika.auth.SessionState
import com.wolfeleo2.fika.auth.Shop
import com.wolfeleo2.fika.auth.ShopLookup
import com.wolfeleo2.fika.auth.ShopStatus
import com.wolfeleo2.fika.auth.SignInResult
import com.wolfeleo2.fika.auth.riderEmail
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.auth.exception.AuthRestException
import io.github.jan.supabase.auth.providers.builtin.Email
import io.github.jan.supabase.auth.status.SessionStatus
import io.github.jan.supabase.exceptions.HttpRequestException
import io.github.jan.supabase.exceptions.RestException
import io.github.jan.supabase.postgrest.exception.PostgrestRestException
import io.github.jan.supabase.postgrest.postgrest
import io.github.jan.supabase.storage.storage
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

@Serializable
private class ShopDto(
    val name: String,
    @SerialName("logo_path") val logoPath: String? = null,
    val status: String,
    val phone: String? = null,
)

class SupabaseAuthRepository(private val supabase: SupabaseClient) : AuthRepository {

    override val sessionState: Flow<SessionState> = supabase.auth.sessionStatus.map {
        when (it) {
            is SessionStatus.Initializing -> SessionState.Loading
            is SessionStatus.Authenticated -> SessionState.SignedIn
            else -> SessionState.SignedOut
        }
    }

    override suspend fun signOut() {
        try {
            supabase.auth.signOut()
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            // The local session is cleared first, so a failed server call still signs the rider out here.
        }
    }

    override suspend fun lookupShop(shopCode: String): ShopLookup = try {
        val dto = supabase.postgrest
            .rpc("rider_shop_lookup", buildJsonObject { put("p_shop_code", shopCode) })
            .decodeAsOrNull<ShopDto>()
        if (dto == null) ShopLookup.NotFound else ShopLookup.Found(
            Shop(
                name = dto.name,
                logoUrl = dto.logoPath?.let { supabase.storage.from("logos").publicUrl(it) },
                status = when (dto.status) {
                    "suspended" -> ShopStatus.Suspended
                    "pending" -> ShopStatus.Pending
                    else -> ShopStatus.Active
                },
                phone = dto.phone,
            ),
        )
    } catch (e: CancellationException) {
        throw e
    } catch (e: PostgrestRestException) {
        if (e.message.orEmpty().contains("rate_limited") || e.hint.orEmpty().contains("rate_limited")) {
            ShopLookup.RateLimited
        } else ShopLookup.NetworkError
    } catch (e: Exception) {
        ShopLookup.NetworkError
    }

    override suspend fun signIn(shopCode: String, phone254: String, pin: String): SignInResult = try {
        supabase.auth.signInWith(Email) {
            email = riderEmail(phone254, shopCode)
            password = pin
        }
        SignInResult.Success
    } catch (e: CancellationException) {
        throw e
    } catch (e: AuthRestException) {
        when (e.error) {
            "invalid_credentials" -> SignInResult.InvalidCredentials
            "user_banned" -> SignInResult.UserBanned
            else -> SignInResult.Unknown
        }
    } catch (e: HttpRequestException) {
        SignInResult.NetworkError
    } catch (e: RestException) {
        SignInResult.Unknown
    } catch (e: Exception) {
        SignInResult.NetworkError
    }
}
