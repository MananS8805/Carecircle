package com.carecircle.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatDelegate
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.os.LocaleListCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.carecircle.AppLocale
import com.carecircle.R
import com.carecircle.data.OnDeviceAi
import androidx.compose.runtime.rememberCoroutineScope
import kotlinx.coroutines.launch
import com.carecircle.ui.theme.LogoMark
import com.carecircle.ui.theme.Muted
import com.carecircle.ui.theme.Palette
import com.carecircle.ui.theme.SoftButton
import com.carecircle.ui.theme.SoftCard

@Composable
fun SettingsScreen(vm: AppViewModel, onManagePeople: () -> Unit, onFamily: () -> Unit, onHealth: () -> Unit, onPrivacy: () -> Unit, onDiagnostics: () -> Unit) {
    val people by vm.people.collectAsStateWithLifecycle()
    val status by rememberReminderStatus()
    var showSetup by remember { mutableStateOf(false) }

    Column(
        Modifier.fillMaxSize().statusBarsPadding().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(stringResource(R.string.tab_settings), style = MaterialTheme.typography.headlineLarge)

        SoftCard(Modifier.fillMaxWidth()) {
            Text(stringResource(R.string.settings_people), style = MaterialTheme.typography.titleLarge)
            Muted(people.joinToString(", ") { displayName(it, people) })
            SoftButton(stringResource(R.string.people_manage), onManagePeople, Modifier.padding(top = 10.dp))
        }

        SoftCard(Modifier.fillMaxWidth()) {
            Text(stringResource(R.string.family_title), style = MaterialTheme.typography.titleLarge)
            Muted(stringResource(R.string.family_settings_hint))
            SoftButton(stringResource(R.string.family_open), onFamily, Modifier.padding(top = 10.dp))
            SoftButton(stringResource(R.string.mode_switch_family), { vm.setMode(com.carecircle.data.Prefs.MODE_FAMILY) }, Modifier.padding(top = 8.dp))
        }

        LanguageCard(vm)

        DisplayCard(vm)

        AiCard()

        SoftCard(Modifier.fillMaxWidth()) {
            Text(stringResource(R.string.settings_reminders), style = MaterialTheme.typography.titleLarge)
            StatusLine(stringResource(R.string.onb_notif_title), status.notifications)
            if (android.os.Build.VERSION.SDK_INT >= 31) StatusLine(stringResource(R.string.onb_exact_title), status.exactAlarms)
            StatusLine(stringResource(R.string.onb_battery_title), status.batteryUnrestricted)
            val weeklyOn by vm.weeklyReminder.collectAsStateWithLifecycle()
            DisplaySwitch(stringResource(R.string.weekly_reminder), stringResource(R.string.weekly_reminder_hint), weeklyOn, vm::setWeeklyReminder)
            SoftButton(stringResource(R.string.health_open), onHealth, Modifier.padding(top = 10.dp))
            SoftButton(stringResource(R.string.settings_review_setup), { showSetup = true }, Modifier.padding(top = 8.dp))
            SoftButton(stringResource(R.string.guide_replay), { vm.replayGuide() }, Modifier.padding(top = 8.dp))
            SoftButton(stringResource(R.string.privacy_title), onPrivacy, Modifier.padding(top = 8.dp))
            SoftButton(stringResource(R.string.diag_title), onDiagnostics, Modifier.padding(top = 8.dp))
        }

        DataCard(vm)

        SoftCard(Modifier.fillMaxWidth()) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                LogoMark(36.dp)
                Spacer(Modifier.width(12.dp))
                Column {
                    Text(stringResource(R.string.app_name), style = MaterialTheme.typography.titleMedium)
                    Muted(stringResource(R.string.settings_version, "1.3"))
                }
            }
            Text(stringResource(R.string.settings_privacy), Modifier.padding(top = 10.dp), style = MaterialTheme.typography.bodySmall)
            Text(stringResource(R.string.disclaimer_medical), Modifier.padding(top = 8.dp), style = MaterialTheme.typography.labelMedium)
        }
    }
    if (showSetup) OnboardingDialog(status, onDone = { showSetup = false })
}

