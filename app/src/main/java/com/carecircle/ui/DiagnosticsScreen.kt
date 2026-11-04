package com.carecircle.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
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
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.carecircle.BuildConfig
import com.carecircle.R
import com.carecircle.data.AppDb
import com.carecircle.data.CareSync
import com.carecircle.data.DiagLog
import com.carecircle.data.Prefs
import com.carecircle.domain.DiagReport
import com.carecircle.domain.HeldAlerts
import com.carecircle.domain.PendingNotifies
import com.carecircle.ui.theme.BigButton
import com.carecircle.ui.theme.Muted
import com.carecircle.ui.theme.Palette
import com.carecircle.ui.theme.SoftButton
import com.carecircle.ui.theme.SoftCard
import java.text.DateFormat
import java.util.Date

/** Builds the report text. Numbers, yes/no and times only: no medicine, person, dose or note ever goes in. */
suspend fun buildDiagnosticsReport(ctx: Context): String {
    val prefs = Prefs(ctx)
    val dao = AppDb.get(ctx).sync()
    val status = readReminderStatus(ctx)
    val fmt = DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.MEDIUM)
    fun at(ms: Long) = if (ms <= 0) "never" else fmt.format(Date(ms))
    fun yn(b: Boolean) = if (b) "yes" else "no"
    val info = listOf(
        "App" to "${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE}) ${BuildConfig.BUILD_TYPE}",
        "Device" to "${Build.MANUFACTURER} ${Build.MODEL}, Android ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})",
        "Firebase configured" to yn(CareSync.isConfigured(ctx)),
        "Role" to (prefs.appMode.ifEmpty { "not chosen" }),
        "Family links shared by this phone" to dao.links().size.toString(),
        "People followed by this phone" to dao.following().size.toString(),
        "Last status sent" to at(prefs.lastSyncAt),
        "Missed-dose push on (family phone)" to yn(prefs.pushEnabled),
        "Pushes waiting to send" to PendingNotifies.decode(prefs.pendingNotify).size.toString(),
        "Alerts held for the digest" to HeldAlerts.decode(prefs.heldAlerts).size.toString(),
        "Notifications allowed" to yn(status.notifications),
        "Exact alarms allowed" to yn(status.exactAlarms),
        "Battery unrestricted" to yn(status.batteryUnrestricted),
        "Last reminder re-arm" to (prefs.rescheduleLog.firstOrNull()?.let { "${at(it.at)} (${it.reason}, ${it.alarms} alarms)" } ?: "never"),
        "Local crash log" to if (prefs.crashLogEnabled) "on" else "off",
    )
    return DiagReport.build(info, DiagLog.read(ctx)) { at(it) }
}

@Composable
fun DiagnosticsScreen(vm: AppViewModel, onBack: () -> Unit) {
    val ctx = LocalContext.current
    var report by remember { mutableStateOf("") }
    var reload by remember { mutableStateOf(0) }
    var crashLog by remember { mutableStateOf(vm.prefs.crashLogEnabled) }
    var copied by remember { mutableStateOf(false) }
    LaunchedEffect(reload) { report = buildDiagnosticsReport(ctx) }

    Column(Modifier.fillMaxSize().statusBarsPadding().verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        TextButton(onClick = onBack) { Text(stringResource(R.string.action_back)) }
        Text(stringResource(R.string.diag_title), style = MaterialTheme.typography.headlineLarge)
        Muted(stringResource(R.string.diag_note))
        SoftCard(Modifier.fillMaxWidth()) {
            Row(
                Modifier.fillMaxWidth().defaultMinSize(minHeight = 56.dp).toggleable(value = crashLog, role = Role.Switch, onValueChange = {
                    crashLog = it; vm.prefs.crashLogEnabled = it
                    if (it) DiagLog.installCrashHandlerIfEnabled(ctx)
                }),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f)) {
                    Text(stringResource(R.string.diag_crash_log), style = MaterialTheme.typography.titleMedium)
                    Muted(stringResource(R.string.diag_crash_log_hint))
                }
                Switch(checked = crashLog, onCheckedChange = null)
            }
        }
        SoftCard(Modifier.fillMaxWidth()) {
            SelectionContainer { Text(report, fontFamily = FontFamily.Monospace, fontSize = 12.sp, lineHeight = 17.sp) }
        }
        BigButton(stringResource(R.string.diag_copy), {
            ctx.getSystemService(ClipboardManager::class.java).setPrimaryClip(ClipData.newPlainText("CareCircle diagnostics", report)); copied = true
        })
        if (copied) Muted(stringResource(R.string.diag_copied))
        SoftButton(stringResource(R.string.diag_share), {
            val send = Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, report)
            ctx.startActivity(Intent.createChooser(send, ctx.getString(R.string.diag_share)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        }, Modifier.fillMaxWidth())
        SoftButton(stringResource(R.string.diag_clear), { DiagLog.clear(ctx); copied = false; reload++ }, Modifier.fillMaxWidth())
        SoftButton(stringResource(R.string.diag_refresh), { reload++ }, Modifier.fillMaxWidth())
        DebugTools.CrashButton()
        Muted(stringResource(R.string.diag_note), Modifier.padding(top = 4.dp))
        androidx.compose.foundation.layout.Spacer(Modifier.padding(bottom = 8.dp))
        Text(stringResource(R.string.disclaimer_medical), style = MaterialTheme.typography.labelMedium, color = Palette.Muted)
    }
}
