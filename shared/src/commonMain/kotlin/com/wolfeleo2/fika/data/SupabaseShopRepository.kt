package com.wolfeleo2.fika.data

import com.wolfeleo2.fika.orders.ShopInfo
import com.wolfeleo2.fika.orders.ShopRepository
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.postgrest.from
import io.github.jan.supabase.postgrest.query.Columns
import kotlinx.coroutines.CancellationException
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
private class TenantDto(
    val name: String,
    val phone: String? = null,
    @SerialName("till_number") val tillNumber: String? = null,
    @SerialName("paybill_number") val paybillNumber: String? = null,
    @SerialName("paybill_account") val paybillAccount: String? = null,
)

class SupabaseShopRepository(private val supabase: SupabaseClient) : ShopRepository {
    /** RLS only lets a rider read their own shop's row, so no filter is needed. */
    override suspend fun myShop(): Result<ShopInfo> = try {
        val t = supabase.from("tenants")
            .select(Columns.list("name", "phone", "till_number", "paybill_number", "paybill_account"))
            .decodeList<TenantDto>().first()
        Result.success(ShopInfo(t.name, t.phone, t.tillNumber, t.paybillNumber, t.paybillAccount))
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        Result.failure(e)
    }
}
