package com.danilkinkin.buckwheat.settings

import android.content.Context
import android.content.Intent
import android.provider.Settings
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.hilt.navigation.compose.hiltViewModel
import com.danilkinkin.buckwheat.R
import com.danilkinkin.buckwheat.autocapture.DEFAULT_PAYMENT_PACKAGES
import com.danilkinkin.buckwheat.base.ButtonRow
import com.danilkinkin.buckwheat.base.CheckedRow
import com.danilkinkin.buckwheat.data.AppViewModel

private val KNOWN_APP_LABELS = linkedMapOf(
    "com.msf.kbank.mobile" to "Kotak811",
    "com.google.android.apps.nbu.paisa.user" to "Google Pay",
    "com.phonepe.app" to "PhonePe",
    "net.one97.paytm" to "Paytm",
    "in.org.npci.upiapp" to "BHIM",
    "com.dreamplug.androidapp" to "CRED",
    "money.super.payments" to "super.money",
)

private fun isListenerGranted(context: Context): Boolean {
    val flat = Settings.Secure.getString(context.contentResolver, "enabled_notification_listeners")
        ?: return false
    return flat.split(":").any { it.contains(context.packageName) }
}

@Composable
fun NotificationCaptureSwitcher(appViewModel: AppViewModel = hiltViewModel()) {
    val context = LocalContext.current
    val enabled by appViewModel.autoCaptureEnabled.observeAsState(false)
    val packages by appViewModel.autoCapturePackages.observeAsState(DEFAULT_PAYMENT_PACKAGES)
    var showAppsDialog by remember { mutableStateOf(false) }

    CheckedRow(
        checked = enabled,
        onValueChange = { appViewModel.setAutoCaptureEnabled(it) },
        text = stringResource(R.string.auto_capture_label),
        description = stringResource(R.string.auto_capture_description),
    )
    ButtonRow(
        icon = painterResource(R.drawable.ic_settings),
        text = stringResource(R.string.auto_capture_access_label),
        endCaption = stringResource(
            if (isListenerGranted(context)) R.string.auto_capture_access_granted
            else R.string.auto_capture_access_denied
        ),
        onClick = {
            context.startActivity(
                Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            )
        },
    )
    ButtonRow(
        icon = painterResource(R.drawable.ic_info),
        text = stringResource(R.string.auto_capture_apps_label),
        endCaption = packages.size.toString(),
        onClick = { showAppsDialog = true },
    )

    if (showAppsDialog) {
        val working = remember { packages.toMutableSet() }
        var tick by remember { mutableStateOf(0) }
        AlertDialog(
            onDismissRequest = { showAppsDialog = false },
            title = { Text(stringResource(R.string.auto_capture_apps_label)) },
            text = {
                Column {
                    KNOWN_APP_LABELS.forEach { (pkg, label) ->
                        @Suppress("UNUSED_EXPRESSION") tick
                        CheckedRow(
                            checked = working.contains(pkg),
                            onValueChange = {
                                if (it) working.add(pkg) else working.remove(pkg)
                                tick++
                            },
                            text = label,
                        )
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    appViewModel.setAutoCapturePackages(working.toSet())
                    showAppsDialog = false
                }) { Text(stringResource(R.string.auto_capture_apps_save)) }
            },
            dismissButton = {
                TextButton(onClick = { showAppsDialog = false }) {
                    Text(stringResource(android.R.string.cancel))
                }
            },
        )
    }
}
