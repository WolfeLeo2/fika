package com.wolfeleo2.fika

import android.app.Application
import com.wolfeleo2.fika.di.SupabaseConfig
import com.wolfeleo2.fika.di.sharedModule
import org.koin.android.ext.koin.androidContext
import com.wolfeleo2.fika.auth.ShopCodeStore
import com.wolfeleo2.fika.tracking.LocationTracker
import org.koin.core.context.startKoin
import org.koin.dsl.module

class FikaApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        startKoin {
            androidContext(this@FikaApplication)
            modules(
                sharedModule(SupabaseConfig(BuildConfig.SUPABASE_URL, BuildConfig.SUPABASE_ANON_KEY)),
                module {
                    single<ShopCodeStore> { PrefsShopCodeStore(get()) }
                    single<LocationTracker> { AndroidLocationTracker(androidContext()) }
                },
            )
        }
    }
}
