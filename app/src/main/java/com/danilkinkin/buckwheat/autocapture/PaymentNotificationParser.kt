package com.danilkinkin.buckwheat.autocapture

import java.math.BigDecimal

/**
 * Pure (no-Android) parser that turns a payment notification's text into a
 * [ParsedPayment], or null when it is not a confident debit. Debits-only:
 * credits, ambiguous, and non-payment notifications return null.
 *
 * All regex are compiled once (object init) to keep the notification hot path
 * cheap. The caller is responsible for the package allowlist and keyword
 * pre-filter; this parser is the precise second stage.
 */
object PaymentNotificationParser {
    private const val MAX_SCAN = 512

    private val AMOUNT = Regex(
        "(?:₹|\\bRs\\.?\\b|\\bINR\\b)\\s*([0-9][0-9,]*(?:\\.[0-9]{1,2})?)",
        RegexOption.IGNORE_CASE,
    )
    private val REF_ID = Regex(
        "(?:UPI\\s*Ref(?:\\s*No\\.?)?|Ref(?:erence)?\\s*no\\.?|RRN|UTR|Txn(?:\\s*ID)?)[:\\s]*([A-Za-z0-9]{4,})",
        RegexOption.IGNORE_CASE,
    )
    private val MERCHANT = Regex(
        "\\b(?:to|at|from)\\s+([A-Z0-9][\\w .&'-]{1,40})",
    )
    private val VPA = Regex("([\\w.-]+@[\\w.-]+)")

    private val DEBIT_WORDS = listOf("debited", "debit", "spent", "paid", "sent", "deducted", "withdrawn", "purchase")
    private val CREDIT_WORDS = listOf("credited", "credit", "received", "refund", "added", "cashback")

    fun parse(
        packageName: String,
        title: String?,
        text: String?,
        bigText: String?,
        postTime: Long,
    ): ParsedPayment? {
        // Combine all text fields: amount and ref id can live in different ones
        // (e.g. amount in text, UPI ref in bigText). Dedup equal fields.
        var haystack = listOfNotNull(title, text, bigText)
            .filter { it.isNotBlank() }
            .distinct()
            .joinToString("\n")
            .trim()
        if (haystack.length > MAX_SCAN) haystack = haystack.substring(0, MAX_SCAN)
        val lc = haystack.lowercase()

        val amountMatch = AMOUNT.find(haystack) ?: return null
        val amount = try {
            BigDecimal(amountMatch.groupValues[1].replace(",", ""))
        } catch (e: NumberFormatException) {
            return null
        }

        val hasDebit = DEBIT_WORDS.any { lc.contains(it) }
        val hasCredit = CREDIT_WORDS.any { lc.contains(it) }
        if (!(hasDebit && !hasCredit)) return null // debits-only; drop credit/ambiguous/neither

        val refId = REF_ID.find(haystack)?.groupValues?.get(1)?.uppercase()
        val merchant = (MERCHANT.find(haystack)?.groupValues?.get(1)
            ?: VPA.find(haystack)?.groupValues?.get(1))
            ?.trim()?.replace(Regex("\\s+"), " ")

        return ParsedPayment(amount, PaymentDirection.DEBIT, merchant, refId, postTime)
    }
}
