package com.carecircle.ui

import android.content.Intent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.carecircle.R
import com.carecircle.data.CareSync
import com.carecircle.data.Following
import com.carecircle.data.FollowResult
import com.carecircle.data.LinkState
import com.carecircle.data.Person
import com.carecircle.data.SyncLink
import com.carecircle.domain.PairingCode
import com.carecircle.domain.SyncType
import com.carecircle.ui.theme.BigButton
import com.carecircle.ui.theme.Muted
import com.carecircle.ui.theme.Palette
import com.carecircle.ui.theme.Pill
import com.carecircle.ui.theme.SoftButton
import com.carecircle.ui.theme.SoftCard

/** Share a person's dose status with family (patient side) and follow someone (family side). */
@Composable
fun FamilyScreen(vm: AppViewModel, onBack: () -> Unit) {
    val ctx = LocalContext.current
    val configured = remember { CareSync.isConfigured(ctx) }
    val people by vm.people.collectAsStateWithLifecycle()
    val links by vm.syncLinks.collectAsStateWithLifecycle()
    val following by vm.following.collectAsStateWithLifecycle()
    val states by vm.linkStates.collectAsStateWithLifecycle()
    val pairing by vm.pairing.collectAsStateWithLifecycle()
    val followResult by vm.followResult.collectAsStateWithLifecycle()
    var consentFor by remember { mutableStateOf<Person?>(null) }
    var code by rememberSaveable { mutableStateOf("") }
    LaunchedEffect(links.size) { vm.refreshLinkStates() }

    LazyColumn(
        Modifier.fillMaxSize().statusBarsPadding(),
        contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item { TextButton(onClick = onBack) { Text(stringResource(R.string.action_back)) } }
        item { Text(stringResource(R.string.family_title), style = MaterialTheme.typography.headlineLarge) }
        item { Muted(stringResource(R.string.family_intro)) }
        if (!configured) {
            item { SoftCard(Modifier.fillMaxWidth(), tint = Palette.WarnSoft) { Text(stringResource(R.string.family_not_configured), style = MaterialTheme.typography.bodyLarge) } }
            return@LazyColumn
        }

        item { Text(stringResource(R.string.family_share_title), style = MaterialTheme.typography.titleLarge) }
        items(people, key = { "p-${it.id}" }) { p ->
            SoftCard(Modifier.fillMaxWidth()) {
                Text(displayName(p, people), style = MaterialTheme.typography.titleMedium)
                links.filter { it.personId == p.id }.forEach { l -> LinkRow(l, states[l.linkId] ?: LinkState.UNKNOWN, vm) }
                SoftButton(stringResource(R.string.family_share_button, p.name), { consentFor = p }, Modifier.padding(top = 10.dp))
            }
        }

        item { Text(stringResource(R.string.family_follow_title), style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(top = 8.dp)) }
        item {
            SoftCard(Modifier.fillMaxWidth()) {
                AppField(code, { code = it.take(12) }, stringResource(R.string.family_follow_hint))
                BigButton(stringResource(R.string.family_follow_button), { vm.follow(code) }, Modifier.padding(top = 10.dp), enabled = code.isNotBlank())
                followResult?.let { r ->
                    Text(followMessage(r), Modifier.padding(top = 8.dp), style = MaterialTheme.typography.bodyMedium, color = if (r is FollowResult.Ok) Palette.Good else Palette.Bad)
                }
            }
        }
        if (following.isEmpty()) item { Muted(stringResource(R.string.family_following_none)) }
        items(following, key = { "f-${it.linkId}" }) { f -> FollowedCard(f, vm) }
    }

    consentFor?.let { p ->
        AlertDialog(
            onDismissRequest = { consentFor = null },
            title = { Text(stringResource(R.string.family_consent_title)) },
            text = {
                androidx.compose.foundation.layout.Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(stringResource(R.string.family_consent_what, p.name))
                    Text(stringResource(R.string.family_consent_who))
                    Text(stringResource(R.string.family_consent_where))
                }
            },
            confirmButton = { TextButton(onClick = { consentFor = null; vm.createPairing(p.id) }) { Text(stringResource(R.string.family_consent_agree)) } },
            dismissButton = { TextButton(onClick = { consentFor = null }) { Text(stringResource(R.string.action_cancel)) } },
            containerColor = Palette.Surface,
        )
    }

    when (val s = pairing) {
        PairingUi.None -> {}
        PairingUi.Creating -> AlertDialog(
            onDismissRequest = {}, confirmButton = {},
            text = { Row(verticalAlignment = Alignment.CenterVertically) { CircularProgressIndicator(Modifier.padding(end = 12.dp), color = Palette.Orange); Text(stringResource(R.string.family_creating)) } },
            containerColor = Palette.Surface,
        )
        is PairingUi.Code -> AlertDialog(
            onDismissRequest = vm::dismissPairing,
            title = { Text(stringResource(R.string.family_code_title)) },
            text = {
                androidx.compose.foundation.layout.Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(PairingCode.display(s.code), fontSize = 36.sp, fontWeight = FontWeight.Medium)
                    Text(stringResource(R.string.family_code_valid, PairingCode.VALID_MINUTES.toInt()))
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    val text = ctx.getString(R.string.family_code_share_text, s.personName, PairingCode.display(s.code), PairingCode.VALID_MINUTES.toInt())
                    val send = Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, text)
                    ctx.startActivity(Intent.createChooser(send, ctx.getString(R.string.family_code_share)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                }) { Text(stringResource(R.string.family_code_share)) }
            },
            dismissButton = { TextButton(onClick = vm::dismissPairing) { Text(stringResource(R.string.action_done)) } },
            containerColor = Palette.Surface,
        )
        is PairingUi.Failed -> AlertDialog(
            onDismissRequest = vm::dismissPairing, confirmButton = { TextButton(onClick = vm::dismissPairing) { Text(stringResource(R.string.action_ok)) } },
            text = { Text(stringResource(if (s.offline) R.string.family_err_offline else R.string.family_err_error)) },
            containerColor = Palette.Surface,
        )
    }
}

