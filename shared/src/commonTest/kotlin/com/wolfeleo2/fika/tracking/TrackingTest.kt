package com.wolfeleo2.fika.tracking

import com.wolfeleo2.fika.orders.OrderStatus
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

private fun ping(lat: Double = -0.3, lng: Double = 36.0, at: Long = 0) = Ping(lat, lng, atMillis = at)

/** About 0.0001 degrees of latitude is 11 m, 0.0002 is 22 m. */
private const val ELEVEN_M = 0.0001

private class FakeRepo(var result: Result<ReportResult?> = Result.success(ReportResult("o1", OrderStatus.OnTheWay))) : TrackingRepository {
    val sent = mutableListOf<Ping>()
    var eta: Int? = null
    override suspend fun report(ping: Ping, etaSeconds: Int?, distanceM: Int?): Result<ReportResult?> {
        sent += ping
        eta = etaSeconds
        return result
    }
}

class TrackingTest {
    @Test
    fun distanceIsRoughlyRight() {
        // 0.001 degrees of latitude is about 111 m.
        assertEquals(111.0, distanceMeters(0.0, 36.0, 0.001, 36.0), 1.0)
        assertEquals(0.0, distanceMeters(-0.3, 36.0, -0.3, 36.0), 0.001)
    }

    @Test
    fun firstPingIsAlwaysSent() = assertTrue(shouldSend(null, ping()))

    @Test
    fun smallMoveSoonAfterIsSkipped() = assertFalse(shouldSend(ping(at = 0), ping(lat = -0.3 + ELEVEN_M, at = 10_000)))

    @Test
    fun movedFarEnoughIsSentEvenIfSoon() = assertTrue(shouldSend(ping(at = 0), ping(lat = -0.3 + 2 * ELEVEN_M, at = 5_000)))

    @Test
    fun quietForThirtySecondsIsSentEvenIfStill() = assertTrue(shouldSend(ping(at = 0), ping(at = 30_000)))

    @Test
    fun reporterSkipsThenSends() = runTest {
        val repo = FakeRepo()
        val reporter = PositionReporter(repo)
        assertEquals(ReportOutcome.Sent, reporter.onLocation(ping(at = 0)))
        assertEquals(ReportOutcome.Skipped, reporter.onLocation(ping(at = 10_000)))
        assertEquals(ReportOutcome.Sent, reporter.onLocation(ping(at = 31_000)))
        assertEquals(2, repo.sent.size)
    }

    @Test
    fun failedSendKeepsNoBacklogAndNextSampleIsTried() = runTest {
        val repo = FakeRepo(Result.failure(RuntimeException("offline")))
        val reporter = PositionReporter(repo)
        assertEquals(ReportOutcome.Failed, reporter.onLocation(ping(at = 0)))
        assertEquals(ReportOutcome.Failed, reporter.onLocation(ping(at = 10_000)))
        // Back online: only the newest sample goes out, nothing queued behind it.
        repo.result = Result.success(ReportResult("o1", OrderStatus.OnTheWay))
        repo.sent.clear()
        assertEquals(ReportOutcome.Sent, reporter.onLocation(ping(at = 20_000)))
        assertEquals(1, repo.sent.size)
        assertEquals(20_000L, repo.sent.single().atMillis)
    }

    @Test
    fun stopsWhenOrderIsNoLongerOnTheWay() = runTest {
        listOf(OrderStatus.Delivered, OrderStatus.Returned, OrderStatus.Cancelled).forEach { status ->
            val reporter = PositionReporter(FakeRepo(Result.success(ReportResult("o1", status))))
            assertEquals(ReportOutcome.Stop, reporter.onLocation(ping()), "status=$status")
        }
    }

    @Test
    fun stopsWhenRiderHasNoStartedOrder() = runTest {
        assertEquals(ReportOutcome.Stop, PositionReporter(FakeRepo(Result.success(null))).onLocation(ping()))
    }

    @Test
    fun etaIsForwarded() = runTest {
        val repo = FakeRepo()
        PositionReporter(repo).onLocation(ping(), etaSeconds = 720, distanceM = 3400)
        assertEquals(720, repo.eta)
    }
}
