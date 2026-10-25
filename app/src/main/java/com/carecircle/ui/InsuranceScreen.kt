package com.carecircle.ui

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Description
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.carecircle.R
import com.carecircle.domain.PolicyItem
import com.carecircle.domain.PolicySummary
import com.carecircle.domain.PolicyWait
import com.carecircle.ui.theme.BigButton
import com.carecircle.ui.theme.BrandRow
import com.carecircle.ui.theme.Muted
import com.carecircle.ui.theme.Palette
import com.carecircle.ui.theme.Pill
import com.carecircle.ui.theme.SoftButton
import com.carecircle.ui.theme.SoftCard
import java.io.File
import java.text.DateFormat
import java.util.Date

@Composable
fun InsuranceScreen(vm: AppViewModel) {
    val state by vm.insurance.collectAsStateWithLifecycle()
    val saved by vm.savedPolicies.collectAsStateWithLifecycle()
    val ctx = LocalContext.current
    val photos = remember { mutableStateListOf<Uri>() }
    var pending by rememberSaveable { mutableStateOf<String?>(null) }

    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris -> if (uris.isNotEmpty()) vm.readDocuments(uris) }
    val camera = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { ok ->
        val path = pending
        if (ok && path != null) photos += FileProvider.getUriForFile(ctx, "${ctx.packageName}.files", File(path))
    }

    LazyColumn(
        Modifier.fillMaxSize().statusBarsPadding(),
        contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item { BrandRow() }
        item { Text(stringResource(R.string.tab_insurance), style = MaterialTheme.typography.headlineLarge) }
        when (val s = state) {
            InsuranceState.Idle -> {
                item { Muted(stringResource(R.string.ins_intro)) }
                item {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        BigButton(stringResource(R.string.ins_add_policy), { picker.launch(arrayOf("application/pdf", "image/*")) }, icon = Icons.Default.Description)
                        SoftButton(stringResource(R.string.ins_photograph), {
                            val f = File.createTempFile("policy-", ".jpg", ctx.cacheDir)
                            pending = f.absolutePath
                            camera.launch(FileProvider.getUriForFile(ctx, "${ctx.packageName}.files", f))
                        }, Modifier.fillMaxWidth(), icon = Icons.Default.CameraAlt)
                        if (photos.isNotEmpty()) {
                            Muted(pluralStringResource(R.plurals.ins_photos_ready, photos.size, photos.size))
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                BigButton(stringResource(R.string.ins_read_photos), { vm.readDocuments(photos.toList()); photos.clear() }, Modifier.weight(1f))
                                SoftButton(stringResource(R.string.ins_clear_photos), { photos.clear(); ctx.cacheDir.listFiles { f -> f.name.startsWith("policy-") }?.forEach { it.delete() } })
                            }
                        }
                    }
                }
                item { Text(stringResource(R.string.ins_saved_title), style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(top = 6.dp)) }
                if (saved.isEmpty()) item { Muted(stringResource(R.string.ins_saved_empty)) }
                items(saved, key = { it.id }) { item ->
                    SoftCard(Modifier.fillMaxWidth(), onClick = { vm.viewSaved(item) }) {
                        Text(
                            stringResource(R.string.ins_saved_on, DateFormat.getDateInstance(DateFormat.MEDIUM).format(Date(item.policy.savedAt))),
                            style = MaterialTheme.typography.titleMedium,
                        )
                        item.policy.summary.sumInsured?.let { Muted(stringResource(R.string.ins_f_sum_insured) + ": " + it.value) }
                        TextButton(onClick = { vm.deleteSaved(item.id) }) { Text(stringResource(R.string.action_delete), color = Palette.Bad) }
                    }
                }
            }
            is InsuranceState.Reading -> item {
                SoftCard(Modifier.fillMaxWidth()) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        CircularProgressIndicator(Modifier.size(28.dp), strokeWidth = 3.dp, color = Palette.Orange)
                        Spacer(Modifier.width(12.dp))
                        Text(
                            if (s.total == 0) stringResource(R.string.ins_reading_start) else stringResource(R.string.ins_reading_progress, s.done, s.total),
                            style = MaterialTheme.typography.titleMedium,
                        )
                    }
                    Muted(stringResource(R.string.ins_reading_note))
                    SoftButton(stringResource(R.string.action_cancel), vm::cancelReading, Modifier.padding(top = 10.dp))
                }
            }
            is InsuranceState.ReadFailed -> item {
                Notice(
                    when (s.kind) {
                        ReadFailure.PASSWORD -> stringResource(R.string.ins_err_password)
                        ReadFailure.UNREADABLE -> stringResource(R.string.ins_err_unreadable)
                        ReadFailure.TOO_LITTLE_TEXT -> stringResource(R.string.ins_err_little_text)
                    },
                    Palette.BadSoft, stringResource(R.string.ins_try_again), vm::resetInsurance,
                )
            }
            is InsuranceState.NeedConsent -> item { ConsentCard(s.pages.size, s.truncated, vm) }
            InsuranceState.Sending -> item {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    CircularProgressIndicator(Modifier.size(28.dp), strokeWidth = 3.dp, color = Palette.Orange)
                    Spacer(Modifier.width(12.dp))
                    Muted(stringResource(R.string.ins_sending))
                }
            }
            is InsuranceState.Failed -> item {
                Notice(
                    when (s.kind) {
                        SendFailure.TOO_LARGE -> stringResource(R.string.ins_err_large)
                        SendFailure.RATE_LIMITED -> stringResource(R.string.info_rate_limited)
                        SendFailure.OFFLINE -> stringResource(R.string.ins_err_offline)
                        SendFailure.OTHER -> stringResource(R.string.ins_err_other)
                    },
                    Palette.WarnSoft, stringResource(R.string.ins_try_again), vm::resetInsurance,
                )
            }
            is InsuranceState.Result -> resultItems(s, vm)
        }
    }
}

