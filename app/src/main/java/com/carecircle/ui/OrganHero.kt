package com.carecircle.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.carecircle.R
import com.carecircle.domain.OrganKind
import com.carecircle.ui.theme.OrganView
import com.carecircle.ui.theme.Palette
import com.carecircle.ui.theme.Pill
import com.carecircle.ui.theme.SoftCard

/**
 * The home hero: a living organ for each condition the person's medicines treat. Swipe between organs,
 * tap one to see which medicines act on it. Two glass tiles overlap its lower edge, like the reference design.
 */
@Composable
fun OrganHero(groups: List<OrganGroup>, taken: Int, total: Int, next: TodayItem?, modifier: Modifier = Modifier) {
    val pager = rememberPagerState { maxOf(1, groups.size) }
    var open by remember { mutableStateOf(false) }
    val current = groups.getOrNull(pager.currentPage)

    Column(modifier.fillMaxWidth()) {
        Box(Modifier.fillMaxWidth().height(350.dp)) {
            HorizontalPager(pager, Modifier.fillMaxSize()) { page ->
                val g = groups.getOrNull(page)
                val kind = g?.organ ?: OrganKind.HEART
                val name = g?.condition?.label() ?: kind.label()
                Column(Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally) {
                    Pill(
                        if (g == null) stringResource(R.string.hero_empty_pill) else "$name  -  ${kind.label()}",
                        modifier = Modifier.padding(top = 4.dp),
                    )
                    OrganView(
                        kind, Modifier.padding(top = 6.dp).size(236.dp),
                        description = stringResource(R.string.hero_organ_desc, name),
                        onTap = { open = !open },
                    )
                }
            }
            Row(
                Modifier.align(Alignment.BottomCenter).fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                HeroTile(
                    stringResource(R.string.hero_taken_today), if (total == 0) "-" else "$taken/$total",
                    if (total == 0) null else stringResource(R.string.hero_doses_scheduled, total), Modifier.weight(1f),
                )
                HeroTile(
                    stringResource(R.string.hero_next_dose), next?.let { Fmt.time(it.scheduledAt) } ?: "-",
                    next?.med?.name, Modifier.weight(1f),
                )
            }
        }
        if (groups.size > 1) {
            Row(Modifier.fillMaxWidth().padding(top = 10.dp), horizontalArrangement = Arrangement.Center) {
                repeat(groups.size) { i ->
                    Box(
                        Modifier.padding(horizontal = 3.dp).size(if (i == pager.currentPage) 18.dp else 7.dp, 7.dp).clip(CircleShape)
                            .background(if (i == pager.currentPage) Palette.Ink else Palette.Line),
                    )
                }
            }
        }
        AnimatedVisibility(open && current != null, enter = expandVertically() + fadeIn(), exit = shrinkVertically() + fadeOut()) {
            current?.let { g ->
                SoftCard(Modifier.fillMaxWidth().padding(top = 12.dp), tint = Palette.OrangeSoft) {
                    Text(
                        stringResource(R.string.hero_acts_on, g.condition?.label() ?: g.organ.label(), g.organ.label()),
                        style = MaterialTheme.typography.titleMedium,
                    )
                    Spacer(Modifier.height(6.dp))
                    g.meds.forEach { m ->
                        Text("${m.name}${if (m.doseText.isNotBlank()) "  " + m.doseText else ""}", style = MaterialTheme.typography.bodyLarge)
                    }
                }
            }
        }
        if (groups.isEmpty()) {
            Text(
                stringResource(R.string.hero_empty_hint), Modifier.padding(top = 10.dp),
                style = MaterialTheme.typography.bodyMedium, color = Palette.Muted,
            )
        } else if (!open) {
            Text(
                pluralStringResource(R.plurals.hero_tap_hint, groups.size), Modifier.padding(top = 10.dp),
                style = MaterialTheme.typography.bodySmall, color = Palette.Muted,
            )
        }
    }
}

@Composable
private fun HeroTile(label: String, value: String, sub: String?, modifier: Modifier = Modifier) {
    SoftCard(modifier) {
        Text(label, style = MaterialTheme.typography.labelMedium, color = Palette.Muted)
        Text(value, fontSize = 30.sp, fontWeight = FontWeight.Light, maxLines = 1)
        Text(sub ?: " ", style = MaterialTheme.typography.bodySmall, color = Palette.Muted, maxLines = 1)
    }
}
