package com.carecircle.ui

import android.content.Context
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.carecircle.AppLocale
import com.carecircle.R
import com.carecircle.domain.WeeklySummary
import com.carecircle.ui.theme.Palette
import java.time.format.DateTimeFormatter

/**
 * The weekly summary as text, in the language of the app. Medicine names only when [includeNames] is true; otherwise just a
 * count of what is running low. Never a condition tag, and nothing is sent by the app: the user shares it.
 */
fun buildWeeklySummaryMessage(ctx: Context, personName: String, s: WeeklySummary, includeNames: Boolean): String {
    val lc = AppLocale.wrap(ctx)
    val locale = AppLocale.locale(ctx)
    val pct = s.percent ?: return lc.getString(R.string.weekly_none, personName)
    val day = DateTimeFormatter.ofPattern("EEEE d MMM", locale)
    val parts = mutableListOf(lc.getString(R.string.weekly_head, personName, pct, s.taken, s.decided))
    if (s.isPartialWeek) parts += lc.getString(R.string.weekly_partial, s.daysWithDoses)
    s.bestDay?.let { parts += lc.getString(R.string.weekly_best, it.format(day)) }
    s.worstDay?.let { parts += lc.getString(R.string.weekly_worst, it.format(day)) }
    if (s.longestStreak >= 2) parts += lc.getString(R.string.weekly_streak, s.longestStreak)
    val lowCount = s.lowStock.size + s.lowStockMore
    if (lowCount > 0) {
        parts += if (includeNames) {
            val items = s.lowStock.joinToString(", ") { lc.getString(R.string.weekly_low_item, it.label, it.daysLeft) }
            lc.getString(R.string.weekly_low_names, items + if (s.lowStockMore > 0) " " + lc.getString(R.string.weekly_more, s.lowStockMore) else "")
        } else lc.getString(R.string.weekly_low_count, lowCount)
    }
    return parts.joinToString(" ")
}

/** Shows the exact text first, with the "Include medicine names" switch (off by default), then the share sheet. */
@Composable
fun WeeklySharePreview(ctx: Context, personName: String, summary: WeeklySummary, onShare: (String) -> Unit, onCancel: () -> Unit) {
    var names by rememberSaveable { mutableStateOf(false) }
    val text = buildWeeklySummaryMessage(ctx, personName, summary, names)
    AlertDialog(
        onDismissRequest = onCancel,
        title = { Text(stringResource(R.string.weekly_preview_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(text)
                Row(
                    Modifier.fillMaxWidth().defaultMinSize(minHeight = 56.dp).toggleable(value = names, role = Role.Switch, onValueChange = { names = it }),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(stringResource(R.string.weekly_names_toggle), Modifier.weight(1f))
                    Switch(checked = names, onCheckedChange = null)
                }
                Text(stringResource(R.string.weekly_preview_note), color = Palette.Muted)
            }
        },
        confirmButton = { TextButton(onClick = { onShare(text) }) { Text(stringResource(R.string.weekly_share)) } },
        dismissButton = { TextButton(onClick = onCancel) { Text(stringResource(R.string.action_cancel)) } },
        containerColor = Palette.Surface,
    )
}