@Composable
private fun Notice(text: String, tint: Color, action: String, onAction: () -> Unit) {
    SoftCard(Modifier.fillMaxWidth(), tint = tint) {
        Text(text, style = MaterialTheme.typography.bodyLarge)
        SoftButton(action, onAction, Modifier.padding(top = 10.dp))
    }
}

/** Shown every time, no "remember my choice". Nothing leaves the phone until the button is pressed. */
@Composable
private fun ConsentCard(pageCount: Int, truncated: Boolean, vm: AppViewModel) {
    SoftCard(Modifier.fillMaxWidth()) {
        Text(stringResource(R.string.consent_title), style = MaterialTheme.typography.titleLarge)
        Muted(pluralStringResource(R.plurals.ins_pages_read, pageCount, pageCount))
        if (truncated) Text(stringResource(R.string.ins_first_pages_note), Modifier.padding(top = 4.dp), style = MaterialTheme.typography.bodySmall, color = Palette.Warn)
        Spacer(Modifier.height(10.dp))
        ConsentLine(stringResource(R.string.consent_what_title), stringResource(R.string.consent_what))
        ConsentLine(stringResource(R.string.consent_who_title), stringResource(R.string.consent_who))
        ConsentLine(stringResource(R.string.consent_stored_title), stringResource(R.string.consent_stored))
        BigButton(stringResource(R.string.consent_agree), vm::consentAndSend, Modifier.padding(top = 14.dp))
        SoftButton(stringResource(R.string.action_cancel), vm::resetInsurance, Modifier.fillMaxWidth().padding(top = 8.dp))
    }
}

@Composable
private fun ConsentLine(title: String, body: String) {
    Column(Modifier.padding(top = 8.dp)) {
        Text(title, style = MaterialTheme.typography.titleMedium)
        Text(body, style = MaterialTheme.typography.bodyMedium)
    }
}

private fun androidx.compose.foundation.lazy.LazyListScope.resultItems(s: InsuranceState.Result, vm: AppViewModel) {
    val sum = s.response.summary
    item { Pill(stringResource(R.string.ins_notice_pill), Palette.OrangeSoft) }
    if (s.truncated) item { Text(stringResource(R.string.ins_first_pages_note), style = MaterialTheme.typography.bodySmall, color = Palette.Warn) }
    if (sum.isEmpty) item { Notice(stringResource(R.string.ins_nothing_found), Palette.Surface, stringResource(R.string.action_done), vm::resetInsurance) }
    else {
        item { SummaryCard(sum) }
    }
    if (s.response.unverified.isNotEmpty()) item {
        SoftCard(Modifier.fillMaxWidth(), tint = Palette.WarnSoft) {
            Text(stringResource(R.string.ins_unverified_title), style = MaterialTheme.typography.titleMedium)
            s.response.unverified.forEach { Text("- " + fieldLabel(it), style = MaterialTheme.typography.bodyMedium) }
        }
    }
    item { Text(stringResource(R.string.disclaimer_insurance), style = MaterialTheme.typography.labelMedium, color = Palette.Muted) }
    item {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            if (!s.saved) BigButton(stringResource(R.string.ins_save), { vm.saveCurrentPolicy() }, Modifier.weight(1f))
            else Text(stringResource(R.string.ins_saved_note), Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium, color = Palette.Good)
            SoftButton(stringResource(R.string.action_done), vm::resetInsurance)
        }
    }
    if (s.saved && s.savedId != null) item {
        TextButton(onClick = { vm.deleteSaved(s.savedId) }) { Text(stringResource(R.string.action_delete), color = Palette.Bad) }
    }
}

