package com.danilkinkin.buckwheat.autocapture

import java.math.RoundingMode

/**
 * Bounded, in-memory duplicate guard. Collapses the same real payment reported
 * by several apps into one entry. Each payment yields up to two keys: a
 * reference key (R:<refId>) when the notification carries a bank/UPI ref, and
 * always an amount+merchant key (H:<paise>|<merchantKey>). A payment is a
 * duplicate if ANY of its keys was seen within [windowMs]; remembering a payment
 * records ALL of its keys. This closes the case where one app reports a ref and
 * another reports the same payment without one.
 *
 * Backed by an access-order LRU capped at [capacity] so memory stays tiny.
 * No Android dependencies; [clock] is injectable. Thread-safe.
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

    /** All dedup keys for a payment, most specific first. */
    fun keys(p: ParsedPayment): List<String> {
        val paise = p.amount.movePointRight(2).setScale(0, RoundingMode.HALF_EVEN).toBigInteger()
        val merchantKey = p.merchant?.lowercase()?.replace(Regex("[^a-z0-9]"), "")?.ifBlank { "?" } ?: "?"
        val hash = "H:$paise|$merchantKey"
        val ref = p.refId
        return if (!ref.isNullOrBlank()) listOf("R:$ref", hash) else listOf(hash)
    }

    @Synchronized
    fun isDuplicate(p: ParsedPayment): Boolean {
        val now = clock()
        return keys(p).any { k -> seen[k]?.let { now - it <= windowMs } == true }
    }

    @Synchronized
    fun remember(p: ParsedPayment) {
        val now = clock()
        for (k in keys(p)) seen[k] = now
    }

    @Synchronized
    fun seed(entries: List<Pair<String, Long>>) {
        for ((k, t) in entries) seen[k] = t
    }
}
