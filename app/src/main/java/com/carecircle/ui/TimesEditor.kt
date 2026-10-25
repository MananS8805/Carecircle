package com.carecircle.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.carecircle.R
import com.carecircle.domain.FoodTiming
import com.carecircle.domain.TimeList
import com.carecircle.ui.theme.Palette
import com.carecircle.ui.theme.SoftButton
import java.time.LocalTime

/** Time chips (tap the cross to remove) plus an "Add time" button that opens a clock picker. Stored as "HH:mm,HH:mm". */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun TimesEditor(csv: String, onChange: (String) -> Unit) {
    var picking by remember { mutableStateOf(false) }
    val times = TimeList.parse(csv)
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(stringResource(R.string.field_times_title), style = MaterialTheme.typography.titleMedium)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            times.forEach { t ->
                val label = Fmt.time(t)
                val removeDesc = stringResource(R.string.cd_remove_time, label)
                Row(
                    Modifier.heightIn(min = 48.dp).clip(CircleShape).background(Palette.OrangeSoft)
                        .semantics { contentDescription = label }.padding(start = 16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(label, style = MaterialTheme.typography.titleMedium)
                    IconButton(onClick = { onChange(TimeList.remove(csv, t)) }, Modifier.size(48.dp)) {
                        Icon(Icons.Default.Close, removeDesc, Modifier.size(18.dp))
                    }
                }
            }
        }
        if (times.isEmpty()) Text(stringResource(R.string.times_need_one), style = MaterialTheme.typography.bodySmall, color = Palette.Bad)
        SoftButton(stringResource(R.string.times_add), { picking = true }, Modifier.defaultMinSize(minHeight = 48.dp), icon = Icons.Default.Add)
    }
    if (picking) TimePickerDialog(
        initial = times.lastOrNull() ?: LocalTime.of(8, 0),
        onConfirm = { onChange(TimeList.add(csv, it)); picking = false },
        onDismiss = { picking = false },
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TimePickerDialog(initial: LocalTime, onConfirm: (LocalTime) -> Unit, onDismiss: () -> Unit) {
    val state = rememberTimePickerState(initial.hour, initial.minute, is24Hour = false)
    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = { TextButton(onClick = { onConfirm(LocalTime.of(state.hour, state.minute)) }) { Text(stringResource(R.string.action_ok)) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) } },
        text = { TimePicker(state) },
        containerColor = Palette.Surface,
    )
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun FoodSelector(selected: FoodTiming, onChange: (FoodTiming) -> Unit) {
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(stringResource(R.string.field_how_to_take), style = MaterialTheme.typography.titleMedium)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            FoodTiming.entries.forEach { f ->
                FilterChip(
                    selected = f == selected, onClick = { onChange(f) }, label = { Text(stringResource(f.labelRes())) },
                    modifier = Modifier.heightIn(min = 48.dp), shape = CircleShape,
                    colors = FilterChipDefaults.filterChipColors(selectedContainerColor = Palette.Ink, selectedLabelColor = Color.White),
                )
            }
        }
    }
}
