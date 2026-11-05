package com.danilkinkin.buckwheat.autocapture

import java.math.RoundingMode

/**
 * Bounded, in-memory duplicate guard. Collapses the same real payment reported
 * by several apps into one entry: by bank/UPI reference id when present, else by
 * amount + normalized merchant, within [windowMs]. Backed by an access-order LRU
 * capped at [capacity] so memory stays tiny. No Android dependencies; [clock] is
 * injectable for testing. Thread-safe (service callbacks may overlap).
 */
class DedupGuard(
    private val windowMs: Long = 600_000L,
    private val capacity: Int = 64,
    private val clock: () -> Long,
) {
    private val seen = object : LinkedHashMap<String, Long>(16, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, Long>): Boolean =
            size > capacity
    }

    fun fingerprint(p: ParsedPayment): String {
        val ref = p.refId
        if (!ref.isNullOrBlank()) return "R:$ref"
        val paise = p.amount.movePointRight(2).setScale(0, RoundingMode.HALF_EVEN).toBigInteger()
        val merchantKey = p.merchant?.lowercase()?.replace(Regex("[^a-z0-9]"), "")?.ifBlank { "?" } ?: "?"
        return "H:$paise|$merchantKey"
    }

    @Synchronized
    fun isDuplicate(fp: String): Boolean {
        val last = seen[fp] ?: return false
        return clock() - last <= windowMs
    }

    @Synchronized
    fun remember(fp: String) {
        seen[fp] = clock()
    }

    @Synchronized
    fun seed(entries: List<Pair<String, Long>>) {
        for ((fp, t) in entries) seen[fp] = t
    }
}
