package com.carecircle.ui

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.clickable
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
import androidx.compose.material3.CircularProgressIndicator
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.carecircle.R
import com.carecircle.data.CardResult
import com.carecircle.data.Medicine
import com.carecircle.data.MedicineCard
import com.carecircle.domain.Conditions
import com.carecircle.domain.Coverage
import com.carecircle.domain.CoverageKind
import com.carecircle.domain.SavedPolicy
import com.carecircle.domain.resolve
import com.carecircle.domain.Messages
import com.carecircle.domain.OrganKind
import com.carecircle.ui.theme.BigButton
import com.carecircle.ui.theme.Muted
import com.carecircle.ui.theme.OrganView
import com.carecircle.ui.theme.Palette
import com.carecircle.ui.theme.Pill
import com.carecircle.ui.theme.SoftButton
import com.carecircle.ui.theme.SoftCard

@Composable
fun DetailScreen(vm: AppViewModel, id: Long, onEdit: (Long) -> Unit, onBack: () -> Unit, onOpenInsurance: () -> Unit) {
    var med by remember { mutableStateOf<Medicine?>(null) }
    LaunchedEffect(id) { med = vm.repo.medicines.get(id) }
    val card by vm.card.collectAsStateWithLifecycle()
    val loading by vm.loading.collectAsStateWithLifecycle()
    val ctx = LocalContext.current
    val m = med ?: return
    LaunchedEffect(m.salt) { if (m.salt.isBlank()) vm.clearCard() else vm.loadCard(m.salt) }
    val people by vm.people.collectAsStateWithLifecycle()
    val owner = people.firstOrNull { it.id == m.personId }
    val left = vm.repo.daysLeft(m)
    val condition = Conditions.resolve(m.conditionTag, m.name, m.salt)
    var policy by remember { mutableStateOf<SavedPolicy?>(null) }
    var policyLoaded by remember { mutableStateOf(false) }
    LaunchedEffect(m.personId, m.conditionTag) { policy = vm.latestPolicy(m.personId); policyLoaded = true }
    val suggestion = (card as? CardResult.Ok)?.takeIf { m.conditionTag.isNullOrBlank() }
        ?.response?.cards?.firstNotNullOfOrNull { Coverage.suggestFromText(it.whyYouTakeIt) }
    val organ = condition?.organ ?: OrganKind.BODY

    LazyColumn(
        Modifier.fillMaxSize().statusBarsPadding(),
        contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            Column {
            Row(verticalAlignment = Alignment.CenterVertically) {
                TextButton(onClick = onBack) { Text(stringResource(R.string.action_back)) }
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                OrganView(organ, Modifier.size(150.dp), description = stringResource(R.string.hero_organ_desc, organ.label()))
                Spacer(Modifier.width(8.dp))
                Column {
                    if (condition != null) Pill(condition.label())
                    Text(m.name, style = MaterialTheme.typography.headlineMedium, modifier = Modifier.padding(top = 6.dp))
                    Muted(listOf(m.doseText, m.timesCsv.replace(",", "  ")).filter { it.isNotBlank() }.joinToString("  -  "))
                    Muted(pluralStringResource(R.plurals.pills_left, m.pillCount, m.pillCount, left))
                    ExpiryPill(m.expiry, Modifier.padding(top = 6.dp))
                }
            }
            }
        }
        item {
            Column {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                SoftButton(stringResource(R.string.action_edit), { onEdit(m.id) })
                SoftButton(stringResource(R.string.action_add_pills), { vm.refillAdd(m, 30); onBack() })
                SoftButton(stringResource(R.string.action_reorder), {
                    val who = owner?.name ?: ctx.getString(R.string.family_member_fallback)
                    val text = ctx.getString(R.string.msg_reorder, Messages.medicineLabel(m.name, m.doseText), who, left)
                    val url = Messages.whatsAppUrl(Messages.normalizePhone(owner?.pharmacyPhone.orEmpty()), text)
                    ctx.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                }, enabled = !owner?.pharmacyPhone.isNullOrBlank())
            }
            if (owner?.pharmacyPhone.isNullOrBlank()) Muted(stringResource(R.string.reorder_add_number, owner?.name ?: ""))
            TextButton(onClick = { vm.archive(m.id); onBack() }) { Text(stringResource(R.string.action_remove), color = Palette.Bad) }
            }
        }

        item { DoctorNoteCard(m, vm) { med = it } }
        if (suggestion != null) item {
            SoftCard(Modifier.fillMaxWidth(), tint = Palette.OrangeSoft) {
                Text(stringResource(R.string.tag_suggest, suggestion.label()), style = MaterialTheme.typography.bodyLarge)
                SoftButton(stringResource(R.string.tag_suggest_apply, suggestion.label()), {
                    val updated = m.copy(conditionTag = suggestion.label)
                    vm.save(updated) { med = updated }
                }, Modifier.padding(top = 8.dp))
            }
        }
        if (!m.conditionTag.isNullOrBlank() && policyLoaded) item { PolicyCard(m.conditionTag!!, policy, onOpenInsurance) }
        item { Text(stringResource(R.string.info_title), style = MaterialTheme.typography.titleLarge) }
        when {
            m.salt.isBlank() -> item { NoticeCard(stringResource(R.string.info_need_name), stringResource(R.string.info_edit_name)) { onEdit(m.id) } }
            loading -> item {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    CircularProgressIndicator(Modifier.size(24.dp), strokeWidth = 3.dp, color = Palette.Orange)
                    Spacer(Modifier.width(12.dp))
                    Muted(stringResource(R.string.info_loading))
                }
            }
            else -> when (val r = card) {
                is CardResult.Ok -> {
                    if (r.fromCache) item { NoticeCard(stringResource(R.string.info_offline), null, tint = Palette.WarnSoft) {} }
                    item { FreshnessRow(r.fetchedAt, r.fromCache) { vm.loadCard(m.salt) } }
                    items(r.response.cards) { c -> InfoCard(c) }
                    if (r.response.unresolved.isNotEmpty()) item {
                        NoticeCard(
                            stringResource(R.string.info_partial, r.response.unresolved.joinToString(", ")),
                            stringResource(R.string.info_edit_name),
                        ) { onEdit(m.id) }
                    }
                }
                is CardResult.NotFound -> item {
                    Column {
                    NoticeCard(
                        stringResource(R.string.info_not_found, r.names.joinToString(", ")),
                        stringResource(R.string.info_edit_name),
                    ) { onEdit(m.id) }
                    Spacer(Modifier.height(8.dp))
                    Disclaimer()
                    }
                }
                CardResult.RateLimited -> item { NoticeCard(stringResource(R.string.info_rate_limited), stringResource(R.string.action_retry), tint = Palette.WarnSoft) { vm.loadCard(m.salt) } }
                CardResult.Error -> item { NoticeCard(stringResource(R.string.info_error), stringResource(R.string.action_retry), tint = Palette.BadSoft) { vm.loadCard(m.salt) } }
                CardResult.NoSalt, null -> {}
            }
        }
        item { DiaryCard(m, vm) }
        item { Spacer(Modifier.height(24.dp)) }
    }
}

