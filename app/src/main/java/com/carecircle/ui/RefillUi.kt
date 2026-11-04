package com.carecircle.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.carecircle.R
import com.carecircle.data.Medicine
import com.carecircle.data.Repository
import com.carecircle.domain.RefillRules
import com.carecircle.ui.theme.BigButton
import com.carecircle.ui.theme.Muted
import com.carecircle.ui.theme.Palette
import com.carecircle.ui.theme.SoftButton
import com.carecircle.ui.theme.SoftCard
import kotlinx.coroutines.delay
import java.text.DateFormat
import java.util.Date

/** "I bought more" sheet, refill history (last 5, See all) and an Undo line that disappears after a few seconds. */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun RefillSection(vm: AppViewModel, m: Medicine, onChanged: () -> Unit) {
    val events by vm.refillHistory(m.id).collectAsStateWithLifecycle(emptyList())
    var sheet by rememberSaveable { mutableStateOf(false) }
    var showAll by rememberSaveable { mutableStateOf(false) }
    var last by remember { mutableStateOf<Repository.RefillOutcome?>(null) }
    var message by remember { mutableStateOf<Int?>(null) }
    val avg = RefillRules.averageDaysBetween(events.map { it.date })
    LaunchedEffect(last, message) { if (last != null || message != null) { delay(9_000); last = null; message = null } }

    SoftCard(Modifier.fillMaxWidth()) {
        Text(stringResource(R.string.refill_history), style = MaterialTheme.typography.titleLarge)
        if (events.isEmpty()) Muted(stringResource(R.string.refill_none), Modifier.padding(top = 4.dp))
        val shown = if (showAll) events else events.take(5)
        shown.forEach { e ->
            Muted(
                stringResource(R.string.refill_line, DateFormat.getDateInstance(DateFormat.MEDIUM).format(Date(e.date)), e.quantityAdded) +
                    if (e.note.isNotBlank()) "  -  ${e.note}" else "",
                Modifier.padding(top = 4.dp),
            )
        }
        if (events.size > 5) TextButton(onClick = { showAll = !showAll }, Modifier.heightIn(min = 48.dp)) {
            Text(stringResource(if (showAll) R.string.refill_show_less else R.string.refill_see_all))
        }
        avg?.let { Muted(stringResource(R.string.refill_avg, it), Modifier.padding(top = 6.dp)) }
        BigButton(stringResource(R.string.bought_more), { sheet = true }, Modifier.padding(top = 10.dp))
        last?.let { out ->
            Row(Modifier.padding(top = 6.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(stringResource(R.string.bought_added, out.quantity), Modifier.padding(top = 12.dp), style = MaterialTheme.typography.bodyLarge)
                TextButton(onClick = { vm.undoRefill(out) { ok -> last = null; message = if (ok) R.string.bought_undone else R.string.bought_undo_blocked; onChanged() } }, Modifier.heightIn(min = 48.dp)) {
                    Text(stringResource(R.string.bought_undo))
                }
            }
        }
        message?.let { Muted(stringResource(it), Modifier.padding(top = 6.dp)) }
    }

    if (sheet) {
        var qty by rememberSaveable { mutableStateOf<Int?>(30) }
        var custom by rememberSaveable { mutableStateOf("") }
        var note by rememberSaveable { mutableStateOf("") }
        val amount = custom.toIntOrNull() ?: qty
        val valid = amount != null && RefillRules.validQuantity(amount)
        ModalBottomSheet(onDismissRequest = { sheet = false }, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true), containerColor = Palette.Surface) {
            Column(Modifier.padding(horizontal = 20.dp).padding(bottom = 20.dp).navigationBarsPadding(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(stringResource(R.string.bought_title, m.name), style = MaterialTheme.typography.titleLarge)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    RefillRules.QUICK_QUANTITIES.forEach { q ->
                        FilterChip(selected = custom.isBlank() && qty == q, onClick = { qty = q; custom = "" }, label = { Text("$q") }, modifier = Modifier.heightIn(min = 48.dp))
                    }
                }
                AppField(custom, { custom = it.filter(Char::isDigit).take(4) }, stringResource(R.string.bought_custom), isError = custom.isNotBlank() && !valid)
                AppField(note, { note = it.take(120) }, stringResource(R.string.bought_note))
                if (!valid) Text(stringResource(R.string.bought_invalid, RefillRules.MAX_QUANTITY), color = Palette.Bad)
                BigButton(stringResource(R.string.bought_save), {
                    val a = amount ?: return@BigButton
                    vm.boughtMore(m.id, a, note) { out -> last = out; message = if (out == null) R.string.bought_invalid_short else null; sheet = false; onChanged() }
                }, enabled = valid)
                SoftButton(stringResource(R.string.action_cancel), { sheet = false }, Modifier.fillMaxWidth())
            }
        }
    }
}
