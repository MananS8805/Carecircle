package com.carecircle.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ErrorOutline
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.carecircle.R
import com.carecircle.ui.theme.LogoMark
import com.carecircle.ui.theme.Muted
import com.carecircle.ui.theme.Palette
import com.carecircle.ui.theme.SoftButton
import com.carecircle.ui.theme.SoftCard

@Composable
fun SettingsScreen(vm: AppViewModel) {
    var caregiver by remember { mutableStateOf(vm.prefs.caregiverPhone) }
    var pharmacy by remember { mutableStateOf(vm.prefs.pharmacyPhone) }
    var patient by remember { mutableStateOf(vm.prefs.patientName) }
    val status by rememberReminderStatus()
    var showSetup by remember { mutableStateOf(false) }

    Column(
        Modifier.fillMaxSize().statusBarsPadding().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(stringResource(R.string.tab_settings), style = MaterialTheme.typography.headlineLarge)

        SoftCard(Modifier.fillMaxWidth()) {
            Text(stringResource(R.string.settings_people), style = MaterialTheme.typography.titleLarge)
            AppField(patient, { patient = it; vm.prefs.patientName = it }, stringResource(R.string.field_patient_name), Modifier.padding(top = 8.dp))
            AppField(caregiver, { caregiver = it; vm.prefs.caregiverPhone = it }, stringResource(R.string.field_caregiver), Modifier.padding(top = 8.dp))
            AppField(pharmacy, { pharmacy = it; vm.prefs.pharmacyPhone = it }, stringResource(R.string.field_pharmacy), Modifier.padding(top = 8.dp))
        }

        SoftCard(Modifier.fillMaxWidth()) {
            Text(stringResource(R.string.settings_reminders), style = MaterialTheme.typography.titleLarge)
            StatusLine(stringResource(R.string.onb_notif_title), status.notifications)
            if (android.os.Build.VERSION.SDK_INT >= 31) StatusLine(stringResource(R.string.onb_exact_title), status.exactAlarms)
            StatusLine(stringResource(R.string.onb_battery_title), status.batteryUnrestricted)
            SoftButton(stringResource(R.string.settings_review_setup), { showSetup = true }, Modifier.padding(top = 10.dp))
        }

        SoftCard(Modifier.fillMaxWidth()) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                LogoMark(36.dp)
                Spacer(Modifier.width(12.dp))
                Column {
                    Text(stringResource(R.string.app_name), style = MaterialTheme.typography.titleMedium)
                    Muted(stringResource(R.string.settings_version, "0.2"))
                }
            }
            Text(stringResource(R.string.settings_privacy), Modifier.padding(top = 10.dp), style = MaterialTheme.typography.bodySmall)
            Text(stringResource(R.string.disclaimer_medical), Modifier.padding(top = 8.dp), style = MaterialTheme.typography.labelMedium)
        }
    }
    if (showSetup) OnboardingDialog(status, onDone = { showSetup = false })
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
