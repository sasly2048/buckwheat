package com.danilkinkin.buckwheat.autocapture

import org.junit.Assert.assertTrue
import org.junit.Test

class PaymentSourcesTest {
    @Test
    fun containsSeededPaymentApps() {
        assertTrue(
            DEFAULT_PAYMENT_PACKAGES.containsAll(
                listOf(
                    "com.msf.kbank.mobile",                   // Kotak811
                    "com.google.android.apps.nbu.paisa.user", // GPay
                    "com.phonepe.app",                        // PhonePe
                    "net.one97.paytm",                        // Paytm
                    "in.org.npci.upiapp",                     // BHIM
                    "com.dreamplug.androidapp",               // CRED
                    "money.super.payments"                    // super.money
                )
            )
        )
    }
}
