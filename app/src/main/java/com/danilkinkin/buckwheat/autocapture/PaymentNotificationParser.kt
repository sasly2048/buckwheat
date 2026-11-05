package com.danilkinkin.buckwheat.autocapture

import java.math.BigDecimal

/**
 * Pure (no-Android) parser that turns a payment notification's text into a
 * [ParsedPayment], or null when it is not a confident outgoing debit.
 * Debits-only: credits, requests/promos, ambiguous and non-payment
 * notifications return null. All regex are compiled once.
 *
 * Confidence rules (deliberately conservative — a mis-parse silently corrupts
 * the budget):
 *  - amount is the currency figure that is NOT a balance/limit/due figure;
 *  - direction requires a whole-word debit confirmation and no credit word,
 *    with "credit card"/"debit card" treated as non-signals;
 *  - "sent" counts as a debit only as "sent ... to", never "sent you ...";
 *  - request/promo notifications are rejected outright.
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
    private val MERCHANT = Regex("\\b(?:to|at|from)\\s+([A-Za-z0-9][\\w .&'-]{1,40})")
    private val VPA = Regex("([\\w.-]+@[\\w.-]+)")

    private val DEBIT = Regex("\\b(debited|debit|spent|paid|withdrawn|deducted|purchased)\\b")
    private val CREDIT = Regex("\\b(credited|credit|received|refund|refunded|cashback|added)\\b")
    private val SENT = Regex("\\bsent\\b")
    private val SENT_YOU = Regex("\\bsent\\s+you\\b")
    private val TO = Regex("\\bto\\b")
    // Not-a-debit markers: collect requests, reminders, promos/offers.
    private val NON_DEBIT = Regex(
        "\\b(request|requested|requesting|reminder|offer|discount|will\\s+be\\s+debited)\\b" +
                "|%\\s*off|\\bnow\\s+at\\b|\\b(?:buy|shop|order)\\s+now\\b",
        RegexOption.IGNORE_CASE,
    )
    private val BALANCE_HINTS = listOf("bal", "avl", "avbl", "avlbl", "available", "limit", "due", "outstanding")

    fun parse(
        packageName: String,
        title: String?,
        text: String?,
        bigText: String?,
        postTime: Long,
    ): ParsedPayment? {
        var haystack = listOfNotNull(title, text, bigText)
            .filter { it.isNotBlank() }
            .distinct()
            .joinToString("\n")
            .trim()
        if (haystack.length > MAX_SCAN) haystack = haystack.substring(0, MAX_SCAN)
        val lc = haystack.lowercase()

        // Reject requests/promos outright.
        if (NON_DEBIT.containsMatchIn(lc)) return null

        // Direction: ignore "credit card"/"debit card" as signals.
        val lcCards = lc.replace("credit card", "").replace("debit card", "")
        val hasCredit = CREDIT.containsMatchIn(lcCards)
        val sentDebit = SENT.containsMatchIn(lcCards) &&
                !SENT_YOU.containsMatchIn(lcCards) &&
                TO.containsMatchIn(lcCards)
        val hasDebit = DEBIT.containsMatchIn(lcCards) || sentDebit
        if (!(hasDebit && !hasCredit)) return null

        // Amount: first currency figure that is not a balance/limit/due figure.
        val amountMatch = AMOUNT.findAll(haystack).firstOrNull { m ->
            val pre = haystack.substring(maxOf(0, m.range.first - 16), m.range.first).lowercase()
            BALANCE_HINTS.none { pre.contains(it) }
        } ?: return null
        val amount = try {
            BigDecimal(amountMatch.groupValues[1].replace(",", ""))
        } catch (e: NumberFormatException) {
            return null
        }

        val refId = REF_ID.find(haystack)?.groupValues?.get(1)?.uppercase()
        val merchant = (MERCHANT.find(haystack)?.groupValues?.get(1)
            ?: VPA.find(haystack)?.groupValues?.get(1))
            ?.trim()
            ?.replace(Regex("\\s+(on|dated)\\s+.*$", RegexOption.IGNORE_CASE), "")
            ?.replace(Regex("\\s+"), " ")
            ?.trim()

        return ParsedPayment(amount, PaymentDirection.DEBIT, merchant, refId, postTime)
    }
}
