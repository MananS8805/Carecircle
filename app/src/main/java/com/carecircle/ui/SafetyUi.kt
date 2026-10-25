package com.carecircle.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.InputChip
import androidx.compose.material3.InputChipDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
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
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.carecircle.R
import com.carecircle.domain.MedLite
import com.carecircle.domain.Safety
import com.carecircle.ui.theme.Muted
import com.carecircle.ui.theme.Palette
import com.carecircle.ui.theme.Pill
import com.carecircle.ui.theme.SoftButton
import com.carecircle.ui.theme.SoftCard

/** Allergies of one person: chips to remove, a field to add. Used on the Manage people screen. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun AllergySection(personId: Long, vm: AppViewModel) {
    val flow = remember(personId) { vm.allergiesFor(personId) }
    val list by flow.collectAsStateWithLifecycle(emptyList())
    var text by remember(personId) { mutableStateOf("") }
    Column(Modifier.fillMaxWidth().padding(top = 12.dp)) {
        Text(stringResource(R.string.allergy_title), style = MaterialTheme.typography.titleMedium)
        if (list.isEmpty()) Muted(stringResource(R.string.allergy_none))
        FlowRow(Modifier.padding(top = 6.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            list.forEach { a ->
                InputChip(
                    selected = false, onClick = { vm.removeAllergy(a.id) }, label = { Text(a.name) },
                    modifier = Modifier.heightIn(min = 48.dp), shape = CircleShape,
                    trailingIcon = { Text("x", color = Palette.Muted, modifier = Modifier.padding(end = 6.dp)) },
                    colors = InputChipDefaults.inputChipColors(containerColor = Palette.BadSoft),
                )
            }
        }
        Row(Modifier.fillMaxWidth().padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(
                text, { text = it.take(60) }, Modifier.weight(1f), singleLine = true, shape = MaterialTheme.shapes.medium,
                label = { Text(stringResource(R.string.allergy_hint)) },
            )
            SoftButton(stringResource(R.string.allergy_add), { vm.addAllergy(personId, text); text = "" }, enabled = text.isNotBlank())
        }
    }
}

/** Red notice cards for allergy matches, shown on the Edit and Detail screens. Never blocks saving. */
@Composable
fun AllergyWarnings(med: MedLite, personName: String, allergies: List<String>) {
    val hits = Safety.allergyMatches(med, allergies)
    hits.forEach { a ->
        SoftCard(Modifier.fillMaxWidth(), tint = Palette.BadSoft) {
            Text(stringResource(R.string.warn_allergy, a, personName, med.name.ifBlank { med.salt }), style = MaterialTheme.typography.bodyLarge, color = Palette.Bad)
        }
    }
}

/** Notice about an ingredient that is already in another medicine. */
@Composable
fun OverlapWarning(candidate: MedLite, others: List<MedLite>) {
    Safety.overlapsWith(candidate, others).forEach { (ingredient, names) ->
        SoftCard(Modifier.fillMaxWidth(), tint = Palette.WarnSoft) {
            Text(stringResource(R.string.warn_overlap, ingredient, names.joinToString(", ")), style = MaterialTheme.typography.bodyLarge)
        }
    }
}

@Composable
fun HighAlertPill(med: MedLite, modifier: Modifier = Modifier) {
    if (Safety.isHighAlert(med)) Pill(stringResource(R.string.high_alert_pill), Palette.BadSoft, Palette.Bad, modifier)
}
