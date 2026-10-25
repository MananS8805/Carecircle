package com.carecircle.ui

import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.FileProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.carecircle.data.CardResult
import com.carecircle.data.Medicine
import com.carecircle.domain.Messages
import com.carecircle.domain.Refill
import com.carecircle.domain.SaltParser
import com.carecircle.domain.Schedule
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import java.io.File
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

private const val DISCLAIMER = "Informational only, not medical advice."

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CareCircleApp(vm: AppViewModel) {
    val nav = rememberNavController()
    val route = nav.currentBackStackEntryAsState().value?.destination?.route
    val tabs = listOf(Triple("today", "Today", Icons.Default.Alarm), Triple("meds", "Medicines", Icons.Default.Medication), Triple("settings", "Settings", Icons.Default.Settings))
    Scaffold(
        bottomBar = {
            NavigationBar {
                tabs.forEach { (r, label, icon) ->
                    NavigationBarItem(selected = route == r, onClick = { nav.navigate(r) { launchSingleTop = true; popUpTo("today") } },
                        icon = { Icon(icon, label) }, label = { Text(label) })
                }
            }
        },
    ) { pad ->
        NavHost(nav, startDestination = "today", modifier = Modifier.padding(pad)) {
            composable("today") { TodayScreen(vm) }
            composable("meds") { MedicinesScreen(vm, onAdd = { nav.navigate("edit/0") }, onOpen = { nav.navigate("detail/$it") }) }
            composable("edit/{id}") { e -> EditScreen(vm, e.arguments?.getString("id")?.toLongOrNull() ?: 0L) { nav.popBackStack() } }
            composable("detail/{id}") { e -> DetailScreen(vm, e.arguments?.getString("id")?.toLongOrNull() ?: 0L, onEdit = { nav.navigate("edit/$it") }, onBack = { nav.popBackStack() }) }
            composable("settings") { SettingsScreen(vm) }
        }
    }
}

