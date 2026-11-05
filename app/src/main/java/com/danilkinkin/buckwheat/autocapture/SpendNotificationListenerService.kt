package com.danilkinkin.buckwheat.autocapture

import android.app.Notification
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import com.danilkinkin.buckwheat.data.dao.AutoCaptureDao
import com.danilkinkin.buckwheat.data.entities.AutoCaptureFingerprint
import com.danilkinkin.buckwheat.data.entities.Transaction
import com.danilkinkin.buckwheat.data.entities.TransactionType
import com.danilkinkin.buckwheat.di.SettingsRepository
import com.danilkinkin.buckwheat.di.SpendsRepository
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import java.util.Date
import javax.inject.Inject

/**
 * Reads notifications from allowlisted payment apps and logs confident debits as
 * SPENT transactions. Event-driven only (no polling/alarms/wakelocks). The hot
 * path is a cheap reject ladder; work is dispatched off the binder thread only
 * for a real payment. Dedup collapses the same payment reported by several apps.
 */
@AndroidEntryPoint
class SpendNotificationListenerService : NotificationListenerService() {
    @Inject lateinit var spendsRepository: SpendsRepository
    @Inject lateinit var settingsRepository: SettingsRepository
    @Inject lateinit var autoCaptureDao: AutoCaptureDao

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val dedup = DedupGuard(windowMs = WINDOW_MS, clock = System::currentTimeMillis)

    @Volatile private var enabled = false
    @Volatile private var monitoredPackages: Set<String> = DEFAULT_PAYMENT_PACKAGES

    override fun onListenerConnected() {
        super.onListenerConnected()
        serviceScope.launch {
            val now = System.currentTimeMillis()
            dedup.seed(autoCaptureDao.recent(now - WINDOW_MS).map { it.fingerprint to it.postTime })
        }
        settingsRepository.isAutoCaptureEnabled().onEach { enabled = it }.launchIn(serviceScope)
        settingsRepository.getAutoCapturePackages().onEach { monitoredPackages = it }.launchIn(serviceScope)
    }

    override fun onNotificationPosted(sbn: StatusBarNotification?) {
        sbn ?: return
        if (!enabled) return
        if (sbn.packageName !in monitoredPackages) return
        val n = sbn.notification ?: return
        if (n.flags and Notification.FLAG_GROUP_SUMMARY != 0) return
        if (n.flags and Notification.FLAG_ONGOING_EVENT != 0) return

        val extras = n.extras ?: return
        val title = extras.getCharSequence(Notification.EXTRA_TITLE)?.toString()
        val text = extras.getCharSequence(Notification.EXTRA_TEXT)?.toString()
        val bigText = extras.getCharSequence(Notification.EXTRA_BIG_TEXT)?.toString()

        // Cheap keyword pre-filter before any regex work.
        val probe = ((title ?: "") + " " + (bigText ?: text ?: "")).lowercase()
        if (DEBIT_HINTS.none { probe.contains(it) }) return

        val parsed = try {
            PaymentNotificationParser.parse(sbn.packageName, title, text, bigText, sbn.postTime)
        } catch (e: Exception) {
            null
        } ?: return

        if (dedup.isDuplicate(parsed)) return
        dedup.remember(parsed) // synchronous: closes the race between near-simultaneous duplicates
        val keys = dedup.keys(parsed)

        serviceScope.launch {
            try {
                spendsRepository.addSpent(
                    Transaction(
                        type = TransactionType.SPENT,
                        value = parsed.amount,
                        date = Date(parsed.postTime),
                        comment = parsed.merchant ?: "",
                    )
                )
                for (key in keys) autoCaptureDao.insert(AutoCaptureFingerprint(key, parsed.postTime))
                autoCaptureDao.prune(System.currentTimeMillis() - WINDOW_MS)
            } catch (e: Exception) {
                // Never crash the listener on a bad notification.
            }
        }
    }

    override fun onDestroy() {
        serviceScope.cancel()
        super.onDestroy()
    }

    companion object {
        private const val WINDOW_MS = 600_000L
        private val DEBIT_HINTS = listOf(
            "debit", "spent", "paid", "sent", "deducted", "withdrawn", "purchase",
        )
    }
}
