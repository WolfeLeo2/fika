package com.wolfeleo2.fika

import android.content.Context
import com.wolfeleo2.fika.auth.ShopCodeStore

class PrefsShopCodeStore(context: Context) : ShopCodeStore {
    private val prefs = context.getSharedPreferences("fika", Context.MODE_PRIVATE)
    override var code: String?
        get() = prefs.getString("shop_code", null)
        set(value) = prefs.edit().putString("shop_code", value).apply()
}