@Composable
fun TodayScreen(vm: AppViewModel) {
    val doses by vm.today.collectAsStateWithLifecycle()
    val ctx = LocalContext.current
    val fmt = DateTimeFormatter.ofPattern("h:mm a")
    Column(Modifier.fillMaxSize().padding(16.dp)) {
        Text("Today", fontSize = 28.sp, fontWeight = FontWeight.Bold)
        if (doses.isEmpty()) Text("No medicines scheduled. Add one in the Medicines tab.", Modifier.padding(top = 16.dp))
        LazyColumn(verticalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.padding(top = 12.dp)) {
            items(doses, key = { "${it.med.id}-${it.scheduledAt}" }) { it ->
                val time = Instant.ofEpochMilli(it.scheduledAt).atZone(ZoneId.systemDefault()).format(fmt)
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(16.dp)) {
                        Text("$time  -  ${it.med.name}", fontSize = 22.sp, fontWeight = FontWeight.SemiBold)
                        Text("${it.med.doseText}  (${it.med.pillsPerDose} pill${if (it.med.pillsPerDose > 1) "s" else ""})", fontSize = 16.sp)
                        Text(
                            when (it.status) {
                                DoseStatus.TAKEN -> "Taken"; DoseStatus.MISSED -> "Missed"; DoseStatus.DUE -> "Due now"; DoseStatus.UPCOMING -> "Upcoming"
                            }, fontSize = 16.sp, modifier = Modifier.padding(top = 4.dp),
                        )
                        if (it.status == DoseStatus.DUE || it.status == DoseStatus.MISSED) {
                            Button(onClick = { vm.markTaken(it) }, modifier = Modifier.fillMaxWidth().height(64.dp).padding(top = 8.dp)) {
                                Text("Taken", fontSize = 22.sp)
                            }
                        }
                        if (it.status == DoseStatus.MISSED) {
                            OutlinedButton(onClick = {
                                val p = vm.prefs
                                val url = Messages.whatsAppUrl(Messages.normalizePhone(p.caregiverPhone),
                                    Messages.missedDose(it.med.patientName.ifBlank { p.patientName.ifBlank { "your family member" } }, it.med.name, time))
                                ctx.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                            }, Modifier.fillMaxWidth().padding(top = 8.dp)) { Text("Alert caregiver on WhatsApp") }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun MedicinesScreen(vm: AppViewModel, onAdd: () -> Unit, onOpen: (Long) -> Unit) {
    val meds by vm.medicines.collectAsStateWithLifecycle()
    val ctx = LocalContext.current
    Column(Modifier.fillMaxSize().padding(16.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text("Medicines", fontSize = 28.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
            IconButton(onClick = { PdfExport.share(ctx, PdfExport.create(ctx, vm.prefs.patientName, meds)) }, enabled = meds.isNotEmpty()) {
                Icon(Icons.Default.PictureAsPdf, "Doctor visit PDF")
            }
            IconButton(onClick = onAdd) { Icon(Icons.Default.Add, "Add medicine") }
        }
        LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            items(meds, key = { it.id }) { m ->
                val left = vm.repo.daysLeft(m)
                Card(Modifier.fillMaxWidth(), onClick = { onOpen(m.id) }) {
                    Column(Modifier.padding(16.dp)) {
                        Text(m.name, fontSize = 20.sp, fontWeight = FontWeight.SemiBold)
                        Text("${m.doseText} at ${m.timesCsv}")
                        Text("${m.pillCount} pills left (~$left days)", color = if (Refill.needsReorder(left)) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface)
                    }
                }
            }
        }
    }
}

@Composable
fun EditScreen(vm: AppViewModel, id: Long, onDone: () -> Unit) {
    val ctx = LocalContext.current
    val existing by produceState<Medicine?>(null, id) { value = if (id == 0L) null else vm.repo.medicines.get(id) }
    var loaded by remember { mutableStateOf(false) }
    var name by remember { mutableStateOf("") }; var dose by remember { mutableStateOf("") }
    var times by remember { mutableStateOf("08:00,20:00") }; var pills by remember { mutableStateOf("30") }
    var perDose by remember { mutableStateOf("1") }; var salt by remember { mutableStateOf("") }; var patient by remember { mutableStateOf("") }
    var scanMsg by remember { mutableStateOf("") }
    LaunchedEffect(existing) {
        val e = existing
        if (e != null && !loaded) { name = e.name; dose = e.doseText; times = e.timesCsv; pills = e.pillCount.toString(); perDose = e.pillsPerDose.toString(); salt = e.salt; patient = e.patientName; loaded = true }
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
                    if (found.isEmpty()) scanMsg = "Could not read a composition. Retake closer to the printed salt, or type it."
                    else { salt = found.joinToString(" + "); scanMsg = "Read: ${found.joinToString(", ")} - please confirm." }
                }
                .addOnFailureListener { scanMsg = "Scan failed: ${it.message}" }
            f.deleteOnExit()
        }
    }

    val timesOk = Schedule.parseTimes(times).isNotEmpty()
    val valid = name.isNotBlank() && timesOk && pills.toIntOrNull() != null && perDose.toIntOrNull()?.let { it > 0 } == true

    Column(Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(if (id == 0L) "Add medicine" else "Edit medicine", fontSize = 24.sp, fontWeight = FontWeight.Bold)
        OutlinedTextField(name, { name = it }, label = { Text("Medicine name") }, modifier = Modifier.fillMaxWidth())
        OutlinedTextField(dose, { dose = it }, label = { Text("Dose (e.g. 500 mg)") }, modifier = Modifier.fillMaxWidth())
        OutlinedTextField(times, { times = it }, label = { Text("Times, 24h, comma separated") }, isError = !timesOk, modifier = Modifier.fillMaxWidth())
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(pills, { pills = it.filter(Char::isDigit) }, label = { Text("Pills in stock") }, modifier = Modifier.weight(1f))
            OutlinedTextField(perDose, { perDose = it.filter(Char::isDigit) }, label = { Text("Pills per dose") }, modifier = Modifier.weight(1f))
        }
        OutlinedTextField(patient, { patient = it }, label = { Text("Patient name (optional)") }, modifier = Modifier.fillMaxWidth())
        OutlinedTextField(salt, { salt = it }, label = { Text("Generic name / salt") }, modifier = Modifier.fillMaxWidth())
        OutlinedButton(onClick = {
            val f = File.createTempFile("strip", ".jpg", ctx.cacheDir); photoFile = f
            camera.launch(FileProvider.getUriForFile(ctx, "${ctx.packageName}.files", f))
        }) { Icon(Icons.Default.CameraAlt, null); Spacer(Modifier.width(8.dp)); Text("Scan medicine strip") }
        if (scanMsg.isNotEmpty()) Text(scanMsg, fontSize = 14.sp)
        Button(enabled = valid, onClick = {
            val base = existing ?: Medicine(name = "", doseText = "", timesCsv = "", pillCount = 0)
            vm.save(base.copy(name = name.trim(), doseText = dose.trim(), timesCsv = Schedule.formatTimes(Schedule.parseTimes(times)),
                pillCount = pills.toInt(), pillsPerDose = perDose.toInt(), salt = salt.trim(), patientName = patient.trim())) { onDone() }
        }, modifier = Modifier.fillMaxWidth()) { Text("Save") }
    }
}

@Composable
fun DetailScreen(vm: AppViewModel, id: Long, onEdit: (Long) -> Unit, onBack: () -> Unit) {
    val med by produceState<Medicine?>(null, id) { value = vm.repo.medicines.get(id) }
    val card by vm.card.collectAsStateWithLifecycle()
    val loading by vm.loading.collectAsStateWithLifecycle()
    val ctx = LocalContext.current
    val m = med ?: return
    LaunchedEffect(m.salt) { if (m.salt.isNotBlank()) vm.loadCard(m.salt) }
    val left = vm.repo.daysLeft(m)
    LazyColumn(Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        item {
            Text(m.name, fontSize = 26.sp, fontWeight = FontWeight.Bold)
            Text("${m.doseText} at ${m.timesCsv}")
            Text("${m.pillCount} pills left (~$left days)")
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 8.dp)) {
                Button(onClick = { onEdit(m.id) }) { Text("Edit") }
                OutlinedButton(onClick = { vm.refillAdd(m, 30); onBack() }) { Text("+30 pills") }
                OutlinedButton(onClick = {
                    val p = vm.prefs
                    val url = Messages.whatsAppUrl(Messages.normalizePhone(p.pharmacyPhone), Messages.reorder(m.name, left, m.patientName.ifBlank { p.patientName.ifBlank { "my family member" } }))
                    ctx.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                }) { Text("Reorder") }
            }
            TextButton(onClick = { vm.archive(m.id); onBack() }) { Text("Remove medicine") }
            HorizontalDivider(Modifier.padding(vertical = 8.dp))
            if (m.salt.isBlank()) Text("Add the generic name (or scan the strip) to see medicine information.")
            if (loading) CircularProgressIndicator()
        }
        when (val r = card) {
            is CardResult.Error -> item { Text(r.message, color = MaterialTheme.colorScheme.error) }
            is CardResult.Ok -> {
                if (r.fromCache) item { Text("Offline - showing saved information.", fontSize = 13.sp) }
                if (r.response.cards.isEmpty()) item { Text("No official information found for this name. Check the spelling of the generic name.") }
                items(r.response.cards) { c ->
                    Card(Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text(c.generic.replaceFirstChar { it.uppercase() }, fontSize = 20.sp, fontWeight = FontWeight.SemiBold)
                            Section("Why you take it", listOf(c.whyYouTakeIt))
                            Section("How and when to take it", listOf(c.howToTake))
                            Section("Common side effects", c.commonSideEffects)
                            Section("Call your doctor if", c.callDoctorIf)
                            Section("Key interactions", c.keyInteractions)
                            Text("Sources: " + c.sources.joinToString { it.name }, fontSize = 12.sp)
                            Text(c.disclaimer.ifBlank { DISCLAIMER }, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
            null -> {}
        }
    }
}

@Composable
private fun Section(title: String, lines: List<String>) {
    val l = lines.filter { it.isNotBlank() }
    if (l.isEmpty()) return
    Text(title, fontWeight = FontWeight.SemiBold)
    l.forEach { Text(if (lines.size > 1) "- $it" else it, fontSize = 15.sp) }
}

@Composable
fun SettingsScreen(vm: AppViewModel) {
    val ctx = LocalContext.current
    var caregiver by remember { mutableStateOf(vm.prefs.caregiverPhone) }
    var pharmacy by remember { mutableStateOf(vm.prefs.pharmacyPhone) }
    var patient by remember { mutableStateOf(vm.prefs.patientName) }
    Column(Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("Settings", fontSize = 28.sp, fontWeight = FontWeight.Bold)
        OutlinedTextField(patient, { patient = it; vm.prefs.patientName = it }, label = { Text("Patient name") }, modifier = Modifier.fillMaxWidth())
        OutlinedTextField(caregiver, { caregiver = it; vm.prefs.caregiverPhone = it }, label = { Text("Caregiver WhatsApp number") }, modifier = Modifier.fillMaxWidth())
        OutlinedTextField(pharmacy, { pharmacy = it; vm.prefs.pharmacyPhone = it }, label = { Text("Pharmacy WhatsApp number") }, modifier = Modifier.fillMaxWidth())
        OutlinedButton(onClick = {
            if (android.os.Build.VERSION.SDK_INT >= 31)
                ctx.startActivity(Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM, Uri.parse("package:${ctx.packageName}")).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        }) { Text("Allow exact alarms (Android 12+)") }
        Text("Your data stays on this phone. Only a medicine's generic name is sent to the CareCircle server to fetch public drug information.", fontSize = 13.sp)
    }
}
