package com.carecircle.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.carecircle.R
import com.carecircle.data.Medicine
import com.carecircle.data.SymptomLog
import com.carecircle.domain.Expiry
import com.carecircle.domain.ExpiryStatus
import com.carecircle.ui.theme.BigButton
import com.carecircle.ui.theme.Muted
import com.carecircle.ui.theme.Palette
import com.carecircle.ui.theme.Pill
import com.carecircle.ui.theme.SoftButton
import com.carecircle.ui.theme.SoftCard
import java.time.LocalDate
import java.time.format.TextStyle
import java.util.Locale

/** "Expires Feb 2028", amber when it is within about two months, red once past. Null when no expiry is known. */
@Composable
fun ExpiryPill(expiry: String?, modifier: Modifier = Modifier, onlyWhenAttention: Boolean = false) {
    val ym = expiry?.let { Expiry.parseTyped(it) } ?: return
    val status = Expiry.status(ym, LocalDate.now())
    if (onlyWhenAttention && status == ExpiryStatus.OK) return
    val month = ym.month.getDisplayName(TextStyle.SHORT, Locale.getDefault()) + " " + ym.year
    when (status) {
        ExpiryStatus.EXPIRED -> Pill(stringResource(R.string.expiry_expired, month), Palette.BadSoft, Palette.Bad, modifier)
        ExpiryStatus.SOON -> Pill(stringResource(R.string.expiry_soon, month), Palette.WarnSoft, Palette.Warn, modifier)
        ExpiryStatus.OK -> Pill(stringResource(R.string.expiry_ok, month), Palette.GoodSoft, Palette.Good, modifier)
    }
}

/** What the doctor said for this person, shown above the generic information and kept on the phone. */
@Composable
fun DoctorNoteCard(m: Medicine, vm: AppViewModel, onSaved: (Medicine) -> Unit) {
    var editing by remember { mutableStateOf(false) }
    var text by remember(m.doctorNote) { mutableStateOf(m.doctorNote.orEmpty()) }
    SoftCard(Modifier.fillMaxWidth(), tint = Palette.OrangeSoft) {
        Text(stringResource(R.string.note_title), style = MaterialTheme.typography.titleLarge)
        if (editing) {
            OutlinedTextField(
                text, { text = it.take(500) }, Modifier.fillMaxWidth().padding(top = 8.dp), shape = MaterialTheme.shapes.medium,
                label = { Text(stringResource(R.string.note_hint)) }, minLines = 2,
            )
            Row(Modifier.padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                BigButton(stringResource(R.string.action_save), { vm.saveNote(m, text) { editing = false; onSaved(it) } }, Modifier.weight(1f))
                SoftButton(stringResource(R.string.action_cancel), { text = m.doctorNote.orEmpty(); editing = false })
            }
        } else {
            val note = m.doctorNote
            if (note.isNullOrBlank()) Muted(stringResource(R.string.note_empty), Modifier.padding(top = 4.dp))
            else Text(note, Modifier.padding(top = 4.dp), style = MaterialTheme.typography.bodyLarge)
            SoftButton(
                if (note.isNullOrBlank()) stringResource(R.string.note_add) else stringResource(R.string.action_edit),
                { editing = true }, Modifier.padding(top = 8.dp),
            )
        }
    }
}

/** "Information updated on ..." with a refresh button. The card is cached on the phone for offline use. */
@Composable
fun FreshnessRow(fetchedAt: Long, fromCache: Boolean, onRefresh: () -> Unit) {
    val date = java.text.DateFormat.getDateInstance(java.text.DateFormat.MEDIUM).format(java.util.Date(fetchedAt))
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(
            stringResource(if (fromCache) R.string.card_saved_on else R.string.card_updated_on, date),
            Modifier.weight(1f), style = MaterialTheme.typography.bodySmall, color = Palette.Muted,
        )
        TextButton(onClick = onRefresh) { Text(stringResource(R.string.card_refresh)) }
    }
}

private val symptomKeys = listOf("nausea", "dizziness", "headache", "rash", "sleepy", "stomach", "other")

