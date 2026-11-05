package com.danilkinkin.buckwheat.autocapture

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.math.BigDecimal

class PaymentNotificationParserTest {
    private fun p(title: String?, text: String?, big: String? = null) =
        PaymentNotificationParser.parse("com.phonepe.app", title, text, big, 1000L)

    @Test fun parsesSimpleDebit() {
        val r = p("PhonePe", "You paid Rs.250 to Swiggy")!!
        assertEquals(BigDecimal("250"), r.amount)
        assertEquals(PaymentDirection.DEBIT, r.direction)
        assertEquals("Swiggy", r.merchant)
    }

    @Test fun stripsIndianGrouping() {
        assertEquals(BigDecimal("123456.00"), p("Bank", "Rs.1,23,456.00 debited")!!.amount)
    }

    @Test fun creditIsDropped() {
        assertNull(p("Bank", "Rs.500 credited to your account"))
    }

    @Test fun mixedDebitCreditIsAmbiguousDrop() {
        assertNull(p("Bank", "Rs.200 debited; Rs.5 cashback credited"))
    }

    @Test fun amountWithoutCurrencyMarkerRejected() {
        assertNull(p("Bank", "order 250 confirmed"))
    }

    @Test fun capturesRefId() {
        assertEquals("123456789012", p("GPay", "Rs.10 paid", "UPI Ref No 123456789012")!!.refId)
    }

    @Test fun noAmountReturnsNull() {
        assertNull(p("Bank", "Welcome to the app"))
    }
}
