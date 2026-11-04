package com.danilkinkin.buckwheat.autocapture

import java.math.BigDecimal

enum class PaymentDirection { DEBIT, CREDIT }

/** Result of parsing a payment notification. Pure data, no Android dependencies. */
data class ParsedPayment(
    val amount: BigDecimal,
    val direction: PaymentDirection,
    val merchant: String?,
    val refId: String?,
    val postTime: Long,
)
