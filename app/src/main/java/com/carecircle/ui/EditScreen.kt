package com.carecircle.ui

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import com.carecircle.R
import com.carecircle.data.Medicine
import com.carecircle.domain.FoodTiming
import com.carecircle.domain.SaltParser
import com.carecircle.domain.Schedule
import com.carecircle.domain.TimeList
import com.carecircle.ui.theme.BigButton
import com.carecircle.ui.theme.Muted
import com.carecircle.ui.theme.SoftButton
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import java.io.File

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
fun EditScreen(vm: AppViewModel, id: Long, onDone: () -> Unit) {
    val ctx = LocalContext.current
    val existing by produceState<Medicine?>(null, id) { value = if (id == 0L) null else vm.repo.medicines.get(id) }
    var loaded by remember { mutableStateOf(false) }
    var name by remember { mutableStateOf("") }
    var dose by remember { mutableStateOf("") }
    var food by remember { mutableStateOf(FoodTiming.NONE) }
    var times by remember { mutableStateOf(if (id == 0L) "08:00" else "") }
    var pills by remember { mutableStateOf("30") }
    var perDose by remember { mutableStateOf("1") }
    var salt by remember { mutableStateOf("") }
    var patient by remember { mutableStateOf("") }
    var scanMsg by remember { mutableStateOf("") }
    LaunchedEffect(existing) {
        val e = existing
        if (e != null && !loaded) {
            name = e.name; dose = e.doseText; times = e.timesCsv; pills = e.pillCount.toString()
            perDose = e.pillsPerDose.toString(); salt = e.salt; patient = e.patientName; food = FoodTiming.fromKey(e.foodTiming); loaded = true
        }
    }

    // System camera -> FileProvider URI -> on-device ML Kit OCR. No image leaves the phone.
    var photoFile by remember { mutableStateOf<File?>(null) }
    val camera = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { ok ->
        val f = photoFile
        if (ok && f != null) {
            val img = InputImage.fromFilePath(ctx, Uri.fromFile(f))
            TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS).process(img)
                .addOnSuccessListener { r ->
                    val found = SaltParser.parse(r.text)
                    if (found.isEmpty()) scanMsg = ctx.getString(R.string.scan_failed_retry)
                    else { salt = found.joinToString(" + "); scanMsg = ctx.getString(R.string.scan_read, found.joinToString(", ")) }
                }
                .addOnFailureListener { scanMsg = ctx.getString(R.string.scan_error) }
                .addOnCompleteListener { f.delete() }
        }
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
        FoodSelector(food, onChange = { food = it })
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            AppField(pills, { pills = it.filter(Char::isDigit) }, stringResource(R.string.field_pills), Modifier.weight(1f))
            AppField(perDose, { perDose = it.filter(Char::isDigit) }, stringResource(R.string.field_per_dose), Modifier.weight(1f))
        }
        AppField(patient, { patient = it }, stringResource(R.string.field_patient))
        AppField(salt, { salt = it }, stringResource(R.string.field_salt))
        SoftButton(stringResource(R.string.scan_strip), {
            val f = File.createTempFile("strip", ".jpg", ctx.cacheDir); photoFile = f
            camera.launch(FileProvider.getUriForFile(ctx, "${ctx.packageName}.files", f))
        }, icon = Icons.Default.CameraAlt)
        if (scanMsg.isNotEmpty()) Muted(scanMsg)
        BigButton(stringResource(R.string.action_save), {
            val base = existing ?: Medicine(name = "", doseText = "", timesCsv = "", pillCount = 0)
            vm.save(
                base.copy(
                    name = name.trim(), doseText = dose.trim(), timesCsv = Schedule.formatTimes(Schedule.parseTimes(times)),
                    pillCount = pills.toInt(), pillsPerDose = perDose.toInt(), salt = salt.trim(), patientName = patient.trim(), foodTiming = food.key,
                ),
            ) { onDone() }
        }, enabled = valid)
    }
}
