package com.carecircle.ui

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.carecircle.R
import com.carecircle.data.InteractionPair
import com.carecircle.data.InteractionResult
import com.carecircle.ui.theme.Muted
import com.carecircle.ui.theme.Palette
import com.carecircle.ui.theme.SoftButton
import com.carecircle.ui.theme.SoftCard

@Composable
fun InteractionScreen(vm: AppViewModel, onBack: () -> Unit) {
    val result by vm.interactions.collectAsStateWithLifecycle()
    val loading by vm.interactionsLoading.collectAsStateWithLifecycle()
    LaunchedEffect(Unit) { vm.checkInteractions() }

    LazyColumn(
        Modifier.fillMaxSize().statusBarsPadding(),
        contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item { TextButton(onClick = onBack) { Text(stringResource(R.string.action_back)) } }
        item { Text(stringResource(R.string.inter_title), style = MaterialTheme.typography.headlineLarge) }
        item { Muted(stringResource(R.string.inter_intro)) }
        if (loading) item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                CircularProgressIndicator(Modifier.size(24.dp), strokeWidth = 3.dp, color = Palette.Orange)
                Spacer(Modifier.width(12.dp))
                Muted(stringResource(R.string.inter_loading))
            }
        }
        when (val r = result) {
            is InteractionResult.Ok -> {
                if (r.fromCache) item { Notice(stringResource(R.string.inter_offline), Palette.WarnSoft) }
                if (r.response.pairs.isEmpty()) item { Notice(stringResource(R.string.inter_none), Palette.Surface) }
                items(r.response.pairs) { PairCard(it) }
                if (r.response.unresolved.isNotEmpty()) item {
                    Notice(stringResource(R.string.inter_unresolved, r.response.unresolved.joinToString(", ")), Palette.Surface)
                }
            }
            InteractionResult.TooFew -> item { Notice(stringResource(R.string.inter_too_few), Palette.Surface) }
            InteractionResult.RateLimited -> item { Notice(stringResource(R.string.info_rate_limited), Palette.WarnSoft, stringResource(R.string.action_retry)) { vm.checkInteractions() } }
            InteractionResult.Error -> item { Notice(stringResource(R.string.inter_error), Palette.BadSoft, stringResource(R.string.action_retry)) { vm.checkInteractions() } }
            null -> {}
        }
        item { Text(stringResource(R.string.disclaimer_interactions), style = MaterialTheme.typography.labelMedium, color = Palette.Muted) }
    }
}

@Composable
private fun Notice(text: String, tint: androidx.compose.ui.graphics.Color, action: String? = null, onAction: () -> Unit = {}) {
    SoftCard(Modifier.fillMaxWidth(), tint = tint) {
        Text(text, style = MaterialTheme.typography.bodyLarge)
        if (action != null) SoftButton(action, onAction, Modifier.padding(top = 10.dp))
    }
}

@Composable
private fun PairCard(p: InteractionPair) {
    val ctx = LocalContext.current
    SoftCard(Modifier.fillMaxWidth()) {
        Text("${p.a.replaceFirstChar { it.uppercase() }}  +  ${p.b.replaceFirstChar { it.uppercase() }}", style = MaterialTheme.typography.titleLarge)
        Text(p.snippet, Modifier.padding(top = 8.dp), style = MaterialTheme.typography.bodyMedium)
        Text(
            p.source, Modifier.padding(top = 10.dp).clickable(enabled = p.sourceUrl != null) {
                ctx.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(p.sourceUrl)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            },
            style = MaterialTheme.typography.bodySmall, color = if (p.sourceUrl != null) Palette.Orange else Palette.Muted,
        )
    }
}
