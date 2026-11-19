package com.wolfeleo2.fika.auth

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class InputsTest {
    @Test
    fun phoneAcceptsEveryFormat() {
        listOf("0712345678", "0112345678", "+254712345678", "254712345678", "712345678", "0712 345 678", "+254 712-345-678")
            .forEach { assertEquals("254${it.filter(Char::isDigit).takeLast(9)}", normalizeKenyanPhone(it), it) }
    }

    @Test
    fun phoneRejectsBadNumbers() {
        listOf("", "071234567", "0912345678", "25471234567", "abc", "+255712345678").forEach {
            assertNull(normalizeKenyanPhone(it), it)
        }
    }

    @Test
    fun shopCodeIsUppercasedAndTrimmed() {
        assertEquals("K7Q2MX", normalizeShopCode(" k7-q2mx9 "))
    }

    @Test
    fun riderEmailLowercasesShopCode() {
        assertEquals("254712345678@k7q2mx.riders.invalid", riderEmail("254712345678", "K7Q2MX"))
    }
}
