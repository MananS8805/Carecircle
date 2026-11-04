package com.carecircle.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.HelpOutline
import androidx.compose.material.icons.filled.HourglassBottom
import androidx.compose.material.icons.filled.SearchOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.carecircle.R
import com.carecircle.data.PolicyQuestionResponse
import com.carecircle.domain.ClaimAnswer
import com.carecircle.domain.ClaimCitation
import com.carecircle.domain.ClaimView
import com.carecircle.domain.Verdict
import com.carecircle.ui.theme.BigButton
import com.carecircle.ui.theme.Muted
import com.carecircle.ui.theme.Palette
import com.carecircle.ui.theme.SoftButton
import com.carecircle.ui.theme.SoftCard

/** "Ask about your policy": a question, a consent screen every time, and an answer that always points to a clause. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun AskPolicySection(vm: AppViewModel) {
    val state by vm.ask.collectAsStateWithLifecycle()
    val prefill by vm.askPrefill.collectAsStateWithLifecycle()
    var question by rememberSaveable { mutableStateOf("") }
    LaunchedEffect(prefill) { prefill?.let { question = it; vm.takeAskPrefill() } }
    val examples = listOf(R.string.ask_example_1, R.string.ask_example_2, R.string.ask_example_3).map { stringResource(it) }

    SoftCard(Modifier.fillMaxWidth()) {
        Text(stringResource(R.string.ask_title), style = MaterialTheme.typography.titleLarge)
        Muted(stringResource(R.string.ask_intro), Modifier.padding(top = 4.dp))
        FlowRow(Modifier.padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            examples.forEach { ex -> AssistChip(onClick = { question = ex }, label = { Text(ex) }, modifier = Modifier.heightIn(min = 48.dp)) }
        }
        AppField(question, { question = it.take(ClaimAnswer.MAX_QUESTION) }, stringResource(R.string.ask_hint), Modifier.padding(top = 8.dp), singleLine = false)
        BigButton(stringResource(R.string.ask_button), { vm.askPolicy(question) }, Modifier.padding(top = 8.dp), enabled = ClaimAnswer.validQuestion(question))

        when (val s = state) {
            AskState.Idle -> {}
            is AskState.NeedConsent -> AlertDialog(
                onDismissRequest = vm::resetAsk,
                title = { Text(stringResource(R.string.ask_consent_title)) },
                text = { Text(stringResource(R.string.ask_consent_text, s.clauseCount)) },
                confirmButton = { TextButton(onClick = { vm.confirmAsk() }) { Text(stringResource(R.string.ask_send)) } },
                dismissButton = { TextButton(onClick = vm::resetAsk) { Text(stringResource(R.string.action_cancel)) } },
                containerColor = Palette.Surface,
            )
            AskState.Sending -> Row(Modifier.padding(top = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                CircularProgressIndicator(Modifier.size(24.dp), strokeWidth = 3.dp, color = Palette.Orange)
                Spacer(Modifier.width(12.dp))
                Muted(stringResource(R.string.ask_sending))
            }
            is AskState.Failed -> {
                Text(
                    stringResource(
                        when (s.kind) {
                            AskFailure.BUSY -> R.string.ask_err_busy
                            AskFailure.RATE_LIMITED -> R.string.info_rate_limited
                            AskFailure.OFFLINE -> R.string.ins_err_offline
                            AskFailure.TOO_LARGE -> R.string.ins_err_large
                            AskFailure.NO_POLICY -> R.string.ask_err_no_policy
                            AskFailure.OTHER -> R.string.ins_err_other
                        },
                    ),
                    Modifier.padding(top = 12.dp), color = Palette.Bad, style = MaterialTheme.typography.bodyLarge,
                )
                SoftButton(stringResource(R.string.ins_try_again), vm::resetAsk, Modifier.padding(top = 8.dp))
            }
            is AskState.Done -> AnswerCard(s.response)
        }
    }
}

@Composable
private fun AnswerCard(r: PolicyQuestionResponse) {
    val noClause = stringResource(R.string.ask_no_clause)
    val view = ClaimAnswer.requireCitations(
        r.answer, r.explanation, r.citations.map { ClaimCitation(it.clauseRef.ifBlank { "p. ${it.page}" }, it.excerpt.ifBlank { it.quote }) }, r.confidence, noClause,
    )
    var open by rememberSaveable { mutableStateOf(false) }
    val (label, icon, tint) = verdictStyle(view.verdict)
    Column(Modifier.padding(top = 14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(
            Modifier.semantics(mergeDescendants = true) { contentDescription = label }
                .then(Modifier.padding(0.dp)),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // Icon plus text, so the verdict never depends on colour alone.
            Row(
                Modifier.padding(vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(icon, null, tint = tint, modifier = Modifier.size(28.dp))
                Spacer(Modifier.width(8.dp))
                Text(label, style = MaterialTheme.typography.titleLarge, color = tint)
            }
        }
        val body = if (view.verdict == Verdict.NOT_FOUND) stringResource(R.string.ask_not_found) else view.explanation
        if (body.isNotBlank()) Text(body, style = MaterialTheme.typography.bodyLarge)
        if (view.citations.isNotEmpty()) {
            TextButton(onClick = { open = !open }, Modifier.heightIn(min = 48.dp)) {
                Icon(if (open) Icons.Default.ExpandLess else Icons.Default.ExpandMore, null)
                Spacer(Modifier.width(4.dp))
                Text(stringResource(R.string.ask_sources))
            }
            if (open) view.citations.forEach { c ->
                SoftCard(Modifier.fillMaxWidth()) {
                    Text(c.clauseRef, style = MaterialTheme.typography.titleMedium)
                    Muted(c.excerpt)
                }
            }
        }
        Muted(stringResource(R.string.ask_notice))
    }
}

@Composable
private fun verdictStyle(v: Verdict): Triple<String, ImageVector, Color> = when (v) {
    Verdict.LIKELY_COVERED -> Triple(stringResource(R.string.verdict_covered), Icons.Default.CheckCircle, Palette.Good)
    Verdict.LIKELY_NOT_COVERED -> Triple(stringResource(R.string.verdict_excluded), Icons.Default.ErrorOutline, Palette.Bad)
    Verdict.WAITING_PERIOD -> Triple(stringResource(R.string.verdict_waiting), Icons.Default.HourglassBottom, Palette.Warn)
    Verdict.NOT_FOUND -> Triple(stringResource(R.string.verdict_not_found), Icons.Default.SearchOff, Palette.Ink)
    Verdict.UNCLEAR -> Triple(stringResource(R.string.verdict_unclear), Icons.Default.HelpOutline, Palette.Warn)
}
