package com.wolfeleo2.fika.orders

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.time.Instant

class FormatTest {
    @Test fun kes() {
        assertEquals("KES 0", formatKes(0))
        assertEquals("KES 950", formatKes(950))
        assertEquals("KES 1,500", formatKes(1500))
        assertEquals("KES 1,234,567", formatKes(1234567))
    }

    @Test fun eta() {
        assertEquals(1, etaMinutes(5))
        assertEquals(12, etaMinutes(720))
        assertEquals(13, etaMinutes(750))
    }

    @Test fun distance() {
        assertEquals("850 m", formatDistance(850))
        assertEquals("3.4 km", formatDistance(3450))
        assertEquals("1.0 km", formatDistance(1000))
    }

    @Test fun names() {
        assertEquals("Wanjiru N.", displayName("Wanjiru Kamau Njeri"))
        assertEquals("Wanjiru K.", displayName("Wanjiru Kamau"))
        assertEquals("Brian", displayName("Brian"))
        assertEquals("Customer", displayName(null))
        assertEquals("OK", initials("Oliver Kamau"))
        assertEquals("B", initials("Brian"))
        assertNull(initials(" "))
        assertEquals("Oliver", firstName(" Oliver Kamau"))
        assertNull(firstName(null))
    }

    @Test fun dayIsNairobiNotUtc() {
        // 22:30 UTC on 11 Nov is already 01:30 on 12 Nov in Nairobi (UTC+3).
        val now = Instant.parse("2026-11-11T22:30:00Z")
        assertEquals(Instant.parse("2026-11-11T21:00:00Z"), startOfTodayNairobi(now))
        assertEquals(Greeting.Morning, greetingFor(now))
    }

    @Test fun greetingByNairobiHour() {
        assertEquals(Greeting.Morning, greetingFor(Instant.parse("2026-11-12T08:59:00Z"))) // 11:59
        assertEquals(Greeting.Afternoon, greetingFor(Instant.parse("2026-11-12T09:00:00Z"))) // 12:00
        assertEquals(Greeting.Afternoon, greetingFor(Instant.parse("2026-11-12T13:59:00Z"))) // 16:59
        assertEquals(Greeting.Evening, greetingFor(Instant.parse("2026-11-12T14:00:00Z"))) // 17:00
    }
}
