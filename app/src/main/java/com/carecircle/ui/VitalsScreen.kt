package com.carecircle.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.carecircle.R
import com.carecircle.data.VitalLog
import com.carecircle.domain.VitalKind
import com.carecircle.domain.Vitals
import com.carecircle.ui.theme.BigButton
import com.carecircle.ui.theme.Muted
import com.carecircle.ui.theme.Palette
import com.carecircle.ui.theme.SoftCard
import java.text.DateFormat
import java.util.Date

@Composable
fun vitalLabel(k: VitalKind): String = stringResource(
    when (k) {
        VitalKind.BP -> R.string.vital_bp
        VitalKind.SUGAR -> R.string.vital_sugar
        VitalKind.PULSE -> R.string.vital_pulse
        VitalKind.WEIGHT -> R.string.vital_weight
        VitalKind.TEMP -> R.string.vital_temp
    },
)

/** Record readings the person measured and see their trend. The app never calls a reading normal or abnormal. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun VitalsScreen(vm: AppViewModel, onBack: () -> Unit) {
    var kind by rememberSaveable { mutableStateOf(VitalKind.BP.key) }
    val k = VitalKind.fromKey(kind) ?: VitalKind.BP
    val flow = remember(k) { vm.vitals(k.key) }
    val readings by flow.collectAsStateWithLifecycle(emptyList())
    var a by rememberSaveable(k.key) { mutableStateOf("") }
    var b by rememberSaveable(k.key) { mutableStateOf("") }
    var note by rememberSaveable(k.key) { mutableStateOf("") }
    var bad by remember { mutableStateOf(false) }
    val oldestFirst = readings.take(30).reversed()
    val stat = Vitals.stats(oldestFirst.map { it.v1 to it.v2 })
    val dateFmt = remember { DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT) }

    LazyColumn(Modifier.fillMaxSize().statusBarsPadding(), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item { TextButton(onClick = onBack) { Text(stringResource(R.string.action_back)) } }
        item { Text(stringResource(R.string.vitals_title), style = MaterialTheme.typography.headlineLarge) }
        item { PersonSwitcher(vm, allowAll = false) }
        item {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                VitalKind.entries.forEach { e ->
                    FilterChip(
                        selected = e == k, onClick = { kind = e.key; bad = false }, label = { Text(vitalLabel(e)) },
                        modifier = Modifier.heightIn(min = 48.dp), shape = CircleShape,
                        colors = FilterChipDefaults.filterChipColors(selectedContainerColor = Palette.Ink, selectedLabelColor = Color.White),
                    )
                }
            }
        }
        item {
            SoftCard(Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.vitals_add, vitalLabel(k)), style = MaterialTheme.typography.titleMedium)
                Row(Modifier.padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        a, { a = it.take(6); bad = false }, Modifier.weight(1f), singleLine = true, shape = MaterialTheme.shapes.medium,
                        label = { Text(if (k.twoValues) stringResource(R.string.vitals_top) else k.unit) }, isError = bad,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    )
                    if (k.twoValues) OutlinedTextField(
                        b, { b = it.take(6); bad = false }, Modifier.weight(1f), singleLine = true, shape = MaterialTheme.shapes.medium,
                        label = { Text(stringResource(R.string.vitals_bottom)) }, isError = bad,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    )
                }
                if (k.twoValues) Muted(k.unit, Modifier.padding(top = 2.dp))
                OutlinedTextField(
                    note, { note = it.take(120) }, Modifier.fillMaxWidth().padding(top = 8.dp), singleLine = true, shape = MaterialTheme.shapes.medium,
                    label = { Text(stringResource(R.string.diary_note)) },
                )
                if (bad) Text(stringResource(R.string.vitals_check_value), Modifier.padding(top = 6.dp), color = Palette.Bad, style = MaterialTheme.typography.bodyMedium)
                BigButton(stringResource(R.string.vitals_save), {
                    val v1 = Vitals.parse(a); val v2 = if (k.twoValues) Vitals.parse(b) else null
                    if (Vitals.isPlausible(k, v1, v2)) { vm.logVital(k.key, v1!!, v2, note); a = ""; b = ""; note = "" } else bad = true
                }, Modifier.padding(top = 10.dp), enabled = a.isNotBlank())
            }
        }
        if (stat != null) item {
            SoftCard(Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.vitals_trend), style = MaterialTheme.typography.titleMedium)
                Chart(oldestFirst, Modifier.fillMaxWidth().height(150.dp).padding(top = 8.dp))
                val last = Vitals.format(k, stat.latest1, stat.latest2)
                val avg = Vitals.format(k, stat.avg1, stat.avg2)
                Text(stringResource(R.string.vitals_stats, last, avg, stat.count, k.unit), Modifier.padding(top = 8.dp), style = MaterialTheme.typography.bodyMedium)
                Muted(stringResource(R.string.vitals_disclaimer), Modifier.padding(top = 4.dp))
            }
        }
        if (readings.isEmpty()) item { Muted(stringResource(R.string.vitals_empty)) }
        items(readings.take(30), key = { it.id }) { r ->
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("${Vitals.format(k, r.v1, r.v2)} ${k.unit}", style = MaterialTheme.typography.titleMedium)
                    Muted(listOf(dateFmt.format(Date(r.measuredAt)), r.note).filter { it.isNotBlank() }.joinToString("  -  "))
                }
                TextButton(onClick = { vm.deleteVital(r.id) }) { Text(stringResource(R.string.action_delete), color = Palette.Muted) }
            }
        }
    }
}

/** A simple line chart of the readings, oldest on the left. Blood pressure draws two lines. */
@Composable
private fun Chart(points: List<VitalLog>, modifier: Modifier) {
    val orange = Palette.Orange
    val ink = Palette.Ink
    val line = Palette.Line
    Canvas(modifier) {
        if (points.isEmpty()) return@Canvas
        val values = points.flatMap { listOfNotNull(it.v1, it.v2) }
        val lo = values.min(); val hi = values.max()
        val span = (hi - lo).takeIf { it > 0.0 } ?: 1.0
        val pad = 8.dp.toPx()
        val w = size.width - 2 * pad; val h = size.height - 2 * pad
        fun x(i: Int) = pad + if (points.size == 1) w / 2 else w * i / (points.size - 1)
        fun y(v: Double) = pad + h - (h * ((v - lo) / span)).toFloat()
        drawLine(line, Offset(pad, size.height - pad), Offset(size.width - pad, size.height - pad), 2f)
        fun series(sel: (VitalLog) -> Double?, color: Color) {
            val path = Path(); var started = false
            points.forEachIndexed { i, p -> val v = sel(p) ?: return@forEachIndexed; if (!started) { path.moveTo(x(i), y(v)); started = true } else path.lineTo(x(i), y(v)) }
            drawPath(path, color, style = Stroke(5f, cap = StrokeCap.Round))
            points.forEachIndexed { i, p -> sel(p)?.let { drawCircle(color, 7f, Offset(x(i), y(it))) } }
        }
        series({ it.v1 }, orange)
        if (points.any { it.v2 != null }) series({ it.v2 }, ink)
    }
}
