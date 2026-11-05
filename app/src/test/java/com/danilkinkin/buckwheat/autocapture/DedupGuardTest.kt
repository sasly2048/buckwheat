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
        val p = pay("10", "Swiggy", "RRN99")
        assertFalse(g.isDuplicate(p)); g.remember(p)
        assertTrue(g.isDuplicate(pay("10", null, "RRN99"))) // other app, no merchant
    }

    @Test fun fallbackHashCollapsesWhenNoRef() {
        val g = guard()
        val p = pay("250.00", "Swiggy", null); g.remember(p)
        assertTrue(g.isDuplicate(pay("250.00", "swiggy", null)))
    }

    @Test fun refThenNoRefStillDuplicate() {
        val g = guard()
        g.remember(pay("10", "Swiggy", "RRN1"))           // one app: has ref
        assertTrue(g.isDuplicate(pay("10", "Swiggy", null))) // another app: no ref, same amount+merchant
    }

    @Test fun noRefThenRefStillDuplicate() {
        val g = guard()
        g.remember(pay("10", "Swiggy", null))             // one app: no ref
        assertTrue(g.isDuplicate(pay("10", "Swiggy", "RRN1"))) // another app: has ref
    }

    @Test fun expiresAfterWindow() {
        val g = guard()
        val p = pay("10", "A", null); g.remember(p)
        now += 600_001L
        assertFalse(g.isDuplicate(p))
    }

    @Test fun evictsBeyondCapacity() {
        val g = guard()
        repeat(70) { g.remember(pay("$it", "m$it", null)) }
        assertFalse(g.isDuplicate(pay("0", "m0", null))) // oldest evicted
    }

    @Test fun seedRestoresAfterRestart() {
        val g = guard()
        val p = pay("10", "A", "R1")
        g.seed(g.keys(p).map { it to now })
        assertTrue(g.isDuplicate(p))
    }
}
