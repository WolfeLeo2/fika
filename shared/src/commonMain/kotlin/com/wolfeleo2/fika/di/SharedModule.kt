package com.wolfeleo2.fika.di

import io.github.jan.supabase.auth.Auth
import io.github.jan.supabase.createSupabaseClient
import io.github.jan.supabase.postgrest.Postgrest
import io.github.jan.supabase.realtime.Realtime
import io.github.jan.supabase.storage.Storage
import com.wolfeleo2.fika.auth.AuthRepository
import com.wolfeleo2.fika.auth.SignInViewModel
import com.wolfeleo2.fika.data.SupabaseAuthRepository
import com.wolfeleo2.fika.data.SupabaseOrdersRepository
import com.wolfeleo2.fika.data.SupabaseShopRepository
import com.wolfeleo2.fika.data.SupabaseTrackingRepository
import com.wolfeleo2.fika.orders.CollectViewModel
import com.wolfeleo2.fika.orders.DeliveredViewModel
import com.wolfeleo2.fika.orders.HistoryViewModel
import com.wolfeleo2.fika.orders.MainViewModel
import com.wolfeleo2.fika.orders.OrderDetailViewModel
import com.wolfeleo2.fika.orders.OrdersRepository
import com.wolfeleo2.fika.orders.ProfileRepository
import com.wolfeleo2.fika.orders.ShopRepository
import com.wolfeleo2.fika.orders.TodayViewModel
import com.wolfeleo2.fika.settings.ThemeSettings
import com.wolfeleo2.fika.tracking.NavigationProgress
import com.wolfeleo2.fika.tracking.TrackingRepository
import kotlin.time.Clock
import org.koin.core.module.dsl.viewModel
import org.koin.core.module.dsl.viewModelOf
import org.koin.dsl.module

class SupabaseConfig(val url: String, val anonKey: String)

fun sharedModule(config: SupabaseConfig) = module {
    single {
        createSupabaseClient(config.url, config.anonKey) {
            install(Auth)
            install(Postgrest)
            install(Realtime)
            install(Storage)
        }
    }
    single<AuthRepository> { SupabaseAuthRepository(get()) }
    viewModelOf(::SignInViewModel)
    single<Clock> { Clock.System }
    single { SupabaseOrdersRepository(get()) }
    single<OrdersRepository> { get<SupabaseOrdersRepository>() }
    single<ProfileRepository> { get<SupabaseOrdersRepository>() }
    viewModelOf(::TodayViewModel)
    viewModelOf(::HistoryViewModel)
    viewModelOf(::MainViewModel)
    single<TrackingRepository> { SupabaseTrackingRepository(get()) }
    single { NavigationProgress() }
    single { ThemeSettings(get()) }
    viewModel { (orderId: String) -> OrderDetailViewModel(orderId, get(), get()) }
    single<ShopRepository> { SupabaseShopRepository(get()) }
    viewModelOf(::DeliveredViewModel)
    viewModel { (orderId: String) -> CollectViewModel(orderId, get(), get(), get(), get()) }
}
