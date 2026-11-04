package com.carecircle.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CompareArrows
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.carecircle.R
import com.carecircle.domain.Conditions
import com.carecircle.domain.resolve
import com.carecircle.domain.OrganKind
import com.carecircle.domain.Refill
import com.carecircle.ui.theme.BigButton
import com.carecircle.ui.theme.Muted
import com.carecircle.ui.theme.OrganIcon
import com.carecircle.ui.theme.Palette
import com.carecircle.ui.theme.ProgressBar
import com.carecircle.ui.theme.SoftButton
import com.carecircle.ui.theme.SoftCard

@Composable
fun MedicinesScreen(vm: AppViewModel, onAdd: () -> Unit, onOpen: (Long) -> Unit, onInteractions: () -> Unit) {
    val meds by vm.medicines.collectAsStateWithLifecycle()
    val scope by vm.scopePerson.collectAsStateWithLifecycle()
    val ctx = LocalContext.current
    LazyColumn(
        Modifier.fillMaxSize().statusBarsPadding(),
        contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(stringResource(R.string.tab_medicines), style = MaterialTheme.typography.headlineLarge, modifier = Modifier.weight(1f))
                IconButton(
                    onClick = { vm.shareDoctorPdf() }, enabled = meds.isNotEmpty(),
                ) { Icon(Icons.Default.PictureAsPdf, stringResource(R.string.cd_doctor_pdf)) }
            }
        }
        item { PersonSwitcher(vm, allowAll = false) }
        com.carecircle.domain.Safety.duplicateIngredients(meds.map { com.carecircle.domain.MedLite(it.name, it.salt) }).forEach { (ingredient, names) ->
            item(key = "dup-$ingredient") {
                SoftCard(Modifier.fillMaxWidth(), tint = Palette.WarnSoft) {
                    Text(stringResource(R.string.warn_duplicate, ingredient, names.joinToString(", ")), style = MaterialTheme.typography.bodyLarge)
                }
            }
        }
        item { BigButton(stringResource(R.string.meds_add), onAdd, icon = Icons.Default.Add) }
        item { SoftButton(stringResource(R.string.inter_button), onInteractions, Modifier.fillMaxWidth(), icon = Icons.Default.CompareArrows) }
        item { MonthlyOrderButton(vm, meds, scope) }
        if (meds.isEmpty()) item { Muted(stringResource(R.string.meds_empty)) }
        items(meds, key = { it.id }) { m ->
            val left = vm.repo.daysLeft(m)
            val organ = Conditions.resolve(m.conditionTag, m.name, m.salt)?.organ ?: OrganKind.BODY
            SoftCard(Modifier.fillMaxWidth(), onClick = { onOpen(m.id) }) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(64.dp).clip(CircleShape).background(Palette.Canvas).padding(10.dp)) { OrganIcon(organ) }
                    Spacer(Modifier.width(14.dp))
                    Column(Modifier.weight(1f)) {
                        Text(m.name, style = MaterialTheme.typography.titleLarge)
                        Muted(listOf(m.doseText, m.timesCsv.replace(",", "  ")).filter { it.isNotBlank() }.joinToString("  -  "))
                    }
                }
                ExpiryPill(m.expiry, Modifier.padding(top = 8.dp), onlyWhenAttention = true)
                HighAlertPill(com.carecircle.domain.MedLite(m.name, m.salt), Modifier.padding(top = 8.dp))
                val low = Refill.needsReorder(left)
                Text(
                    pluralStringResource(R.plurals.pills_left, m.pillCount, m.pillCount, left),
                    Modifier.padding(top = 12.dp, bottom = 6.dp), style = MaterialTheme.typography.bodyMedium,
                    color = if (low) Palette.Bad else Palette.Ink,
                )
                ProgressBar((left / 30f), color = if (low) Palette.Bad else Palette.Orange)
            }
        }
    }
}

/** One pre-filled message listing what is running low, with a 30-day quantity. Sent only when the user taps send in WhatsApp or the share sheet. */
@Composable
private fun MonthlyOrderButton(vm: AppViewModel, meds: List<com.carecircle.data.Medicine>, person: com.carecircle.data.Person?) {
    val ctx = LocalContext.current
    val lines = com.carecircle.domain.Messages.monthlyOrder(
        meds.map { com.carecircle.domain.Messages.OrderMed(it.name, it.doseText, it.pillCount, it.pillsPerDose, com.carecircle.domain.Schedule.parseTimes(it.timesCsv).size, it.active) },
    )
    var none by androidx.compose.runtime.saveable.rememberSaveable { androidx.compose.runtime.mutableStateOf(false) }
    SoftButton(stringResource(R.string.monthly_order), {
        if (lines.isEmpty()) { none = true; return@SoftButton }
        none = false
        val who = person?.name ?: ctx.getString(R.string.family_member_fallback)
        val text = buildString {
            append(ctx.getString(R.string.monthly_header, who))
            lines.forEach { append("\n").append(ctx.getString(R.string.monthly_line, it.label, it.daysLeft, it.quantity)) }
            append("\n").append(ctx.getString(R.string.monthly_footer))
        }
        val phone = com.carecircle.domain.Messages.normalizePhone(person?.pharmacyPhone.orEmpty())
        val intent = if (phone != null) android.content.Intent(android.content.Intent.ACTION_VIEW, android.net.Uri.parse(com.carecircle.domain.Messages.whatsAppUrl(phone, text)))
        else android.content.Intent.createChooser(android.content.Intent(android.content.Intent.ACTION_SEND).setType("text/plain").putExtra(android.content.Intent.EXTRA_TEXT, text), ctx.getString(R.string.monthly_order))
        ctx.startActivity(intent.addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK))
    }, Modifier.fillMaxWidth())
    if (none) Muted(stringResource(R.string.monthly_none))
}