/** Checks whether this phone can run Gemini Nano on the device (experimental). The test text never leaves the phone. */
@Composable
private fun AiCard() {
    val scope = rememberCoroutineScope()
    var state by remember { mutableStateOf<OnDeviceAi.Availability?>(null) }
    var busy by remember { mutableStateOf(false) }
    var reply by remember { mutableStateOf("") }
    val sample = stringResource(R.string.ai_test_prompt)
    SoftCard(Modifier.fillMaxWidth()) {
        Text(stringResource(R.string.ai_title), style = MaterialTheme.typography.titleLarge)
        Muted(stringResource(R.string.ai_intro))
        state?.let {
            Text(
                stringResource(
                    when (it) {
                        OnDeviceAi.Availability.AVAILABLE -> R.string.ai_status_available
                        OnDeviceAi.Availability.DOWNLOADABLE -> R.string.ai_status_downloadable
                        OnDeviceAi.Availability.DOWNLOADING -> R.string.ai_status_downloading
                        OnDeviceAi.Availability.UNAVAILABLE -> R.string.ai_status_unavailable
                        OnDeviceAi.Availability.ERROR -> R.string.ai_status_error
                    },
                ),
                Modifier.padding(top = 8.dp), style = MaterialTheme.typography.bodyLarge, color = Palette.Orange,
            )
        }
        Column(Modifier.padding(top = 10.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            SoftButton(stringResource(R.string.ai_check), {
                busy = true; reply = ""
                scope.launch { state = OnDeviceAi.availability(); busy = false }
            }, Modifier.fillMaxWidth(), enabled = !busy)
            if (state == OnDeviceAi.Availability.DOWNLOADABLE) SoftButton(stringResource(R.string.ai_download), {
                busy = true
                scope.launch { OnDeviceAi.download(); state = OnDeviceAi.availability(); busy = false }
            }, Modifier.fillMaxWidth(), enabled = !busy)
            if (state == OnDeviceAi.Availability.AVAILABLE) SoftButton(stringResource(R.string.ai_test), {
                busy = true
                scope.launch { reply = OnDeviceAi.generate(sample).orEmpty(); busy = false }
            }, Modifier.fillMaxWidth(), enabled = !busy)
        }
        if (reply.isNotEmpty()) Text(stringResource(R.string.ai_test_result, reply), Modifier.padding(top = 10.dp), style = MaterialTheme.typography.bodyMedium)
    }
}

/** System default, English, Hindi or Gujarati. The change recreates the screen; the selected person is stored, so it survives. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun LanguageCard(vm: AppViewModel) {
    var current by remember { mutableStateOf(vm.prefs.language) }
    SoftCard(Modifier.fillMaxWidth()) {
        Text(stringResource(R.string.settings_language), style = MaterialTheme.typography.titleLarge)
        FlowRow(Modifier.padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            AppLocale.supported.forEach { (tag, label) ->
                FilterChip(
                    selected = current == tag,
                    onClick = {
                        current = tag
                        vm.prefs.language = tag
                        AppCompatDelegate.setApplicationLocales(LocaleListCompat.forLanguageTags(tag))
                    },
                    label = { Text(if (tag.isEmpty()) stringResource(R.string.language_system) else label) },
                    modifier = Modifier.heightIn(min = 48.dp), shape = CircleShape,
                    colors = FilterChipDefaults.filterChipColors(selectedContainerColor = Palette.Ink, selectedLabelColor = Color.White),
                )
            }
        }
    }
}

/** Text size, high contrast and simple mode. Each applies at once and is remembered. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun DisplayCard(vm: AppViewModel) {
    val d by vm.display.collectAsStateWithLifecycle()
    SoftCard(Modifier.fillMaxWidth()) {
        Text(stringResource(R.string.display_title), style = MaterialTheme.typography.titleLarge)
        Muted(stringResource(R.string.display_text_size), Modifier.padding(top = 8.dp))
        FlowRow(Modifier.padding(top = 4.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf(
                com.carecircle.domain.TextSize.NORMAL to R.string.size_normal,
                com.carecircle.domain.TextSize.LARGE to R.string.size_large,
                com.carecircle.domain.TextSize.EXTRA_LARGE to R.string.size_xl,
            ).forEach { (size, label) ->
                FilterChip(
                    selected = d.textSize == size, onClick = { vm.setTextSize(size) }, label = { Text(stringResource(label)) },
                    modifier = Modifier.heightIn(min = 48.dp), shape = CircleShape,
                    colors = FilterChipDefaults.filterChipColors(selectedContainerColor = Palette.Ink, selectedLabelColor = Color.White),
                )
            }
        }
        DisplaySwitch(stringResource(R.string.display_contrast), stringResource(R.string.display_contrast_hint), d.highContrast, vm::setHighContrast)
        DisplaySwitch(stringResource(R.string.display_simple), stringResource(R.string.display_simple_hint), d.simpleMode, vm::setSimpleMode)
    }
}

@Composable
private fun DisplaySwitch(title: String, hint: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        Modifier.fillMaxWidth().heightIn(min = 56.dp).padding(top = 8.dp)
            .toggleable(value = checked, role = androidx.compose.ui.semantics.Role.Switch, onValueChange = onChange),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            Muted(hint)
        }
        androidx.compose.material3.Switch(checked = checked, onCheckedChange = null)
    }
}

/** Export, restore and delete-all. Everything stays on the phone; files go through the system file picker. */
@Composable
private fun DataCard(vm: AppViewModel) {
    val msg by vm.dataMsg.collectAsStateWithLifecycle()
    val summary by vm.deleteSummary.collectAsStateWithLifecycle()
    var askExport by remember { mutableStateOf(false) }
    var deleteStep by remember { mutableStateOf(0) }
    val export = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { it?.let(vm::exportTo) }
    val restore = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { it?.let(vm::readBackup) }

    SoftCard(Modifier.fillMaxWidth()) {
        Text(stringResource(R.string.data_title), style = MaterialTheme.typography.titleLarge)
        Muted(stringResource(R.string.data_intro))
        Column(Modifier.padding(top = 10.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            SoftButton(stringResource(R.string.data_export), { askExport = true }, Modifier.fillMaxWidth())
            SoftButton(stringResource(R.string.data_restore), { restore.launch(arrayOf("*/*")) }, Modifier.fillMaxWidth())
            SoftButton(stringResource(R.string.data_delete_all), { deleteStep = 1 }, Modifier.fillMaxWidth())
        }
        msg?.let {
            Text(stringResource(it.res), Modifier.padding(top = 10.dp), style = MaterialTheme.typography.bodyMedium, color = Palette.Orange)
        }
        summary?.let { d ->
            Text(stringResource(R.string.delete_summary, d.people, d.medicines, d.doseRecords, d.otherRecords), Modifier.padding(top = 8.dp), style = MaterialTheme.typography.bodyMedium)
        }
    }

    RestoreDialogs(vm)
    if (askExport) ExportPasswordDialog(
        onChoose = { pw -> askExport = false; vm.setExportPassword(pw); export.launch("carecircle-backup.json") },
        onCancel = { askExport = false },
    )
    DeleteAllFlow(vm, deleteStep) { deleteStep = it }
}

/** The two-step confirmation for "Delete all my data"; shared by Settings and the Privacy page. [step] 0 = closed. */
@Composable
internal fun DeleteAllFlow(vm: AppViewModel, step: Int, setStep: (Int) -> Unit) {
    if (step == 1) AlertDialog(
        onDismissRequest = { setStep(0) },
        title = { Text(stringResource(R.string.data_delete_title)) },
        text = { Text(stringResource(R.string.data_delete_text)) },
        confirmButton = { TextButton(onClick = { setStep(2) }) { Text(stringResource(R.string.action_continue)) } },
        dismissButton = { TextButton(onClick = { setStep(0) }) { Text(stringResource(R.string.action_cancel)) } },
        containerColor = Palette.Surface,
    )
    if (step == 2) AlertDialog(
        onDismissRequest = { setStep(0) },
        title = { Text(stringResource(R.string.data_delete_final_title)) },
        text = { Text(stringResource(R.string.data_delete_final_text)) },
        confirmButton = { TextButton(onClick = { setStep(0); vm.deleteAllData() }) { Text(stringResource(R.string.data_delete_final_yes), color = Palette.Bad) } },
        dismissButton = { TextButton(onClick = { setStep(0) }) { Text(stringResource(R.string.action_cancel)) } },
        containerColor = Palette.Surface,
    )
}

@Composable
private fun StatusLine(label: String, ok: Boolean) {
    Row(Modifier.padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        Icon(
            if (ok) Icons.Default.CheckCircle else Icons.Default.ErrorOutline, null,
            tint = if (ok) Palette.Good else Palette.Warn,
        )
        Spacer(Modifier.width(10.dp))
        Text(label, style = MaterialTheme.typography.bodyLarge)
    }
}