@Composable
private fun fieldLabel(key: String): String = when (key) {
    "sumInsured" -> stringResource(R.string.ins_f_sum_insured)
    "roomRentCap" -> stringResource(R.string.ins_f_room_rent)
    "coPay" -> stringResource(R.string.ins_f_copay)
    "waitingInitial" -> stringResource(R.string.ins_f_wait_initial)
    "waitingPreExisting" -> stringResource(R.string.ins_f_wait_pre)
    "waitingSpecific" -> stringResource(R.string.ins_f_wait_specific)
    "inclusions" -> stringResource(R.string.ins_f_inclusions)
    "exclusions" -> stringResource(R.string.ins_f_exclusions)
    "dayCare" -> stringResource(R.string.ins_f_day_care)
    "preHospitalisation" -> stringResource(R.string.ins_f_pre_hosp)
    "postHospitalisation" -> stringResource(R.string.ins_f_post_hosp)
    "claimHelpline" -> stringResource(R.string.ins_f_helpline)
    "tpa" -> stringResource(R.string.ins_f_tpa)
    else -> key
}

@Composable
private fun SummaryCard(sum: PolicySummary) {
    SoftCard(Modifier.fillMaxWidth()) {
        Text(stringResource(R.string.ins_summary_title), style = MaterialTheme.typography.titleLarge)
        CitedRow(R.string.ins_f_sum_insured, sum.sumInsured)
        CitedRow(R.string.ins_f_room_rent, sum.roomRentCap)
        CitedRow(R.string.ins_f_copay, sum.coPay)
        CitedRow(R.string.ins_f_wait_initial, sum.waitingInitial)
        CitedRow(R.string.ins_f_wait_pre, sum.waitingPreExisting)
        sum.waitingSpecific.forEach { WaitRow(it) }
        CitedRow(R.string.ins_f_day_care, sum.dayCare)
        CitedRow(R.string.ins_f_pre_hosp, sum.preHospitalisation)
        CitedRow(R.string.ins_f_post_hosp, sum.postHospitalisation)
        CitedRow(R.string.ins_f_helpline, sum.claimHelpline)
        CitedRow(R.string.ins_f_tpa, sum.tpa)
        if (sum.inclusions.isNotEmpty()) {
            Text(stringResource(R.string.ins_f_inclusions), Modifier.padding(top = 14.dp), style = MaterialTheme.typography.titleMedium)
            sum.inclusions.forEach { CitedValue(null, it.value, it.page, it.clause, it.quote) }
        }
        if (sum.exclusions.isNotEmpty()) {
            Text(stringResource(R.string.ins_f_exclusions), Modifier.padding(top = 14.dp), style = MaterialTheme.typography.titleMedium)
            sum.exclusions.forEach { CitedValue(null, it.value, it.page, it.clause, it.quote) }
        }
    }
}

@Composable
private fun CitedRow(labelRes: Int, item: PolicyItem?) {
    if (item != null) CitedValue(stringResource(labelRes), item.value, item.page, item.clause, item.quote)
}

@Composable
private fun WaitRow(w: PolicyWait) {
    CitedValue(stringResource(R.string.ins_f_wait_specific) + ": " + w.illness, w.period, w.page, w.clause, w.quote)
}

/** One fact with its citation and an expandable source quote. No fact appears without these. */
@Composable
fun CitedValue(label: String?, value: String, page: Int, clause: String?, quote: String) {
    var open by rememberSaveable(label, value, page) { mutableStateOf(false) }
    Column(Modifier.fillMaxWidth().padding(top = 12.dp)) {
        if (label != null) Text(label, style = MaterialTheme.typography.labelMedium, color = Palette.Muted)
        Text(value, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Medium)
        Text(citation(page, clause), style = MaterialTheme.typography.bodySmall, color = Palette.Orange)
        TextButton(onClick = { open = !open }, contentPadding = PaddingValues(0.dp)) {
            Text(if (open) stringResource(R.string.ins_hide_source) else stringResource(R.string.ins_show_source), style = MaterialTheme.typography.labelMedium)
        }
        if (open) Text("\"" + quote + "\"", Modifier.fillMaxWidth(), style = MaterialTheme.typography.bodySmall, color = Palette.Muted)
    }
}

@Composable
fun citation(page: Int, clause: String?): String =
    if (clause.isNullOrBlank()) stringResource(R.string.cite_page, page) else stringResource(R.string.cite_page_clause, page, clause)
