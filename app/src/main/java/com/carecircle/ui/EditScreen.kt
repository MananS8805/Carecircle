package com.carecircle.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.carecircle.R
import com.carecircle.data.Medicine
import com.carecircle.domain.FoodTiming
import com.carecircle.domain.Schedule
import com.carecircle.domain.TimeList
import com.carecircle.ui.theme.BigButton
import com.carecircle.ui.theme.Muted
import com.carecircle.ui.theme.SoftButton

@Composable
fun AppField(
    value: String, onChange: (String) -> Unit, label: String, modifier: Modifier = Modifier,
    isError: Boolean = false, singleLine: Boolean = true,
) {
    OutlinedTextField(
        value, onChange, modifier.fillMaxWidth(), label = { Text(label) }, isError = isError, singleLine = singleLine,
        shape = MaterialTheme.shapes.medium,
    )
}

@Composable
fun EditScreen(vm: AppViewModel, id: Long, onScan: () -> Unit, onDone: () -> Unit) {
    var existing by remember { mutableStateOf<Medicine?>(null) }
    LaunchedEffect(id) { existing = if (id == 0L) null else vm.repo.medicines.get(id) }
    // rememberSaveable: the form survives opening the camera screen and coming back.
    var loaded by rememberSaveable { mutableStateOf(false) }
    var name by rememberSaveable { mutableStateOf("") }
    var dose by rememberSaveable { mutableStateOf("") }
    var times by rememberSaveable { mutableStateOf(if (id == 0L) "08:00" else "") }
    var food by rememberSaveable { mutableStateOf(FoodTiming.NONE.key) }
    var pills by rememberSaveable { mutableStateOf("30") }
    var perDose by rememberSaveable { mutableStateOf("1") }
    var salt by rememberSaveable { mutableStateOf("") }
    var patient by rememberSaveable { mutableStateOf("") }
    var scanMsg by rememberSaveable { mutableStateOf("") }
    LaunchedEffect(existing) {
        val e = existing
        if (e != null && !loaded) {
            name = e.name; dose = e.doseText; times = e.timesCsv; pills = e.pillCount.toString()
            perDose = e.pillsPerDose.toString(); salt = e.salt; patient = e.patientName; food = e.foodTiming; loaded = true
        }
    }

    val scan by vm.pendingScan.collectAsStateWithLifecycle()
    val scanRead = stringResource(R.string.scan_read, scan?.salt.orEmpty())
    val scanCheck = stringResource(R.string.scan_read_check, scan?.salt.orEmpty())
    LaunchedEffect(scan) {
        scan?.let { salt = it.salt; scanMsg = if (it.needsCheck) scanCheck else scanRead; vm.pendingScan.value = null }
    }

    val timesOk = TimeList.isValid(times)
    val valid = name.isNotBlank() && timesOk && pills.toIntOrNull() != null && perDose.toIntOrNull()?.let { it > 0 } == true

    Column(
        Modifier.fillMaxSize().statusBarsPadding().imePadding().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Text(
            if (id == 0L) stringResource(R.string.edit_add_title) else stringResource(R.string.edit_edit_title),
            style = MaterialTheme.typography.headlineLarge,
        )
        AppField(name, { name = it }, stringResource(R.string.field_name))
        AppField(dose, { dose = it }, stringResource(R.string.field_dose))
        TimesEditor(times, onChange = { times = it })
        FoodSelector(FoodTiming.fromKey(food), onChange = { food = it.key })
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            AppField(pills, { pills = it.filter(Char::isDigit) }, stringResource(R.string.field_pills), Modifier.weight(1f))
            AppField(perDose, { perDose = it.filter(Char::isDigit) }, stringResource(R.string.field_per_dose), Modifier.weight(1f))
        }
        AppField(salt, { salt = it }, stringResource(R.string.field_salt))
        SoftButton(stringResource(R.string.scan_strip), onScan, Modifier.fillMaxWidth(), icon = Icons.Default.CameraAlt)
        if (scanMsg.isNotEmpty()) Muted(scanMsg)
        BigButton(stringResource(R.string.action_save), {
            val base = existing ?: Medicine(name = "", doseText = "", timesCsv = "", pillCount = 0)
            vm.save(
                base.copy(
                    name = name.trim(), doseText = dose.trim(), timesCsv = Schedule.formatTimes(Schedule.parseTimes(times)),
                    pillCount = pills.toInt(), pillsPerDose = perDose.toInt(), salt = salt.trim(), patientName = patient.trim(), foodTiming = food,
                ),
            ) { onDone() }
        }, enabled = valid)
    }
}
