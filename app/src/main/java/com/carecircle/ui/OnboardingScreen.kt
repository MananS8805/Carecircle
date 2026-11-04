package com.carecircle.ui

import android.Manifest
import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatDelegate
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Keyboard
import androidx.compose.material.icons.filled.Mic
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
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.os.LocaleListCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.carecircle.AppLocale
import com.carecircle.R
import com.carecircle.domain.Onboarding
import com.carecircle.domain.OnboardingStep
import com.carecircle.reminder.DoseWidget
import com.carecircle.reminder.Notifier
import com.carecircle.ui.theme.BigButton
import com.carecircle.ui.theme.LogoMark
import com.carecircle.ui.theme.Muted
import com.carecircle.ui.theme.Palette
import com.carecircle.ui.theme.SoftButton
import com.carecircle.ui.theme.SoftCard

/**
 * First-run guide: five steps, each skippable, the whole guide too. The step is saved as it advances, so it resumes after
 * the process dies, and the person step edits the existing first person instead of adding one, so nothing is duplicated.
 * [onNavigate] opens an existing screen (scan, add medicine); the guide is waiting on its own route when the user returns.
 */
@Composable
fun OnboardingScreen(vm: AppViewModel, step: OnboardingStep, onNavigate: (String) -> Unit, onFinished: () -> Unit) {
    Column(Modifier.fillMaxSize().statusBarsPadding().verticalScroll(rememberScrollState()).padding(20.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            LogoMark(36.dp)
            Muted(stringResource(R.string.guide_step, step.ordinal + 1, OnboardingStep.entries.size), Modifier.weight(1f).padding(start = 12.dp))
            TextButton(onClick = { vm.finishGuide(); onFinished() }, Modifier.heightIn(min = 48.dp)) { Text(stringResource(R.string.guide_skip_all)) }
        }
        when (step) {
            OnboardingStep.LANGUAGE_PRIVACY -> LanguageAndPrivacy(vm) { onNavigate("privacy") }
            OnboardingStep.PERSON -> FirstPerson(vm)
            OnboardingStep.MEDICINE -> FirstMedicine(vm, onNavigate)
            OnboardingStep.PERMISSIONS -> Permissions(vm)
            OnboardingStep.SUMMARY -> Summary(vm, onFinished)
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun LanguageAndPrivacy(vm: AppViewModel, onPrivacy: () -> Unit) {
    var current by remember { mutableStateOf(vm.prefs.language) }
    val msg by vm.dataMsg.collectAsStateWithLifecycle()
    val restore = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { it?.let(vm::readBackup) }
    Text(stringResource(R.string.guide_privacy_title), style = MaterialTheme.typography.headlineLarge)
    Muted(stringResource(R.string.guide_privacy_text))
    SoftButton(stringResource(R.string.privacy_title), onPrivacy, Modifier.fillMaxWidth())
    Text(stringResource(R.string.settings_language), style = MaterialTheme.typography.titleLarge)
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        AppLocale.supported.forEach { (tag, label) ->
            FilterChip(
                selected = current == tag,
                onClick = { current = tag; vm.prefs.language = tag; AppCompatDelegate.setApplicationLocales(LocaleListCompat.forLanguageTags(tag)) },
                label = { Text(if (tag.isEmpty()) stringResource(R.string.language_system) else label) },
                modifier = Modifier.heightIn(min = 48.dp), shape = CircleShape,
                colors = FilterChipDefaults.filterChipColors(selectedContainerColor = Palette.Ink, selectedLabelColor = Color.White),
            )
        }
    }
    BigButton(stringResource(R.string.guide_next), { vm.guideNext(OnboardingStep.LANGUAGE_PRIVACY) })
    SoftButton(stringResource(R.string.guide_restore), { restore.launch(arrayOf("*/*")) }, Modifier.fillMaxWidth())
    msg?.let { Text(stringResource(it.res), color = Palette.Orange) }
    RestoreDialogs(vm)
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun FirstPerson(vm: AppViewModel) {
    var name by rememberSaveable { mutableStateOf("") }
    var phone by rememberSaveable { mutableStateOf("") }
    val chips = listOf(R.string.guide_rel_me, R.string.guide_rel_mom, R.string.guide_rel_dad, R.string.guide_rel_grandparent).map { stringResource(it) }
    Text(stringResource(R.string.guide_person_title), style = MaterialTheme.typography.headlineLarge)
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        chips.forEach { c -> FilterChip(selected = name == c, onClick = { name = c }, label = { Text(c) }, modifier = Modifier.heightIn(min = 48.dp), shape = CircleShape) }
    }
    AppField(name, { name = it.take(Onboarding.MAX_NAME) }, stringResource(R.string.guide_person_name))
    AppField(phone, { phone = it.take(20) }, stringResource(R.string.guide_caregiver))
    Muted(stringResource(R.string.guide_caregiver_why))
    BigButton(stringResource(R.string.guide_person_save), { vm.saveFirstPerson(name, phone) { vm.guideNext(OnboardingStep.PERSON) } }, enabled = Onboarding.validName(name))
    SoftButton(stringResource(R.string.guide_skip_step), { vm.guideNext(OnboardingStep.PERSON) }, Modifier.fillMaxWidth())
}

@Composable
private fun FirstMedicine(vm: AppViewModel, onNavigate: (String) -> Unit) {
    Text(stringResource(R.string.guide_med_title), style = MaterialTheme.typography.headlineLarge)
    Muted(stringResource(R.string.guide_med_text))
    BigButton(stringResource(R.string.guide_med_scan), { vm.guideNext(OnboardingStep.MEDICINE); onNavigate("scan") }, icon = Icons.Default.CameraAlt)
    SoftButton(stringResource(R.string.guide_med_speak), { vm.startWithVoice.value = true; vm.guideNext(OnboardingStep.MEDICINE); onNavigate("edit/0") }, Modifier.fillMaxWidth(), icon = Icons.Default.Mic)
    SoftButton(stringResource(R.string.guide_med_type), { vm.guideNext(OnboardingStep.MEDICINE); onNavigate("edit/0") }, Modifier.fillMaxWidth(), icon = Icons.Default.Keyboard)
    SoftButton(stringResource(R.string.guide_med_skip), { vm.guideNext(OnboardingStep.MEDICINE) }, Modifier.fillMaxWidth())
}

@Composable
private fun Permissions(vm: AppViewModel) {
    val ctx = LocalContext.current
    val status by rememberReminderStatus()
    var testSent by remember { mutableStateOf(false) }
    val notif = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { }
    Text(stringResource(R.string.guide_perm_title), style = MaterialTheme.typography.headlineLarge)
    PermissionCard(R.string.onb_notif_title, R.string.onb_notif_why, status.notifications) {
        if (android.os.Build.VERSION.SDK_INT >= 33) notif.launch(Manifest.permission.POST_NOTIFICATIONS)
        else ctx.startActivity(Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE, ctx.packageName).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }
    if (android.os.Build.VERSION.SDK_INT >= 31) PermissionCard(R.string.onb_exact_title, R.string.onb_exact_why, status.exactAlarms) {
        ctx.startActivity(Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM, Uri.parse("package:${ctx.packageName}")).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }
    PermissionCard(R.string.onb_battery_title, R.string.onb_battery_why, status.batteryUnrestricted) {
        ctx.startActivity(Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }
    SoftButton(stringResource(R.string.health_test), {
        Notifier.plainAlert(ctx, 9_000_001, ctx.getString(R.string.health_test_title), ctx.getString(R.string.health_test_text)); testSent = true
    }, Modifier.fillMaxWidth())
    if (testSent) Muted(stringResource(R.string.health_test_sent))
    BigButton(stringResource(R.string.guide_next), { vm.guideNext(OnboardingStep.PERMISSIONS) })
}

@Composable
private fun PermissionCard(title: Int, why: Int, granted: Boolean, onAllow: () -> Unit) {
    var skipped by rememberSaveable(title) { mutableStateOf(false) }
    SoftCard(Modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (granted) Icon(Icons.Default.CheckCircle, null, tint = Palette.Good)
            Text(stringResource(title), Modifier.padding(start = if (granted) 8.dp else 0.dp), style = MaterialTheme.typography.titleMedium)
        }
        Muted(stringResource(why), Modifier.padding(top = 4.dp))
        if (granted) Text(stringResource(R.string.guide_perm_granted), Modifier.padding(top = 6.dp), color = Palette.Good)
        else if (!skipped) Row(Modifier.padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            SoftButton(stringResource(R.string.onb_allow), onAllow)
            TextButton(onClick = { skipped = true }, Modifier.heightIn(min = 48.dp)) { Text(stringResource(R.string.guide_perm_not_now)) }
        }
    }
}

@Composable
private fun Summary(vm: AppViewModel, onFinished: () -> Unit) {
    val ctx = LocalContext.current
    val canPin = remember { ctx.getSystemService(AppWidgetManager::class.java)?.isRequestPinAppWidgetSupported == true }
    Text(stringResource(R.string.guide_done_title), style = MaterialTheme.typography.headlineLarge)
    Muted(stringResource(R.string.guide_done_text))
    if (canPin) SoftButton(stringResource(R.string.guide_add_widget), {
        ctx.getSystemService(AppWidgetManager::class.java)?.requestPinAppWidget(ComponentName(ctx, DoseWidget::class.java), null, null)
    }, Modifier.fillMaxWidth())
    Spacer(Modifier.height(4.dp))
    BigButton(stringResource(R.string.guide_finish), { vm.finishGuide(); onFinished() })
}