/** What the saved policy text says about this condition. Only the policy's own words, each with page, clause and quote. */
@Composable
private fun PolicyCard(tag: String, policy: SavedPolicy?, onOpenInsurance: () -> Unit) {
    val matches = Coverage.matchCoverage(tag, policy?.summary)
    SoftCard(Modifier.fillMaxWidth()) {
        Text(stringResource(R.string.cov_title), style = MaterialTheme.typography.titleLarge)
        when {
            policy == null -> {
                Text(stringResource(R.string.cov_no_policy), Modifier.padding(top = 6.dp), style = MaterialTheme.typography.bodyLarge)
                SoftButton(stringResource(R.string.cov_open_insurance), onOpenInsurance, Modifier.padding(top = 8.dp))
            }
            matches.isEmpty() -> Text(stringResource(R.string.cov_no_mention), Modifier.padding(top = 6.dp), style = MaterialTheme.typography.bodyLarge)
            else -> matches.forEach { m ->
                Pill(
                    stringResource(
                        when (m.kind) {
                            CoverageKind.WAITING_SPECIFIC -> R.string.cov_kind_waiting
                            CoverageKind.PRE_EXISTING -> R.string.cov_kind_pre_existing
                            CoverageKind.EXCLUSION -> R.string.cov_kind_exclusion
                            CoverageKind.INCLUSION -> R.string.cov_kind_inclusion
                        },
                    ),
                    Palette.OrangeSoft, Palette.Ink, Modifier.padding(top = 12.dp),
                )
                CitedValue(null, m.text, m.page, m.clause, m.quote)
            }
        }
        Text(stringResource(R.string.disclaimer_insurance), Modifier.padding(top = 12.dp), style = MaterialTheme.typography.labelMedium, color = Palette.Muted)
    }
}

@Composable
private fun NoticeCard(text: String, action: String?, tint: androidx.compose.ui.graphics.Color = Palette.Surface, onAction: () -> Unit) {
    SoftCard(Modifier.fillMaxWidth(), tint = tint) {
        Text(text, style = MaterialTheme.typography.bodyLarge)
        if (action != null) SoftButton(action, onAction, Modifier.padding(top = 10.dp))
    }
}

@Composable
private fun Disclaimer() {
    Text(stringResource(R.string.disclaimer_medical), style = MaterialTheme.typography.labelMedium, color = Palette.Muted)
}

@Composable
private fun InfoCard(c: MedicineCard) {
    val ctx = LocalContext.current
    SoftCard(Modifier.fillMaxWidth()) {
        Text(c.generic.replaceFirstChar { it.uppercase() }, style = MaterialTheme.typography.titleLarge)
        InfoSection(stringResource(R.string.info_why), listOf(c.whyYouTakeIt))
        InfoSection(stringResource(R.string.info_how), listOf(c.howToTake))
        InfoSection(stringResource(R.string.info_side_effects), c.commonSideEffects)
        InfoSection(stringResource(R.string.info_call_doctor), c.callDoctorIf)
        InfoSection(stringResource(R.string.info_interactions), c.keyInteractions)
        Text(stringResource(R.string.info_sources), Modifier.padding(top = 12.dp), style = MaterialTheme.typography.labelMedium, color = Palette.Muted)
        c.sources.forEach { s ->
            Text(
                s.name, Modifier.padding(vertical = 4.dp).clickable(enabled = s.url != null) {
                    ctx.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(s.url)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                },
                style = MaterialTheme.typography.bodySmall, color = if (s.url != null) Palette.Orange else Palette.Muted,
            )
        }
        Spacer(Modifier.height(6.dp))
        Text(stringResource(R.string.disclaimer_medical), fontSize = 12.sp, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun InfoSection(title: String, lines: List<String>) {
    val l = lines.filter { it.isNotBlank() }
    if (l.isEmpty()) return
    Text(title, Modifier.padding(top = 10.dp), style = MaterialTheme.typography.titleMedium)
    l.forEach { Text(if (lines.size > 1) "- $it" else it, style = MaterialTheme.typography.bodyMedium) }
}
