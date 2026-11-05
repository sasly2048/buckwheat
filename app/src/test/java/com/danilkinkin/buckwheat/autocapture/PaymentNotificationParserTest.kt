package com.danilkinkin.buckwheat.autocapture

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.math.BigDecimal

class PaymentNotificationParserTest {
    private fun p(title: String?, text: String?, big: String? = null) =
        PaymentNotificationParser.parse("com.phonepe.app", title, text, big, 1000L)

    // --- original behavior ---
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

    // --- C2: amount must be the debit figure, not a balance/limit ---
    @Test fun picksDebitAmountNotBalance() {
        assertEquals(BigDecimal("250"), p("Bank", "Avbl Bal Rs.9,999.00. Rs.250 debited for UPI")!!.amount)
    }

    @Test fun picksDebitAmountWhenLimitFirst() {
        assertEquals(BigDecimal("1250"), p("Bank", "Available limit Rs.50,000. Rs.1,250 spent at AMAZON")!!.amount)
    }

    // --- C3: credit-card debits must be logged ---
    @Test fun creditCardDebitIsLogged() {
        val r = p("Bank", "Rs.1,250 spent on your HDFC Credit Card")!!
        assertEquals(BigDecimal("1250"), r.amount)
        assertEquals(PaymentDirection.DEBIT, r.direction)
    }

    // --- C4: direction of "sent" ---
    @Test fun sentYouIsNotDebit() {
        assertNull(p("Bank", "Rajesh sent you Rs.500"))
    }

    @Test fun sentToIsDebit() {
        assertEquals(BigDecimal("500"), p("Bank", "Rs.500 sent to Swiggy")!!.amount)
    }

    // --- C5: requests / promos are not spends ---
    @Test fun collectRequestIsDropped() {
        assertNull(p("PhonePe", "Payment request sent: Rs.500 to Swiggy"))
    }

    @Test fun promoIsDropped() {
        assertNull(p("Shop", "Purchase now at Rs.999 only"))
    }
}
