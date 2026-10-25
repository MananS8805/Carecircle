package com.carecircle.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.carecircle.R
import com.carecircle.domain.ApptLite
import com.carecircle.domain.Appointments
import com.carecircle.ui.theme.BigButton
import com.carecircle.ui.theme.Muted
import com.carecircle.ui.theme.Palette
import com.carecircle.ui.theme.Pill
import com.carecircle.ui.theme.SoftButton
import com.carecircle.ui.theme.SoftCard
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

/** Doctor appointments with a reminder before each. Stays on the phone and is part of the backup. */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun AppointmentsScreen(vm: AppViewModel, onBack: () -> Unit) {
    val list by vm.appointments.collectAsStateWithLifecycle()
    val zone = ZoneId.systemDefault()
    var adding by rememberSaveable { mutableStateOf(false) }
    var title by rememberSaveable { mutableStateOf("") }
    var doctor by rememberSaveable { mutableStateOf("") }
    var notes by rememberSaveable { mutableStateOf("") }
    var dateIso by rememberSaveable { mutableStateOf("") }
    var timeIso by rememberSaveable { mutableStateOf("") }
    var remind by rememberSaveable { mutableIntStateOf(Appointments.DEFAULT_REMIND) }
    var pickDate by remember { mutableStateOf(false) }
    var pickTime by remember { mutableStateOf(false) }
    val now = System.currentTimeMillis()
    val ordered = Appointments.order(list.map { ApptLite(it.id, it.startsAt, it.remindBefore) }, now).mapNotNull { o -> list.firstOrNull { it.id == o.id } }
    val dateFmt = remember { DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM) }

    LazyColumn(Modifier.fillMaxSize().statusBarsPadding(), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item { TextButton(onClick = onBack) { Text(stringResource(R.string.action_back)) } }
        item { Text(stringResource(R.string.appt_title), style = MaterialTheme.typography.headlineLarge) }
        item { PersonSwitcher(vm, allowAll = false) }
        if (!adding) item { BigButton(stringResource(R.string.appt_add), { adding = true }, icon = Icons.Default.Add) }
        else item {
            SoftCard(Modifier.fillMaxWidth()) {
                OutlinedTextField(title, { title = it.take(80) }, Modifier.fillMaxWidth(), singleLine = true, shape = MaterialTheme.shapes.medium, label = { Text(stringResource(R.string.appt_field_title)) })
                OutlinedTextField(doctor, { doctor = it.take(80) }, Modifier.fillMaxWidth().padding(top = 8.dp), singleLine = true, shape = MaterialTheme.shapes.medium, label = { Text(stringResource(R.string.appt_field_doctor)) })
                Row(Modifier.padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    SoftButton(if (dateIso.isEmpty()) stringResource(R.string.appt_pick_date) else LocalDate.parse(dateIso).format(dateFmt), { pickDate = true })
                    SoftButton(if (timeIso.isEmpty()) stringResource(R.string.appt_pick_time) else Fmt.time(LocalTime.parse(timeIso)), { pickTime = true })
                }
                Text(stringResource(R.string.appt_remind), Modifier.padding(top = 10.dp), style = MaterialTheme.typography.titleMedium)
                FlowRow(Modifier.padding(top = 6.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Appointments.REMIND_CHOICES.forEach { m ->
                        FilterChip(
                            selected = remind == m, onClick = { remind = m },
                            label = { Text(stringResource(when (m) { 60 -> R.string.appt_remind_1h; 180 -> R.string.appt_remind_3h; else -> R.string.appt_remind_1d })) },
                            modifier = Modifier.heightIn(min = 48.dp), shape = CircleShape,
                            colors = FilterChipDefaults.filterChipColors(selectedContainerColor = Palette.Ink, selectedLabelColor = Color.White),
                        )
                    }
                }
                OutlinedTextField(notes, { notes = it.take(300) }, Modifier.fillMaxWidth().padding(top = 8.dp), shape = MaterialTheme.shapes.medium, label = { Text(stringResource(R.string.appt_field_notes)) }, minLines = 2)
                Row(Modifier.padding(top = 10.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    BigButton(stringResource(R.string.action_save), {
                        val at = LocalDate.parse(dateIso).atTime(LocalTime.parse(timeIso)).atZone(zone).toInstant().toEpochMilli()
                        vm.saveAppointment(title, doctor, at, notes, remind)
                        adding = false; title = ""; doctor = ""; notes = ""; dateIso = ""; timeIso = ""
                    }, Modifier.weight(1f), enabled = title.isNotBlank() && dateIso.isNotEmpty() && timeIso.isNotEmpty())
                    SoftButton(stringResource(R.string.action_cancel), { adding = false })
                }
            }
        }
        if (ordered.isEmpty()) item { Muted(stringResource(R.string.appt_empty)) }
        items(ordered, key = { it.id }) { a ->
            val at = Instant.ofEpochMilli(a.startsAt).atZone(zone)
            val upcoming = a.startsAt >= now
            SoftCard(Modifier.fillMaxWidth()) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(a.title, style = MaterialTheme.typography.titleLarge)
                        Muted(listOf(a.doctor, at.toLocalDate().format(dateFmt) + "  " + Fmt.time(a.startsAt)).filter { it.isNotBlank() }.joinToString("  -  "))
                    }
                    Pill(stringResource(if (upcoming) R.string.appt_upcoming else R.string.appt_past), if (upcoming) Palette.OrangeSoft else Palette.Line, if (upcoming) Palette.Ink else Palette.Muted)
                }
                if (a.notes.isNotBlank()) Text(a.notes, Modifier.padding(top = 6.dp), style = MaterialTheme.typography.bodyMedium)
                TextButton(onClick = { vm.deleteAppointment(a.id) }) { Text(stringResource(R.string.action_delete), color = Palette.Bad) }
            }
        }
    }

    if (pickDate) {
        val state = rememberDatePickerState()
        DatePickerDialog(
            onDismissRequest = { pickDate = false },
            confirmButton = { TextButton(onClick = {
                state.selectedDateMillis?.let { dateIso = Instant.ofEpochMilli(it).atZone(ZoneOffset.UTC).toLocalDate().toString() }
                pickDate = false
            }) { Text(stringResource(R.string.action_ok)) } },
            dismissButton = { TextButton(onClick = { pickDate = false }) { Text(stringResource(R.string.action_cancel)) } },
        ) { DatePicker(state) }
    }
    if (pickTime) TimePickerDialog(
        initial = if (timeIso.isEmpty()) LocalTime.of(10, 0) else LocalTime.parse(timeIso),
        onConfirm = { timeIso = it.toString(); pickTime = false }, onDismiss = { pickTime = false },
    )
}
