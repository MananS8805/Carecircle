package com.carecircle.ui

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
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
import com.carecircle.data.Person
import com.carecircle.data.Persons
import com.carecircle.data.Prefs
import com.carecircle.domain.FoodTiming
import com.carecircle.domain.Messages
import com.carecircle.ui.theme.BigButton
import com.carecircle.ui.theme.BrandRow
import com.carecircle.ui.theme.Muted
import com.carecircle.ui.theme.Palette
import com.carecircle.ui.theme.Pill
import com.carecircle.ui.theme.SoftButton
import com.carecircle.ui.theme.SoftCard

@Composable
fun TodayScreen(vm: AppViewModel) {
    val doses by vm.today.collectAsStateWithLifecycle()
    val groups by vm.bodyMap.collectAsStateWithLifecycle()
    val status by rememberReminderStatus()
    val ctx = LocalContext.current
    var onboarding by remember { mutableStateOf(!vm.prefs.onboardingDone) }

    val people by vm.people.collectAsStateWithLifecycle()
    val selection by vm.selection.collectAsStateWithLifecycle()
    val scope by vm.scopePerson.collectAsStateWithLifecycle()
    val showAll = selection == Prefs.ALL_PEOPLE && people.size > 1
    val name = if (showAll) "" else (scope?.name ?: "").takeIf { it != Persons.DEFAULT_NAME }.orEmpty()
    val taken = doses.count { it.status == DoseStatus.TAKEN }
    val next = doses.firstOrNull { it.status == DoseStatus.UPCOMING || it.status == DoseStatus.DUE }

    LazyColumn(
        Modifier.fillMaxSize().statusBarsPadding(),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        item { BrandRow() }
        item { PersonSwitcher(vm, allowAll = true) }
        item {
            Column {
                Text(
                    if (name.isBlank()) stringResource(R.string.greeting_anon) else stringResource(R.string.greeting_name, name),
                    style = MaterialTheme.typography.headlineMedium, color = Palette.Muted,
                )
                Text(stringResource(R.string.today_headline), style = MaterialTheme.typography.headlineLarge)
            }
        }
        if (vm.prefs.onboardingDone && !status.ok) item {
            SoftCard(Modifier.fillMaxWidth(), tint = Palette.WarnSoft) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.NotificationsActive, null, tint = Palette.Warn)
                    Spacer(Modifier.width(10.dp))
                    Column(Modifier.weight(1f)) {
                        Text(stringResource(R.string.banner_title), style = MaterialTheme.typography.titleMedium)
                        Text(
                            if (!status.notifications) stringResource(R.string.banner_notifications) else stringResource(R.string.banner_exact),
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                }
                SoftButton(stringResource(R.string.banner_fix), { onboarding = true }, Modifier.padding(top = 10.dp))
            }
        }
        item { OrganHero(groups, taken, doses.size, next) }
        item { Text(stringResource(R.string.today_doses), style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(top = 6.dp)) }
        if (doses.isEmpty()) item { Muted(stringResource(R.string.today_empty)) }
        val groups = if (showAll) people.map { p -> p to doses.filter { it.med.personId == p.id } }.filter { it.second.isNotEmpty() }
        else listOf(null to doses)
        groups.forEach { (person, list) ->
            if (person != null) item(key = "h-${person.id}") {
                Text(displayName(person, people), style = MaterialTheme.typography.titleMedium, color = Palette.Orange, modifier = Modifier.padding(top = 4.dp))
            }
            items(list, key = { "${it.med.id}-${it.scheduledAt}" }) { d ->
                val owner = person ?: people.firstOrNull { it.id == d.med.personId }
                DoseCard(d, vm, owner, onAlert = {
                    val who = owner?.name ?: ctx.getString(R.string.family_member_fallback)
                    val text = ctx.getString(R.string.msg_missed, who, Messages.medicineLabel(d.med.name, d.med.doseText), Fmt.time(d.scheduledAt))
                    val url = Messages.whatsAppUrl(Messages.normalizePhone(owner?.caregiverPhone.orEmpty()), text)
                    ctx.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                })
            }
        }
        item { Spacer(Modifier.height(8.dp)) }
    }

    if (onboarding) OnboardingDialog(status, onDone = { vm.prefs.onboardingDone = true; onboarding = false })
}

@Composable
private fun DoseCard(d: TodayItem, vm: AppViewModel, owner: Person?, onAlert: () -> Unit) {
    val (label, bg, fg) = when (d.status) {
        DoseStatus.TAKEN -> Triple(stringResource(R.string.status_taken), Palette.GoodSoft, Palette.Good)
        DoseStatus.MISSED -> Triple(stringResource(R.string.status_missed), Palette.BadSoft, Palette.Bad)
        DoseStatus.DUE -> Triple(stringResource(R.string.status_due), Palette.OrangeSoft, Palette.Orange)
        DoseStatus.UPCOMING -> Triple(stringResource(R.string.status_upcoming), Palette.Line, Palette.Muted)
    }
    SoftCard(Modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(Fmt.time(d.scheduledAt), fontSize = 30.sp, fontWeight = FontWeight.Light, modifier = Modifier.weight(1f))
            Pill(label, bg, fg)
        }
        Text(d.med.name, style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(top = 4.dp))
        Text(
            listOf(d.med.doseText, pluralStringResource(R.plurals.pills_count, d.med.pillsPerDose, d.med.pillsPerDose))
                .filter { it.isNotBlank() }.joinToString("  -  "),
            style = MaterialTheme.typography.bodyMedium, color = Palette.Muted,
        )
        FoodTiming.fromKey(d.med.foodTiming).takeIf { it != FoodTiming.NONE }?.let {
            Pill(stringResource(it.labelRes()), Palette.Canvas, Palette.Ink, Modifier.padding(top = 8.dp))
        }
        if (d.status == DoseStatus.DUE || d.status == DoseStatus.MISSED) {
            BigButton(stringResource(R.string.action_taken), { vm.markTaken(d) }, Modifier.padding(top = 12.dp))
        }
        if (d.status == DoseStatus.MISSED) {
            val hasNumber = !owner?.caregiverPhone.isNullOrBlank()
            SoftButton(stringResource(R.string.alert_caregiver), onAlert, Modifier.fillMaxWidth().padding(top = 8.dp), enabled = hasNumber)
            if (!hasNumber) Muted(stringResource(R.string.alert_add_number, owner?.name ?: ""), Modifier.padding(top = 4.dp))
        }
    }
}