@Composable
private fun followMessage(r: FollowResult): String = when (r) {
    is FollowResult.Ok -> stringResource(R.string.family_followed, r.personName)
    FollowResult.Invalid -> stringResource(R.string.family_err_invalid)
    FollowResult.NotFound -> stringResource(R.string.family_err_notfound)
    FollowResult.Expired -> stringResource(R.string.family_err_expired)
    FollowResult.Taken -> stringResource(R.string.family_err_taken)
    FollowResult.NotConfigured -> stringResource(R.string.family_not_configured)
    FollowResult.Offline -> stringResource(R.string.family_err_offline)
    FollowResult.Error -> stringResource(R.string.family_err_error)
}

@Composable
private fun LinkRow(l: SyncLink, state: LinkState, vm: AppViewModel) {
    androidx.compose.foundation.layout.Column(Modifier.fillMaxWidth().padding(top = 10.dp)) {
        Text(stringResource(R.string.family_code_label) + " " + PairingCode.display(l.linkId), style = MaterialTheme.typography.bodyLarge)
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            val (label, bg, fg) = when (state) {
                LinkState.LINKED -> Triple(stringResource(R.string.family_state_linked), Palette.GoodSoft, Palette.Good)
                LinkState.WAITING -> Triple(stringResource(R.string.family_state_waiting), Palette.WarnSoft, Palette.Warn)
                LinkState.GONE -> Triple(stringResource(R.string.family_state_gone), Palette.Line, Palette.Muted)
                LinkState.UNKNOWN -> Triple(stringResource(R.string.family_state_unknown), Palette.Line, Palette.Muted)
            }
            Pill(label, bg, fg)
            androidx.compose.foundation.layout.Spacer(Modifier.weight(1f))
            TextButton(onClick = { vm.unlink(l.linkId) }) { Text(stringResource(R.string.family_unlink), color = Palette.Bad) }
        }
    }
}

@Composable
private fun FollowedCard(f: Following, vm: AppViewModel) {
    val ctx = LocalContext.current
    val flow = remember(f.linkId) { vm.events(f.linkId) }
    val events by flow.collectAsStateWithLifecycle(emptyList())
    SoftCard(Modifier.fillMaxWidth()) {
        Text(f.personName.ifBlank { stringResource(R.string.family_member_fallback) }, style = MaterialTheme.typography.titleLarge)
        if (events.isEmpty()) Muted(stringResource(R.string.family_events_none), Modifier.padding(top = 4.dp))
        events.take(6).forEach { e ->
            Row(Modifier.fillMaxWidth().padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                val (bg, fg) = when (e.type) {
                    SyncType.TAKEN -> Palette.GoodSoft to Palette.Good
                    SyncType.MISSED -> Palette.BadSoft to Palette.Bad
                    SyncType.SKIPPED -> Palette.Line to Palette.Muted
                    SyncType.SUMMARY -> Palette.OrangeSoft to Palette.Ink
                }
                Pill(stringResource(when (e.type) { SyncType.TAKEN -> R.string.status_taken; SyncType.MISSED -> R.string.status_missed; SyncType.SKIPPED -> R.string.status_skipped; SyncType.SUMMARY -> R.string.hist_title }), bg, fg)
                Text(describeEvent(ctx, e), Modifier.padding(start = 10.dp).weight(1f), style = MaterialTheme.typography.bodyMedium)
            }
        }
        TextButton(onClick = { vm.unfollow(f.linkId) }, Modifier.padding(top = 4.dp)) { Text(stringResource(R.string.family_stop_following), color = Palette.Bad) }
    }
}
