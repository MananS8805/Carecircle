package com.carecircle.ui

import android.Manifest
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.RadioButtonUnchecked
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.carecircle.R
import com.carecircle.ui.theme.BigButton
import com.carecircle.ui.theme.LogoMark
import com.carecircle.ui.theme.Muted
import com.carecircle.ui.theme.Palette
import com.carecircle.ui.theme.SoftButton
import com.carecircle.ui.theme.SoftCard

/** First-run setup: notifications, exact alarms and battery settings, each with a one-line reason. */
@Composable
fun OnboardingDialog(status: ReminderStatus, onDone: () -> Unit) {
    val ctx = LocalContext.current
    var notifAsked by remember { mutableStateOf(false) }
    val notifLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { notifAsked = true }

    Dialog(onDismissRequest = onDone) {
        SoftCard(Modifier.fillMaxWidth()) {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                LogoMark(40.dp)
                Text(stringResource(R.string.onb_title), style = MaterialTheme.typography.headlineMedium)
                Muted(stringResource(R.string.onb_intro))

                SetupRow(
                    stringResource(R.string.onb_notif_title), stringResource(R.string.onb_notif_why), status.notifications,
                    if (notifAsked) stringResource(R.string.onb_open_settings) else stringResource(R.string.onb_allow),
                ) {
                    if (notifAsked || android.os.Build.VERSION.SDK_INT < 33) {
                        ctx.startActivity(
                            Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE, ctx.packageName)
                                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                        )
                    } else notifLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                }
                if (android.os.Build.VERSION.SDK_INT >= 31) {
                    SetupRow(stringResource(R.string.onb_exact_title), stringResource(R.string.onb_exact_why), status.exactAlarms, stringResource(R.string.onb_open_settings)) {
                        ctx.startActivity(
                            Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM, Uri.parse("package:${ctx.packageName}"))
                                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                        )
                    }
                }
                SetupRow(stringResource(R.string.onb_battery_title), stringResource(R.string.onb_battery_why), status.batteryUnrestricted, stringResource(R.string.onb_open_settings)) {
                    ctx.startActivity(Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                }
                BigButton(stringResource(R.string.onb_done), onDone)
            }
        }
    }
}

@Composable
private fun SetupRow(title: String, why: String, ok: Boolean, actionLabel: String, onAction: () -> Unit) {
    Row(verticalAlignment = Alignment.Top) {
        Icon(
            if (ok) Icons.Default.CheckCircle else Icons.Default.RadioButtonUnchecked, null,
            tint = if (ok) Palette.Good else Palette.Muted, modifier = Modifier.size(26.dp),
        )
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            Muted(why)
            if (!ok) SoftButton(actionLabel, onAction, Modifier.padding(top = 6.dp))
        }
    }
}
