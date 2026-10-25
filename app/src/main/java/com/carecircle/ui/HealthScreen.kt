package com.carecircle.ui

import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.PowerManager
import android.provider.Settings
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.carecircle.R
import com.carecircle.data.Prefs
import com.carecircle.domain.HealthIssue
import com.carecircle.domain.ReminderHealth
import com.carecircle.reminder.Notifier
import com.carecircle.ui.theme.BigButton
import com.carecircle.ui.theme.Muted
import com.carecircle.ui.theme.Palette
import com.carecircle.ui.theme.SoftButton
import com.carecircle.ui.theme.SoftCard

/** Reads what the phone allows. Called again whenever the screen comes back from system settings. */
fun readHealth(ctx: Context, scheduled: Int, firstAdded: Long): ReminderHealth {
    val nm = ctx.getSystemService(NotificationManager::class.java)
    val status = readReminderStatus(ctx)
    val channelsOn = listOf(Notifier.CH_DOSE, Notifier.CH_URGENT).all { nm.getNotificationChannel(it)?.importance != NotificationManager.IMPORTANCE_NONE }
    return ReminderHealth(
        notifications = status.notifications, exactAlarms = status.exactAlarms, batteryUnrestricted = status.batteryUnrestricted,
        doNotDisturbOff = nm.currentInterruptionFilter == NotificationManager.INTERRUPTION_FILTER_ALL,
        channelsOn = channelsOn, scheduledMedicines = scheduled, lastReminderAt = Prefs(ctx).lastReminderAt,
        firstMedicineAddedAt = firstAdded, now = System.currentTimeMillis(),
    )
}

/** One screen that answers "will my reminders actually fire?", with a fix button for each problem and a test reminder. */
@Composable
fun HealthScreen(vm: AppViewModel, onBack: () -> Unit) {
    val ctx = LocalContext.current
    var health by remember { mutableStateOf<ReminderHealth?>(null) }
    var testSent by remember { mutableStateOf(false) }
    suspend fun reload() {
        val (count, first) = vm.healthInputs()
        health = readHealth(ctx, count, first)
    }
    LaunchedEffect(Unit) { Notifier.ensureChannels(ctx); reload() }
    val owner = LocalLifecycleOwner.current
    var tick by remember { mutableStateOf(0) }
    androidx.compose.runtime.DisposableEffect(owner) {
        val obs = LifecycleEventObserver { _, e -> if (e == Lifecycle.Event.ON_RESUME) tick++ }
        owner.lifecycle.addObserver(obs)
        onDispose { owner.lifecycle.removeObserver(obs) }
    }
    LaunchedEffect(tick) { if (tick > 0) reload() }

    LazyColumn(Modifier.fillMaxSize().statusBarsPadding(), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item { TextButton(onClick = onBack) { Text(stringResource(R.string.action_back)) } }
        item { Text(stringResource(R.string.health_title), style = MaterialTheme.typography.headlineLarge) }
        val h = health
        if (h == null) item { Muted(stringResource(R.string.info_loading)) }
        else {
            item {
                SoftCard(Modifier.fillMaxWidth(), tint = if (h.blocking) Palette.BadSoft else if (h.issues.isEmpty()) Palette.GoodSoft else Palette.WarnSoft) {
                    Text(
                        stringResource(if (h.blocking) R.string.health_bad else if (h.issues.isEmpty()) R.string.health_good else R.string.health_warn),
                        style = MaterialTheme.typography.titleLarge,
                    )
                    Muted(stringResource(R.string.health_scheduled, h.scheduledMedicines), Modifier.padding(top = 4.dp))
                    Muted(
                        if (h.lastReminderAt > 0) stringResource(R.string.health_last, java.text.DateFormat.getDateTimeInstance(java.text.DateFormat.MEDIUM, java.text.DateFormat.SHORT).format(java.util.Date(h.lastReminderAt)))
                        else stringResource(R.string.health_last_never),
                    )
                }
            }
            item { Row(Modifier.fillMaxWidth()) { Text(stringResource(R.string.health_checks), style = MaterialTheme.typography.titleLarge) } }
            item { Check(!HealthIssue.NOTIFICATIONS_OFF.inList(h), R.string.onb_notif_title, R.string.health_why_notif, blocking = true) { open(ctx, Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE, ctx.packageName)) } }
            item { Check(!HealthIssue.CHANNEL_OFF.inList(h), R.string.health_channels, R.string.health_why_channels, blocking = true) { open(ctx, Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE, ctx.packageName)) } }
            if (android.os.Build.VERSION.SDK_INT >= 31) item { Check(!HealthIssue.EXACT_ALARMS_OFF.inList(h), R.string.onb_exact_title, R.string.health_why_exact, blocking = true) { open(ctx, Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM, Uri.parse("package:${ctx.packageName}"))) } }
            item { Check(!HealthIssue.BATTERY_RESTRICTED.inList(h), R.string.onb_battery_title, R.string.onb_battery_why, blocking = false) { open(ctx, Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)) } }
            item { Check(!HealthIssue.DO_NOT_DISTURB.inList(h), R.string.health_dnd, R.string.health_why_dnd, blocking = false) { open(ctx, Intent(Settings.ACTION_ZEN_MODE_PRIORITY_SETTINGS)) } }
            item { Check(!HealthIssue.NOTHING_SCHEDULED.inList(h), R.string.health_medicines, R.string.health_why_medicines, blocking = false, action = null) {} }
            if (HealthIssue.NEVER_FIRED.inList(h)) item { Check(false, R.string.health_fired, R.string.health_why_fired, blocking = false, action = null) {} }
            item {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    BigButton(stringResource(R.string.health_test), {
                        Notifier.plainAlert(ctx, 9_000_001, ctx.getString(R.string.health_test_title), ctx.getString(R.string.health_test_text)); testSent = true
                    })
                    if (testSent) Muted(stringResource(R.string.health_test_sent))
                    SoftButton(stringResource(R.string.health_recheck), { tick++ }, Modifier.fillMaxWidth())
                }
            }
        }
    }
}

private fun HealthIssue.inList(h: ReminderHealth) = this in h.issues

private fun open(ctx: Context, intent: Intent) {
    runCatching { ctx.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
        .onFailure { runCatching { ctx.startActivity(Intent(Settings.ACTION_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) } }
}

@Composable
private fun Check(ok: Boolean, titleRes: Int, whyRes: Int, blocking: Boolean, action: String? = "open", onFix: () -> Unit) {
    SoftCard(Modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.Top) {
            Icon(
                if (ok) Icons.Default.CheckCircle else if (blocking) Icons.Default.ErrorOutline else Icons.Default.Warning, null,
                tint = if (ok) Palette.Good else if (blocking) Palette.Bad else Palette.Warn,
            )
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(stringResource(titleRes), style = MaterialTheme.typography.titleMedium)
                Muted(stringResource(whyRes))
                if (!ok && action != null) SoftButton(stringResource(R.string.onb_open_settings), onFix, Modifier.padding(top = 8.dp))
            }
        }
    }
}
