package com.danilkinkin.buckwheat.autocapture

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.math.BigDecimal

class DedupGuardTest {
    private var now = 0L
    private fun guard() = DedupGuard(windowMs = 600_000L, hashWindowMs = 90_000L, capacity = 64) { now }
    private fun pay(amount: String, merchant: String?, ref: String?) =
        ParsedPayment(BigDecimal(amount), PaymentDirection.DEBIT, merchant, ref, now)

    @Test fun sameRefIdIsDuplicate() {
        val g = guard()
        val p = pay("10", "Swiggy", "RRN99")
        assertFalse(g.isDuplicate(p)); g.remember(p)
        assertTrue(g.isDuplicate(pay("10", null, "RRN99")))
    }

    @Test fun fallbackHashCollapsesWhenNoRef() {
        val g = guard()
        g.remember(pay("250.00", "Swiggy", null))
        assertTrue(g.isDuplicate(pay("250.00", "swiggy", null)))
    }

    @Test fun refThenNoRefStillDuplicateWithinShortWindow() {
        val g = guard()
        g.remember(pay("10", "Swiggy", "RRN1"))
        assertTrue(g.isDuplicate(pay("10", "Swiggy", null)))
    }

    @Test fun noRefThenRefStillDuplicateWithinShortWindow() {
        val g = guard()
        g.remember(pay("10", "Swiggy", null))
        assertTrue(g.isDuplicate(pay("10", "Swiggy", "RRN1")))
    }

    // C6: two genuinely distinct payments that each carry a distinct ref must NOT collapse
    @Test fun distinctRefsSameAmountNotDuplicateAfterHashWindow() {
        val g = guard()
        g.remember(pay("250", "Swiggy", "RRN1"))
        now += 120_000L // 2 min: past the short hash window, within the ref window
        assertFalse(g.isDuplicate(pay("250", "Swiggy", "RRN2")))
    }

    @Test fun sameRefStillDuplicateAfterHashWindow() {
        val g = guard()
        g.remember(pay("250", "Swiggy", "RRN1"))
        now += 300_000L // 5 min: past hash window, within ref window
        assertTrue(g.isDuplicate(pay("250", null, "RRN1")))
    }

    @Test fun hashExpiresAfterShortWindow() {
        val g = guard()
        g.remember(pay("10", "A", null))
        now += 90_001L
        assertFalse(g.isDuplicate(pay("10", "A", null)))
    }

    @Test fun evictsBeyondCapacity() {
        val g = guard()
        repeat(70) { g.remember(pay("$it", "m$it", null)) }
        assertFalse(g.isDuplicate(pay("0", "m0", null)))
    }

    @Test fun forgetRemovesKeys() {
        val g = guard()
        val p = pay("10", "A", "R1")
        g.remember(p); assertTrue(g.isDuplicate(p))
        g.forget(p); assertFalse(g.isDuplicate(p))
    }

    @Test fun seedRestoresAfterRestart() {
        val g = guard()
        val p = pay("10", "A", "R1")
        g.seed(g.keys(p).map { it to now })
        assertTrue(g.isDuplicate(p))
    }
}
