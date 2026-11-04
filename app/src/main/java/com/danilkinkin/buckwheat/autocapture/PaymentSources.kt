package com.danilkinkin.buckwheat.autocapture

/**
 * Default allowlist of payment/bank app package names whose notifications are
 * scanned for debits. User-editable at runtime; this is only the seed.
 */
val DEFAULT_PAYMENT_PACKAGES: Set<String> = setOf(
    "com.msf.kbank.mobile",                   // Kotak811 / Kotak Mobile Banking
    "com.google.android.apps.nbu.paisa.user", // Google Pay (India)
    "com.phonepe.app",                        // PhonePe
    "net.one97.paytm",                        // Paytm
    "in.org.npci.upiapp",                     // BHIM
    "com.dreamplug.androidapp",               // CRED
    "money.super.payments",                   // super.money (UPI by Flipkart)
)
