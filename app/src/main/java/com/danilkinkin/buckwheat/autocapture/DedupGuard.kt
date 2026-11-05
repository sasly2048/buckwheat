package com.danilkinkin.buckwheat.autocapture

import java.math.RoundingMode

/**
 * Bounded, in-memory duplicate guard. Collapses the same real payment reported
 * by several apps into one entry. Each payment yields up to two keys:
 *  - R:<refId> when the notification carries a bank/UPI reference, matched
 *    within [windowMs] (long) — the authoritative cross-app key;
 *  - H:<paise>|<merchantKey> always, matched within [hashWindowMs] (short) —
 *    a fallback for apps that omit a ref.
 *
 * The short hash window lets cross-app duplicates (which arrive within seconds)
 * collapse, while NOT dropping a genuinely distinct repeat purchase of the same
 * amount+merchant made minutes later that carries its own distinct ref.
 *
 * Backed by an access-order LRU capped at [capacity]. No Android dependencies;
 * [clock] is injectable. Thread-safe.
 */
class DedupGuard(
    private val windowMs: Long = 600_000L,
    private val hashWindowMs: Long = 90_000L,
    private val capacity: Int = 64,
    private val clock: () -> Long,
) {
    private val seen = object : LinkedHashMap<String, Long>(16, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, Long>): Boolean =
            size > capacity
    }

    /** All dedup keys for a payment, ref key (if any) first. */
    fun keys(p: ParsedPayment): List<String> {
        val paise = p.amount.movePointRight(2).setScale(0, RoundingMode.HALF_EVEN).toBigInteger()
        val merchantKey = p.merchant?.lowercase()?.replace(Regex("[^a-z0-9]"), "")?.ifBlank { "?" } ?: "?"
        val hash = "H:$paise|$merchantKey"
        val ref = p.refId
        return if (!ref.isNullOrBlank()) listOf("R:$ref", hash) else listOf(hash)
    }

    private fun windowFor(key: String): Long = if (key.startsWith("R:")) windowMs else hashWindowMs

    @Synchronized
    fun isDuplicate(p: ParsedPayment): Boolean {
        val now = clock()
        return keys(p).any { k -> seen[k]?.let { now - it <= windowFor(k) } == true }
    }

    @Synchronized
    fun remember(p: ParsedPayment) {
        val now = clock()
        for (k in keys(p)) seen[k] = now
    }

    /** Undo a [remember] — used when the capture is abandoned (e.g. no active budget). */
    @Synchronized
    fun forget(p: ParsedPayment) {
        for (k in keys(p)) seen.remove(k)
    }

    @Synchronized
    fun seed(entries: List<Pair<String, Long>>) {
        for ((k, t) in entries) seen[k] = t
    }
}
