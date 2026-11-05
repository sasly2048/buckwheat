package com.danilkinkin.buckwheat.autocapture

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.math.BigDecimal

class DedupGuardTest {
    private var now = 0L
    private fun guard() = DedupGuard(windowMs = 600_000L, capacity = 64) { now }
    private fun pay(amount: String, merchant: String?, ref: String?) =
        ParsedPayment(BigDecimal(amount), PaymentDirection.DEBIT, merchant, ref, now)

    @Test fun sameRefIdIsDuplicate() {
        val g = guard()
        val fp = g.fingerprint(pay("10", "Swiggy", "RRN99"))
        assertFalse(g.isDuplicate(fp)); g.remember(fp)
        assertTrue(g.isDuplicate(g.fingerprint(pay("10", null, "RRN99")))) // other app, no merchant
    }

    @Test fun fallbackHashCollapsesWhenNoRef() {
        val g = guard()
        val fp = g.fingerprint(pay("250.00", "Swiggy", null)); g.remember(fp)
        assertTrue(g.isDuplicate(g.fingerprint(pay("250.00", "swiggy", null))))
    }

    @Test fun expiresAfterWindow() {
        val g = guard()
        val fp = g.fingerprint(pay("10", "A", null)); g.remember(fp)
        now += 600_001L
        assertFalse(g.isDuplicate(fp))
    }

    @Test fun evictsBeyondCapacity() {
        val g = guard()
        repeat(70) { g.remember(g.fingerprint(pay("$it", "m$it", null))) }
        assertFalse(g.isDuplicate(g.fingerprint(pay("0", "m0", null)))) // oldest evicted
    }

    @Test fun seedRestoresAfterRestart() {
        val g = guard()
        val fp = g.fingerprint(pay("10", "A", "R1"))
        g.seed(listOf(fp to now)); assertTrue(g.isDuplicate(fp))
    }
}
