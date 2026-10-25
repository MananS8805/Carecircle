package com.carecircle.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.carecircle.R
import com.carecircle.domain.AdherenceResult
import com.carecircle.domain.DayStat
import com.carecircle.domain.DoseLine
import com.carecircle.domain.DoseOutcome
import com.carecircle.ui.theme.Muted
import com.carecircle.ui.theme.Palette
import com.carecircle.ui.theme.Pill
import com.carecircle.ui.theme.SoftButton
import com.carecircle.ui.theme.SoftCard
import java.time.LocalDate
import java.time.format.TextStyle
import java.util.Locale

/** Per-person adherence for the last 7 or 30 days. Computed on the phone; nothing here uses the network. */
@Composable
fun HistoryScreen(vm: AppViewModel, onBack: () -> Unit) {
    val result by vm.history.collectAsStateWithLifecycle()
    val days by vm.historyDays.collectAsStateWithLifecycle()
    val scope by vm.scopePerson.collectAsStateWithLifecycle()
    var selected by remember { mutableStateOf(LocalDate.now()) }
    LaunchedEffect(scope?.id, days) { vm.loadHistory(days) }

    LazyColumn(
        Modifier.fillMaxSize().statusBarsPadding(),
        contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item { TextButton(onClick = onBack) { Text(stringResource(R.string.action_back)) } }
        item { Text(stringResource(R.string.hist_title), style = MaterialTheme.typography.headlineLarge) }
        item { PersonSwitcher(vm, allowAll = false) }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(7, 30).forEach { d ->
                    FilterChip(
                        selected = days == d, onClick = { vm.loadHistory(d) },
                        label = { Text(stringResource(if (d == 7) R.string.hist_7 else R.string.hist_30)) },
                        modifier = Modifier.heightIn(min = 48.dp), shape = CircleShape,
                        colors = FilterChipDefaults.filterChipColors(selectedContainerColor = Palette.Ink, selectedLabelColor = Color.White),
                    )
                }
            }
        }
        val r = result
        if (r == null) item { Muted(stringResource(R.string.info_loading)) }
        else {
            item { SummaryCard(r) }
            if (r.percent != null) {
                item { DayBars(r, selected) { selected = it } }
                item { DayDetails(r, selected) }
                item { Text(stringResource(R.string.hist_by_medicine), style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(top = 4.dp)) }
                items(r.perMedicine, key = { it.medicineId }) { m ->
                    SoftCard(Modifier.fillMaxWidth()) {
                        Text(listOf(m.name, m.doseText).filter { it.isNotBlank() }.joinToString(" "), style = MaterialTheme.typography.titleMedium)
                        Muted(stringResource(R.string.pdf_med_counts, m.taken, m.missed, m.skipped))
                    }
                }
                item { SoftButton(stringResource(R.string.hist_share), { vm.shareWeeklySummary() }, Modifier.fillMaxWidth(), icon = Icons.Default.Share) }
            }
        }
    }
}

@Composable
private fun SummaryCard(r: AdherenceResult) {
    SoftCard(Modifier.fillMaxWidth()) {
        val pct = r.percent
        if (pct == null) {
            Text(stringResource(R.string.hist_no_data), style = MaterialTheme.typography.titleMedium)
            Muted(stringResource(R.string.hist_no_data_hint))
        } else {
            Text("$pct%", fontSize = 56.sp, fontWeight = FontWeight.Light, color = if (pct >= 80) Palette.Good else if (pct >= 50) Palette.Warn else Palette.Bad)
            Text(stringResource(R.string.hist_summary, r.taken, r.decided, pct), style = MaterialTheme.typography.titleMedium)
            if (r.pending > 0) Muted(stringResource(R.string.hist_pending_note, r.pending))
        }
    }
}

@Composable
private fun DayBars(r: AdherenceResult, selected: LocalDate, onSelect: (LocalDate) -> Unit) {
    val maxTotal = (r.days.maxOfOrNull { it.total } ?: 1).coerceAtLeast(1)
    val locale = Locale.getDefault()
    SoftCard(Modifier.fillMaxWidth()) {
        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            r.days.forEach { d ->
                Column(
                    Modifier.clip(RoundedCornerShape(10.dp)).clickable { onSelect(d.date) }
                        .background(if (d.date == selected) Palette.OrangeSoft else Color.Transparent).padding(horizontal = 6.dp, vertical = 4.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Bar(d, maxTotal)
                    Text(d.date.dayOfWeek.getDisplayName(TextStyle.NARROW, locale), style = MaterialTheme.typography.labelMedium, color = Palette.Muted)
                    Text("${d.date.dayOfMonth}", style = MaterialTheme.typography.labelMedium)
                }
            }
        }
    }
}

@Composable
private fun Bar(d: DayStat, maxTotal: Int) {
    val h = 90.dp
    Column(Modifier.width(20.dp).height(h), verticalArrangement = Arrangement.Bottom) {
        if (d.total == 0) { Box(Modifier.fillMaxWidth().height(2.dp).background(Palette.Line)); return@Column }
        @Composable fun seg(n: Int, c: Color) { if (n > 0) Box(Modifier.fillMaxWidth().height(h * n / maxTotal).background(c)) }
        Column(Modifier.clip(RoundedCornerShape(6.dp))) {
            seg(d.pending, Palette.Line); seg(d.missed, Palette.Bad); seg(d.skipped, Palette.Muted); seg(d.taken, Palette.Good)
        }
    }
}

@Composable
private fun DayDetails(r: AdherenceResult, date: LocalDate) {
    val lines = r.linesOn(date)
    SoftCard(Modifier.fillMaxWidth()) {
        Text(date.format(java.time.format.DateTimeFormatter.ofLocalizedDate(java.time.format.FormatStyle.MEDIUM)), style = MaterialTheme.typography.titleMedium)
        if (lines.isEmpty()) Muted(stringResource(R.string.hist_day_empty))
        lines.forEach { DoseRow(it) }
    }
}

@Composable
private fun DoseRow(l: DoseLine) {
    val (label, bg, fg) = when (l.outcome) {
        DoseOutcome.TAKEN -> Triple(stringResource(R.string.status_taken), Palette.GoodSoft, Palette.Good)
        DoseOutcome.MISSED -> Triple(stringResource(R.string.status_missed), Palette.BadSoft, Palette.Bad)
        DoseOutcome.SKIPPED -> Triple(stringResource(R.string.status_skipped), Palette.Line, Palette.Muted)
        DoseOutcome.PENDING -> Triple(stringResource(R.string.status_upcoming), Palette.Line, Palette.Muted)
    }
    Row(Modifier.fillMaxWidth().padding(top = 10.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(l.name, style = MaterialTheme.typography.bodyLarge)
            val taken = l.takenAt?.let { stringResource(R.string.hist_taken_at, Fmt.time(it)) }
            Muted(Fmt.time(l.scheduledAt) + (taken?.let { "  -  $it" } ?: ""))
        }
        Pill(label, bg, fg)
    }
    Spacer(Modifier.height(0.dp))
}
