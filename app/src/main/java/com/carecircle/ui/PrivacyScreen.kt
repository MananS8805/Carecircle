package com.carecircle.ui

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.carecircle.BuildConfig
import com.carecircle.R
import com.carecircle.ui.theme.Muted
import com.carecircle.ui.theme.Palette
import com.carecircle.ui.theme.SoftButton
import com.carecircle.ui.theme.SoftCard

/**
 * What stays on the phone, what can leave and when, what is never sent, what the server logs, and the controls.
 * Keep this page, `docs/privacy-policy.md` and `docs/play-store/data-safety-answers.md` in step with `data/Api.kt`.
 */
@Composable
fun PrivacyScreen(vm: AppViewModel, onBack: () -> Unit) {
    val ctx = LocalContext.current
    var deleteStep by remember { mutableStateOf(0) }
    LazyColumn(Modifier.fillMaxSize().statusBarsPadding(), contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item { TextButton(onClick = onBack) { Text(stringResource(R.string.action_back)) } }
        item { Text(stringResource(R.string.privacy_title), style = MaterialTheme.typography.headlineLarge) }
        item { Muted(stringResource(R.string.privacy_intro)) }
        item { Block(R.string.privacy_local_title, listOf(R.string.privacy_local_text)) }
        item {
            Block(
                R.string.privacy_leaves_title,
                listOf(R.string.privacy_leave_cards, R.string.privacy_leave_policy, R.string.privacy_leave_question, R.string.privacy_leave_sync, R.string.privacy_leave_voice, R.string.privacy_leave_messages),
            )
        }
        item { Block(R.string.privacy_never_title, listOf(R.string.privacy_never_text)) }
        item { Block(R.string.privacy_logs_title, listOf(R.string.privacy_logs_text)) }
        item { Block(R.string.privacy_controls_title, listOf(R.string.privacy_controls_text)) }
        item { SoftButton(stringResource(R.string.data_delete_all), { deleteStep = 1 }, Modifier.fillMaxWidth()) }
        item {
            val email = BuildConfig.SUPPORT_EMAIL
            val url = BuildConfig.PRIVACY_URL
            SoftCard(Modifier.fillMaxWidth()) {
                // TODO(release): set careCircleSupportEmail and careCirclePrivacyUrl in gradle.properties.
                Text(if (email.isBlank()) stringResource(R.string.privacy_contact_unset) else stringResource(R.string.privacy_contact, email), style = MaterialTheme.typography.bodyLarge)
                if (url.isNotBlank()) SoftButton(stringResource(R.string.privacy_open_web), {
                    ctx.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                }, Modifier.padding(top = 8.dp))
            }
        }
        item { Text(stringResource(R.string.disclaimer_medical), style = MaterialTheme.typography.labelMedium, color = Palette.Muted) }
    }
    DeleteAllFlow(vm, deleteStep) { deleteStep = it }
}

@Composable
private fun Block(title: Int, lines: List<Int>) {
    SoftCard(Modifier.fillMaxWidth()) {
        Text(stringResource(title), style = MaterialTheme.typography.titleLarge)
        Column(Modifier.padding(top = 6.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            lines.forEach { Text(stringResource(it), style = MaterialTheme.typography.bodyLarge) }
        }
    }
}
