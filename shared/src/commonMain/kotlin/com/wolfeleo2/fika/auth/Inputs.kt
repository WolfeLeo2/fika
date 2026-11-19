package com.wolfeleo2.fika.auth

/** Uppercases and strips everything but letters and digits; max 6 characters. */
fun normalizeShopCode(input: String): String =
    input.uppercase().filter { it.isLetterOrDigit() }.take(SHOP_CODE_LENGTH)

const val SHOP_CODE_LENGTH = 6
const val PIN_LENGTH = 6

/**
 * Accepts `07…`, `01…`, `+254…`, `254…` or the bare 9-digit number and returns `254XXXXXXXXX`,
 * or null if it isn't a Kenyan mobile number.
 */
fun normalizeKenyanPhone(input: String): String? {
    val digits = input.filter { it.isDigit() }
    val national = when {
        digits.length == 12 && digits.startsWith("254") -> digits.drop(3)
        digits.length == 10 && digits.startsWith("0") -> digits.drop(1)
        digits.length == 9 -> digits
        else -> return null
    }
    return if (national[0] == '7' || national[0] == '1') "254$national" else null
}

/** The synthetic login the web backend creates for a rider, e.g. `254712345678@k7q2mx.riders.invalid`. */
fun riderEmail(phone254: String, shopCode: String): String =
    "$phone254@${shopCode.lowercase()}.riders.invalid"
