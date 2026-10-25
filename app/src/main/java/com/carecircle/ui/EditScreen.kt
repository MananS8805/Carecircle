package com.carecircle.ui

import android.app.Activity
import android.content.Intent
import android.speech.RecognizerIntent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.carecircle.AppLocale
import com.carecircle.R
import com.carecircle.data.Medicine
import com.carecircle.domain.Condition
import com.carecircle.domain.Conditions
import com.carecircle.domain.Expiry
import com.carecircle.domain.FoodTiming
import com.carecircle.domain.Schedule
import com.carecircle.domain.TimeList
import com.carecircle.domain.VoiceParser
import com.carecircle.ui.theme.BigButton
import com.carecircle.ui.theme.Muted
import com.carecircle.ui.theme.Palette
import com.carecircle.ui.theme.SoftButton
import java.util.Locale

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
private fun CheckThis(show: Boolean) {
    if (show) Text(stringResource(R.string.voice_check_this), style = MaterialTheme.typography.labelMedium, color = Palette.Orange)
}

@Composable
fun EditScreen(vm: AppViewModel, id: Long, onScan: () -> Unit, onDone: () -> Unit) {
    val ctx = LocalContext.current
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
    var tag by rememberSaveable { mutableStateOf("") }
    var expiryText by rememberSaveable { mutableStateOf("") }
    var scanMsg by rememberSaveable { mutableStateOf("") }
    var heard by rememberSaveable { mutableStateOf("") }
    var voiceFilled by rememberSaveable { mutableStateOf("") } // comma-separated field names filled by voice
    var voiceNote by rememberSaveable { mutableStateOf("") }
    var askVoice by remember { mutableStateOf(false) }
    LaunchedEffect(existing) {
        val e = existing
        if (e != null && !loaded) {
            name = e.name; dose = e.doseText; times = e.timesCsv; pills = e.pillCount.toString()
            perDose = e.pillsPerDose.toString(); salt = e.salt; tag = e.conditionTag.orEmpty(); food = e.foodTiming
            expiryText = e.expiry?.let { s -> Expiry.parseTyped(s)?.let { ym -> "%02d/%d".format(ym.monthValue, ym.year) } }.orEmpty(); loaded = true
        }
    }

    val scan by vm.pendingScan.collectAsStateWithLifecycle()
    val scanRead = stringResource(R.string.scan_read, scan?.salt.orEmpty())
    val scanCheck = stringResource(R.string.scan_read_check, scan?.salt.orEmpty())
    LaunchedEffect(scan) {
        scan?.let { r -> r.expiry?.let { x -> Expiry.parseTyped(x)?.let { ym -> expiryText = "%02d/%d".format(ym.monthValue, ym.year) } } }
        scan?.let { salt = it.salt; scanMsg = if (it.needsCheck) scanCheck else scanRead; vm.pendingScan.value = null }
    }

    // Voice entry: the phone's own speech service listens; CareCircle only receives the text. Nothing is saved until Save.
    val voice = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { res ->
        val text = res.data?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)?.firstOrNull()
        if (res.resultCode == Activity.RESULT_OK && !text.isNullOrBlank()) {
            val d = VoiceParser.parseVoiceMedicine(text)
            val filled = mutableListOf<String>()
            if (d.name.isNotEmpty()) { name = d.name; filled += "name" }
            if (d.dose.isNotEmpty()) { dose = d.dose; filled += "dose" }
            if (d.times.isNotEmpty()) { times = TimeList.format(d.times); filled += "times" }
            d.foodTiming?.let { food = it.key; filled += "food" }
            voiceFilled = filled.joinToString(",")
            heard = text
            voiceNote = if (filled.isEmpty()) ctx.getString(R.string.voice_nothing) else ""
        }
    }
    fun startVoice() {
        val tagFor = when (AppLocale.locale(ctx).language) { "hi" -> "hi-IN"; "gu" -> "gu-IN"; "en" -> "en-IN"; else -> Locale.getDefault().toLanguageTag() }
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH)
            .putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            .putExtra(RecognizerIntent.EXTRA_LANGUAGE, tagFor)
            .putExtra(RecognizerIntent.EXTRA_PROMPT, ctx.getString(R.string.voice_prompt))
        if (intent.resolveActivity(ctx.packageManager) == null) voiceNote = ctx.getString(R.string.voice_unavailable)
        else runCatching { voice.launch(intent) }.onFailure { voiceNote = ctx.getString(R.string.voice_unavailable) }
    }

    val timesOk = TimeList.isValid(times)
    val expiryOk = expiryText.isBlank() || Expiry.parseTyped(expiryText) != null
    val valid = expiryOk && name.isNotBlank() && timesOk && pills.toIntOrNull() != null && perDose.toIntOrNull()?.let { it > 0 } == true
    val filledSet = voiceFilled.split(',').filter { it.isNotEmpty() }.toSet()

    Column(
        Modifier.fillMaxSize().statusBarsPadding().imePadding().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Text(
            if (id == 0L) stringResource(R.string.edit_add_title) else stringResource(R.string.edit_edit_title),
            style = MaterialTheme.typography.headlineLarge,
        )
        if (id == 0L) {
            SoftButton(stringResource(R.string.voice_button), { askVoice = true }, Modifier.fillMaxWidth(), icon = Icons.Default.Mic)
            if (heard.isNotEmpty()) Muted(stringResource(R.string.voice_heard, heard))
            if (voiceNote.isNotEmpty()) Muted(voiceNote)
        }
        AppField(name, { name = it; voiceFilled = (filledSet - "name").joinToString(",") }, stringResource(R.string.field_name))
        CheckThis("name" in filledSet)
        AppField(dose, { dose = it; voiceFilled = (filledSet - "dose").joinToString(",") }, stringResource(R.string.field_dose))
        CheckThis("dose" in filledSet)
        TimesEditor(times, onChange = { times = it })
        CheckThis("times" in filledSet)
        FoodSelector(FoodTiming.fromKey(food), onChange = { food = it.key })
        CheckThis("food" in filledSet)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            AppField(pills, { pills = it.filter(Char::isDigit) }, stringResource(R.string.field_pills), Modifier.weight(1f))
            AppField(perDose, { perDose = it.filter(Char::isDigit) }, stringResource(R.string.field_per_dose), Modifier.weight(1f))
        }
        ConditionPicker(tag, onChange = { tag = it.take(60) })
        AppField(salt, { salt = it }, stringResource(R.string.field_salt))
        AppField(expiryText, { expiryText = it.take(7) }, stringResource(R.string.field_expiry), isError = expiryText.isNotBlank() && Expiry.parseTyped(expiryText) == null)
        SoftButton(stringResource(R.string.scan_strip), onScan, Modifier.fillMaxWidth(), icon = Icons.Default.CameraAlt)
        if (scanMsg.isNotEmpty()) Muted(scanMsg)
        BigButton(stringResource(R.string.action_save), {
            val base = existing ?: Medicine(name = "", doseText = "", timesCsv = "", pillCount = 0)
            vm.save(
                base.copy(
                    name = name.trim(), doseText = dose.trim(), timesCsv = Schedule.formatTimes(Schedule.parseTimes(times)),
                    pillCount = pills.toInt(), pillsPerDose = perDose.toInt(), salt = salt.trim(), foodTiming = food,
                    conditionTag = tag.trim().ifEmpty { null },
                    expiry = Expiry.parseTyped(expiryText)?.let { Expiry.format(it) },
                ),
            ) { onDone() }
        }, enabled = valid)
    }

    if (askVoice) AlertDialog(
        onDismissRequest = { askVoice = false },
        title = { Text(stringResource(R.string.voice_button)) },
        text = { Text(stringResource(R.string.voice_rationale)) },
        confirmButton = { TextButton(onClick = { askVoice = false; startVoice() }) { Text(stringResource(R.string.voice_start)) } },
        dismissButton = { TextButton(onClick = { askVoice = false }) { Text(stringResource(R.string.action_cancel)) } },
        containerColor = Palette.Surface,
    )
}

/** "What is it for?": chips for the common conditions plus free text. Stays on the phone; used for the organ view and policy notes. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ConditionPicker(tag: String, onChange: (String) -> Unit) {
    val chip = Conditions.fromTag(tag)
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(stringResource(R.string.field_for), style = MaterialTheme.typography.titleMedium)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Condition.entries.forEach { c ->
                FilterChip(
                    selected = chip == c, onClick = { onChange(if (chip == c) "" else c.label) },
                    label = { Text(c.label()) }, modifier = Modifier.heightIn(min = 48.dp), shape = CircleShape,
                    colors = FilterChipDefaults.filterChipColors(selectedContainerColor = Palette.Ink, selectedLabelColor = Color.White),
                )
            }
        }
        AppField(if (chip == null) tag else "", { onChange(it) }, stringResource(R.string.field_for_custom))
    }
}