@Composable
private fun symptomLabel(key: String): String = when (key) {
    "nausea" -> stringResource(R.string.sym_nausea)
    "dizziness" -> stringResource(R.string.sym_dizziness)
    "headache" -> stringResource(R.string.sym_headache)
    "rash" -> stringResource(R.string.sym_rash)
    "sleepy" -> stringResource(R.string.sym_sleepy)
    "stomach" -> stringResource(R.string.sym_stomach)
    "other" -> stringResource(R.string.sym_other)
    else -> key
}

@Composable
fun severityLabel(severity: Int): String = when (severity) {
    3 -> stringResource(R.string.sev_severe)
    2 -> stringResource(R.string.sev_moderate)
    else -> stringResource(R.string.sev_mild)
}

/** Quick log of side effects the person noticed. Kept on the phone and included in the doctor-visit PDF. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun DiaryCard(m: Medicine, vm: AppViewModel) {
    val entries by vm.symptoms(m.id).collectAsStateWithLifecycle(emptyList())
    var symptom by remember { mutableStateOf("") }
    var severity by remember { mutableIntStateOf(1) }
    var note by remember { mutableStateOf("") }
    var lastSevere by remember { mutableStateOf(false) }
    SoftCard(Modifier.fillMaxWidth()) {
        Text(stringResource(R.string.diary_title), style = MaterialTheme.typography.titleLarge)
        Muted(stringResource(R.string.diary_intro))
        FlowRow(Modifier.padding(top = 10.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            symptomKeys.forEach { k ->
                FilterChip(
                    selected = symptom == k, onClick = { symptom = if (symptom == k) "" else k }, label = { Text(symptomLabel(k)) },
                    modifier = Modifier.heightIn(min = 48.dp), shape = CircleShape,
                    colors = FilterChipDefaults.filterChipColors(selectedContainerColor = Palette.Ink, selectedLabelColor = Color.White),
                )
            }
        }
        if (symptom.isNotEmpty()) {
            Row(Modifier.padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                (1..3).forEach { s ->
                    FilterChip(
                        selected = severity == s, onClick = { severity = s }, label = { Text(severityLabel(s)) },
                        modifier = Modifier.heightIn(min = 48.dp), shape = CircleShape,
                        colors = FilterChipDefaults.filterChipColors(selectedContainerColor = Palette.Orange, selectedLabelColor = Color.White),
                    )
                }
            }
            OutlinedTextField(
                note, { note = it.take(200) }, Modifier.fillMaxWidth().padding(top = 8.dp), shape = MaterialTheme.shapes.medium, singleLine = true,
                label = { Text(stringResource(if (symptom == "other") R.string.diary_describe else R.string.diary_note)) },
            )
            BigButton(stringResource(R.string.diary_add), {
                val text = if (symptom == "other") note.ifBlank { symptom } else symptom
                vm.logSymptom(m.id, text, severity, if (symptom == "other") "" else note)
                lastSevere = severity == 3
                symptom = ""; severity = 1; note = ""
            }, Modifier.padding(top = 8.dp))
        }
        if (lastSevere) Text(stringResource(R.string.diary_severe_hint), Modifier.padding(top = 10.dp), style = MaterialTheme.typography.bodyLarge, color = Palette.Bad)
        entries.take(5).forEach { e -> DiaryRow(e, vm) }
    }
}

@Composable
private fun DiaryRow(e: SymptomLog, vm: AppViewModel) {
    val date = java.text.DateFormat.getDateInstance(java.text.DateFormat.MEDIUM).format(java.util.Date(e.loggedAt))
    Row(Modifier.fillMaxWidth().padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        androidx.compose.foundation.layout.Column(Modifier.weight(1f)) {
            Text(symptomLabel(e.symptom), style = MaterialTheme.typography.bodyLarge)
            Muted(listOf(date, e.note).filter { it.isNotBlank() }.joinToString("  -  "))
        }
        Pill(severityLabel(e.severity), if (e.severity == 3) Palette.BadSoft else Palette.Line, if (e.severity == 3) Palette.Bad else Palette.Ink)
        Spacer(Modifier.width(4.dp))
        TextButton(onClick = { vm.deleteSymptom(e.id) }) { Text(stringResource(R.string.action_delete), color = Palette.Muted) }
    }
}
