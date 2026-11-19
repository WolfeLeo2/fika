package com.wolfeleo2.fika.orders

import kotlin.time.Instant
import kotlinx.datetime.TimeZone
import kotlinx.datetime.atStartOfDayIn
import kotlinx.datetime.toLocalDateTime

val NairobiTime: TimeZone = TimeZone.of("Africa/Nairobi")

/** `KES 1,500` */
fun formatKes(amount: Int): String =
    "KES " + amount.toString().reversed().chunked(3).joinToString(",").reversed()

/** Whole minutes, at least 1. */
fun etaMinutes(seconds: Int): Int = ((seconds + 30) / 60).coerceAtLeast(1)

/** `850 m` or `3.4 km` */
fun formatDistance(meters: Int): String =
    if (meters < 1000) "$meters m" else "${meters / 1000}.${meters % 1000 / 100} km"

/** `Wanjiru K.` for a full name, `Brian` for a single name. */
fun displayName(full: String?): String {
    val parts = full.orEmpty().trim().split(Regex("\\s+")).filter { it.isNotEmpty() }
    return when {
        parts.isEmpty() -> "Customer"
        parts.size == 1 -> parts[0]
        else -> "${parts[0]} ${parts.last().first().uppercase()}."
    }
}

/** `OK` for `Oliver Kamau`. */
fun initials(name: String?): String? {
    val parts = name.orEmpty().trim().split(Regex("\\s+")).filter { it.isNotEmpty() }
    if (parts.isEmpty()) return null
    return (parts.first().first().toString() + (parts.drop(1).firstOrNull()?.first() ?: "")).uppercase()
}

enum class Greeting { Morning, Afternoon, Evening }

/** Time of day in Africa/Nairobi: before 12 is morning, before 17 afternoon, otherwise evening. */
fun greetingFor(now: Instant): Greeting = when (now.toLocalDateTime(NairobiTime).time.hour) {
    in 0..11 -> Greeting.Morning
    in 12..16 -> Greeting.Afternoon
    else -> Greeting.Evening
}

fun startOfTodayNairobi(now: Instant): Instant =
    now.toLocalDateTime(NairobiTime).date.atStartOfDayIn(NairobiTime)

/** `3:41 pm` in Africa/Nairobi. */
fun timeLabel(at: Instant): String {
    val t = at.toLocalDateTime(NairobiTime).time
    val h = if (t.hour % 12 == 0) 12 else t.hour % 12
    return "$h:${t.minute.toString().padStart(2, '0')} ${if (t.hour < 12) "am" else "pm"}"
}
