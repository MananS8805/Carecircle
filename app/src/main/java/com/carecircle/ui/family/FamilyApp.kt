package com.carecircle.ui.family

import android.Manifest
import android.content.Intent
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatDelegate
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.FamilyRestroom
import androidx.compose.material.icons.filled.PersonAdd
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.os.LocaleListCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.carecircle.AppLocale
import com.carecircle.R
import com.carecircle.data.FollowResult
import com.carecircle.data.Following
import com.carecircle.data.Prefs
import com.carecircle.data.SyncEventDoc
import com.carecircle.domain.FamilyEvent
import com.carecircle.domain.FamilyView
import com.carecircle.domain.Mood
import com.carecircle.domain.OrganKind
import com.carecircle.domain.SyncType
import com.carecircle.ui.AppViewModel
import com.carecircle.ui.Fmt
import com.carecircle.ui.describeEvent
import com.carecircle.ui.label
import com.carecircle.ui.readReminderStatus
import com.carecircle.ui.theme.LogoMark
import com.carecircle.ui.theme.OrganView
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.TextStyle
import java.util.Locale

private val topLevel = setOf("fhome", "fadd", "fsettings")

/** The whole family experience: who is being followed, how today is going, and what happened recently. */
@Composable
fun FamilyApp(vm: AppViewModel) {
    val nav = rememberNavController()
    val route = nav.currentBackStackEntryAsState().value?.destination?.route
    val tabs = listOf(
        FNav("fhome", stringResource(R.string.fam_tab_home), Icons.Default.FamilyRestroom),
        FNav("fadd", stringResource(R.string.fam_tab_add), Icons.Default.PersonAdd),
        FNav("fsettings", stringResource(R.string.fam_tab_settings), Icons.Default.Settings),
    )
    androidx.compose.material3.Scaffold(
        containerColor = FamilyColors.Bg,
        bottomBar = { if (route == null || route in topLevel) FNavBar(tabs, route) { r -> nav.navigate(r) { launchSingleTop = true; popUpTo("fhome") } } },
    ) { pad ->
        Box(Modifier.fillMaxSize().padding(bottom = pad.calculateBottomPadding())) {
            NavHost(nav, startDestination = "fhome") {
                composable("fhome") { FamilyHome(vm, onOpen = { nav.navigate("fpatient/$it") }, onAdd = { nav.navigate("fadd") }) }
                composable("fpatient/{id}") { e -> PatientScreen(vm, e.arguments?.getString("id").orEmpty(), onBack = { nav.popBackStack() }) }
                composable("fadd") { AddMember(vm, onDone = { nav.navigate("fhome") { popUpTo("fhome") } }) }
                composable("fsettings") { FamilySettings(vm) }
            }
        }
    }
}

@Composable
private fun Header(title: String, sub: String? = null) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        LogoMark(30.dp)
        Spacer(Modifier.width(10.dp))
        Column {
            Text(title, fontSize = 26.sp, fontWeight = FontWeight.Medium, color = FamilyColors.Ink)
            if (sub != null) Text(sub, fontSize = 14.sp, color = FamilyColors.Muted)
        }
    }
}

@Composable
private fun FButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true) {
    Box(
        modifier.fillMaxWidth().heightIn(min = 56.dp).clip(CircleShape).background(if (enabled) FamilyColors.Teal else FamilyColors.Line)
            .clickable(enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) { Text(text, color = if (enabled) Color.White else FamilyColors.Muted, fontSize = 17.sp, fontWeight = FontWeight.Medium) }
}

@Composable
private fun moodLabel(mood: Mood, missed: Int): String = when (mood) {
    Mood.ALL_TAKEN -> stringResource(R.string.fam_mood_all)
    Mood.ON_TRACK -> stringResource(R.string.fam_mood_track)
    Mood.MISSED -> stringResource(R.string.fam_mood_missed, missed)
    Mood.NO_DATA -> stringResource(R.string.fam_mood_nodata)
}

private fun moodColors(mood: Mood): Pair<Color, Color> = when (mood) {
    Mood.ALL_TAKEN -> FamilyColors.Good to FamilyColors.GoodSoft
    Mood.ON_TRACK -> FamilyColors.Teal to FamilyColors.TealSoft
    Mood.MISSED -> FamilyColors.Bad to FamilyColors.BadSoft
    Mood.NO_DATA -> FamilyColors.Muted to FamilyColors.Line
}

@Composable
private fun FamilyHome(vm: AppViewModel, onOpen: (String) -> Unit, onAdd: () -> Unit) {
    val following by vm.following.collectAsStateWithLifecycle()
    val ctx = LocalContext.current
    var notifOk by remember { mutableStateOf(readReminderStatus(ctx).notifications) }
    val perm = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { notifOk = it }
    LazyColumn(Modifier.fillMaxSize().statusBarsPadding(), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        item { Header(stringResource(R.string.fam_home_title), stringResource(R.string.fam_home_sub)) }
        if (!notifOk && android.os.Build.VERSION.SDK_INT >= 33) item {
            FCard(Modifier.fillMaxWidth(), tint = FamilyColors.WarnSoft) {
                Text(stringResource(R.string.fam_notifications_hint), color = FamilyColors.Ink, fontSize = 15.sp)
                Spacer(Modifier.height(10.dp))
                FButton(stringResource(R.string.onb_allow), { perm.launch(Manifest.permission.POST_NOTIFICATIONS) })
            }
        }
        if (following.isEmpty()) item {
            FCard(Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.fam_empty_title), fontSize = 20.sp, fontWeight = FontWeight.Medium, color = FamilyColors.Ink)
                Text(stringResource(R.string.fam_empty_text), Modifier.padding(top = 6.dp, bottom = 14.dp), color = FamilyColors.Muted, fontSize = 15.sp)
                FButton(stringResource(R.string.fam_add), onAdd)
            }
        }
        items(following, key = { it.linkId }) { f -> PatientCard(f, vm) { onOpen(f.linkId) } }
    }
}

@Composable
private fun PatientCard(f: Following, vm: AppViewModel, onClick: () -> Unit) {
    val statusFlow = remember(f.linkId) { vm.status(f.linkId) }
    val status by statusFlow.collectAsStateWithLifecycle(null)
    val fresh = status?.day == LocalDate.now().toEpochDay()
    val taken = if (fresh) status?.taken ?: 0 else 0
    val total = if (fresh) status?.total ?: 0 else 0
    val missed = if (fresh) status?.missed ?: 0 else 0
    val mood = FamilyView.mood(taken, total, missed, fresh)
    val (color, soft) = moodColors(mood)
    FCard(Modifier.fillMaxWidth(), onClick = onClick) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            FRing(FamilyView.fraction(taken, total), 88.dp, 11.dp, color) {
                Text(if (total == 0) "-" else "$taken/$total", fontSize = 22.sp, fontWeight = FontWeight.Light, color = FamilyColors.Ink)
            }
            Spacer(Modifier.width(16.dp))
            Column(Modifier.weight(1f)) {
                Text(f.personName.ifBlank { stringResource(R.string.family_member_fallback) }, fontSize = 22.sp, fontWeight = FontWeight.Medium, color = FamilyColors.Ink)
                FChip(moodLabel(mood, missed), soft, color, Modifier.padding(top = 6.dp))
                val s = status
                if (fresh && s != null) Text(
                    if (s.nextAt > 0) stringResource(R.string.fam_next, s.nextMedicine, Fmt.time(s.nextAt)) else stringResource(R.string.fam_none_next),
                    Modifier.padding(top = 6.dp), fontSize = 14.sp, color = FamilyColors.Muted, maxLines = 1,
                )
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun PatientScreen(vm: AppViewModel, linkId: String, onBack: () -> Unit) {
    val following by vm.following.collectAsStateWithLifecycle()
    val f = following.firstOrNull { it.linkId == linkId }
    val ctx = LocalContext.current
    val statusFlow = remember(linkId) { vm.status(linkId) }
    val eventsFlow = remember(linkId) { vm.events(linkId) }
    val status by statusFlow.collectAsStateWithLifecycle(null)
    val events by eventsFlow.collectAsStateWithLifecycle(emptyList())
    var ringFlip by remember { mutableStateOf(false) }
    var organIdx by remember { mutableIntStateOf(0) }
    var showMeds by remember { mutableStateOf(false) }
    var pickedDay by remember { mutableStateOf<LocalDate?>(null) }
    var confirmStop by remember { mutableStateOf(false) }

    val fresh = status?.day == LocalDate.now().toEpochDay()
    val taken = if (fresh) status?.taken ?: 0 else 0
    val total = if (fresh) status?.total ?: 0 else 0
    val missed = if (fresh) status?.missed ?: 0 else 0
    val mood = FamilyView.mood(taken, total, missed, fresh)
    val (color, soft) = moodColors(mood)
    val zone = ZoneId.systemDefault()
    val familyEvents = events.map { FamilyEvent(it.type, it.at, it.medicine) }
    val days = FamilyView.days(familyEvents, LocalDate.now(), 14, zone)
    val organs = FamilyView.organs(events.filter { it.type != SyncType.SUMMARY }.map { it.medicine })
    val organ = organs[organIdx.coerceIn(0, organs.lastIndex)]
    val name = f?.personName?.ifBlank { null } ?: stringResource(R.string.family_member_fallback)

    LazyColumn(Modifier.fillMaxSize().statusBarsPadding(), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        item { TextButton(onClick = onBack) { Text(stringResource(R.string.action_back), color = FamilyColors.Teal) } }
        item {
            Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                Text(name, fontSize = 28.sp, fontWeight = FontWeight.Medium, color = FamilyColors.Ink)
                Spacer(Modifier.height(14.dp))
                FRing(FamilyView.fraction(taken, total), 200.dp, 20.dp, color, Modifier.clickable { ringFlip = !ringFlip }) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        val s = status
                        if (ringFlip && s != null) {
                            Text(if (s.nextAt > 0) Fmt.time(s.nextAt) else "-", fontSize = 34.sp, fontWeight = FontWeight.Light, color = FamilyColors.Ink)
                            Text(if (s.nextAt > 0) s.nextMedicine else stringResource(R.string.fam_none_next), fontSize = 14.sp, color = FamilyColors.Muted, maxLines = 2, modifier = Modifier.padding(horizontal = 20.dp))
                        } else {
                            Text(if (total == 0) "-" else "$taken/$total", fontSize = 48.sp, fontWeight = FontWeight.Light, color = FamilyColors.Ink)
                            Text(stringResource(R.string.fam_ring_label), fontSize = 14.sp, color = FamilyColors.Muted)
                        }
                    }
                }
                Spacer(Modifier.height(10.dp))
                FChip(moodLabel(mood, missed), soft, color)
                Text(stringResource(R.string.fam_ring_tap), Modifier.padding(top = 8.dp), fontSize = 13.sp, color = FamilyColors.Muted)
                status?.takeIf { it.updatedAt > 0 }?.let {
                    Text(stringResource(R.string.fam_updated, Fmt.time(it.updatedAt)), fontSize = 12.sp, color = FamilyColors.Muted)
                }
            }
        }
        item {
            FCard(Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.fam_organs_title), fontSize = 18.sp, fontWeight = FontWeight.Medium, color = FamilyColors.Ink)
                if (organs.size > 1) Row(Modifier.padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    organs.forEachIndexed { i, o ->
                        FilterChip(
                            selected = organIdx == i, onClick = { organIdx = i; showMeds = false }, label = { Text(o.label()) },
                            modifier = Modifier.heightIn(min = 48.dp), shape = CircleShape,
                            colors = FilterChipDefaults.filterChipColors(selectedContainerColor = FamilyColors.Teal, selectedLabelColor = Color.White),
                        )
                    }
                }
                Box(Modifier.fillMaxWidth().padding(top = 8.dp), contentAlignment = Alignment.Center) {
                    OrganView(organ, Modifier.size(190.dp), description = stringResource(R.string.hero_organ_desc, organ.label()), onTap = { showMeds = !showMeds })
                }
                if (showMeds) {
                    val meds = FamilyView.medicinesFor(organ, events.filter { it.type != SyncType.SUMMARY }.map { it.medicine })
                    Text(stringResource(R.string.hero_acts_on, organ.label(), organ.label()), fontWeight = FontWeight.Medium, color = FamilyColors.Ink)
                    meds.ifEmpty { listOf(stringResource(R.string.fam_organ_unknown)) }.forEach { Text(it, Modifier.padding(top = 2.dp), color = FamilyColors.Ink) }
                } else Text(stringResource(R.string.fam_organ_tap), fontSize = 13.sp, color = FamilyColors.Muted)
            }
        }
        item {
            FCard(Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.fam_14days), fontSize = 18.sp, fontWeight = FontWeight.Medium, color = FamilyColors.Ink)
                val maxTotal = (days.maxOfOrNull { it.total } ?: 1).coerceAtLeast(1)
                Row(Modifier.padding(top = 10.dp).horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    days.forEach { d ->
                        Column(
                            Modifier.clip(RoundedCornerShape(10.dp)).clickable { pickedDay = d.date }
                                .background(if (d.date == pickedDay) FamilyColors.TealSoft else Color.Transparent).padding(horizontal = 5.dp, vertical = 4.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                        ) {
                            Column(Modifier.width(18.dp).height(80.dp), verticalArrangement = Arrangement.Bottom) {
                                if (d.total == 0) Box(Modifier.fillMaxWidth().height(2.dp).background(FamilyColors.Line))
                                else Column(Modifier.clip(RoundedCornerShape(5.dp))) {
                                    if (d.missed > 0) Box(Modifier.fillMaxWidth().height(80.dp * d.missed / maxTotal).background(FamilyColors.Bad))
                                    if (d.skipped > 0) Box(Modifier.fillMaxWidth().height(80.dp * d.skipped / maxTotal).background(FamilyColors.Muted))
                                    if (d.taken > 0) Box(Modifier.fillMaxWidth().height(80.dp * d.taken / maxTotal).background(FamilyColors.Good))
                                }
                            }
                            Text(d.date.dayOfWeek.getDisplayName(TextStyle.NARROW, Locale.getDefault()), fontSize = 11.sp, color = FamilyColors.Muted)
                            Text("${d.date.dayOfMonth}", fontSize = 11.sp, color = FamilyColors.Ink)
                        }
                    }
                }
                pickedDay?.let { p -> days.firstOrNull { it.date == p }?.let {
                    Text(stringResource(R.string.fam_day_summary, p.dayOfMonth.toString(), it.taken, it.missed, it.skipped), Modifier.padding(top = 8.dp), color = FamilyColors.Ink)
                } }
            }
        }
        item { Text(stringResource(R.string.fam_timeline), fontSize = 18.sp, fontWeight = FontWeight.Medium, color = FamilyColors.Ink) }
        if (events.isEmpty()) item { Text(stringResource(R.string.family_events_none), color = FamilyColors.Muted) }
        items(events.take(30), key = { it.id }) { e -> TimelineRow(e, ctx) }
        item { TextButton(onClick = { confirmStop = true }) { Text(stringResource(R.string.family_stop_following), color = FamilyColors.Bad) } }
    }
    if (confirmStop) AlertDialog(
        onDismissRequest = { confirmStop = false },
        title = { Text(stringResource(R.string.fam_stop_title, name)) },
        text = { Text(stringResource(R.string.fam_stop_text)) },
        confirmButton = { TextButton(onClick = { confirmStop = false; vm.unfollow(linkId); onBack() }) { Text(stringResource(R.string.family_stop_following), color = FamilyColors.Bad) } },
        dismissButton = { TextButton(onClick = { confirmStop = false }) { Text(stringResource(R.string.action_cancel)) } },
        containerColor = FamilyColors.Surface,
    )
}

@Composable
private fun TimelineRow(e: SyncEventDoc, ctx: android.content.Context) {
    val dot = when (e.type) { SyncType.TAKEN -> FamilyColors.Good; SyncType.MISSED -> FamilyColors.Bad; SyncType.SKIPPED -> FamilyColors.Muted; SyncType.SUMMARY -> FamilyColors.Teal }
    Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(FamilyColors.Surface).padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(12.dp).clip(CircleShape).background(dot))
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(describeEvent(ctx, e), color = FamilyColors.Ink, fontSize = 15.sp)
            val day = java.time.Instant.ofEpochMilli(e.at).atZone(ZoneId.systemDefault()).toLocalDate()
            Text(day.format(java.time.format.DateTimeFormatter.ofLocalizedDate(java.time.format.FormatStyle.MEDIUM)), color = FamilyColors.Muted, fontSize = 12.sp)
        }
    }
}

@Composable
private fun AddMember(vm: AppViewModel, onDone: () -> Unit) {
    val result by vm.followResult.collectAsStateWithLifecycle()
    var code by rememberSaveable { mutableStateOf("") }
    Column(Modifier.fillMaxSize().statusBarsPadding().padding(16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Header(stringResource(R.string.fam_add))
        FCard(Modifier.fillMaxWidth()) {
            Text(stringResource(R.string.fam_add_help), color = FamilyColors.Muted, fontSize = 15.sp)
            OutlinedTextField(
                code, { code = it.take(12) }, Modifier.fillMaxWidth().padding(top = 12.dp), singleLine = true, shape = MaterialTheme.shapes.medium,
                label = { Text(stringResource(R.string.family_follow_hint)) },
            )
            Spacer(Modifier.height(12.dp))
            FButton(stringResource(R.string.family_follow_button), { vm.follow(code) }, enabled = code.isNotBlank())
            result?.let { r ->
                val ok = r is FollowResult.Ok
                Text(
                    when (r) {
                        is FollowResult.Ok -> stringResource(R.string.family_followed, r.personName)
                        FollowResult.Invalid -> stringResource(R.string.family_err_invalid)
                        FollowResult.NotFound -> stringResource(R.string.family_err_notfound)
                        FollowResult.Expired -> stringResource(R.string.family_err_expired)
                        FollowResult.Taken -> stringResource(R.string.family_err_taken)
                        FollowResult.NotConfigured -> stringResource(R.string.family_not_configured)
                        FollowResult.Offline -> stringResource(R.string.family_err_offline)
                        FollowResult.Error -> stringResource(R.string.family_err_error)
                    },
                    Modifier.padding(top = 10.dp), color = if (ok) FamilyColors.Good else FamilyColors.Bad, fontSize = 15.sp,
                )
                if (ok) TextButton(onClick = { code = ""; onDone() }) { Text(stringResource(R.string.action_done), color = FamilyColors.Teal) }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun FamilySettings(vm: AppViewModel) {
    val ctx = LocalContext.current
    var lang by remember { mutableStateOf(vm.prefs.language) }
    Column(Modifier.fillMaxSize().statusBarsPadding().verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Header(stringResource(R.string.fam_tab_settings))
        FCard(Modifier.fillMaxWidth()) {
            Text(stringResource(R.string.settings_language), fontSize = 18.sp, fontWeight = FontWeight.Medium, color = FamilyColors.Ink)
            FlowRow(Modifier.padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                AppLocale.supported.forEach { (tag, label) ->
                    FilterChip(
                        selected = lang == tag,
                        onClick = { lang = tag; vm.prefs.language = tag; AppCompatDelegate.setApplicationLocales(LocaleListCompat.forLanguageTags(tag)) },
                        label = { Text(if (tag.isEmpty()) stringResource(R.string.language_system) else label) },
                        modifier = Modifier.heightIn(min = 48.dp), shape = CircleShape,
                        colors = FilterChipDefaults.filterChipColors(selectedContainerColor = FamilyColors.Teal, selectedLabelColor = Color.White),
                    )
                }
            }
        }
        FCard(Modifier.fillMaxWidth()) {
            Text(stringResource(R.string.fam_notifications), fontSize = 18.sp, fontWeight = FontWeight.Medium, color = FamilyColors.Ink)
            Text(stringResource(R.string.fam_notifications_hint), Modifier.padding(vertical = 6.dp), color = FamilyColors.Muted, fontSize = 15.sp)
            FButton(stringResource(R.string.onb_open_settings), {
                ctx.startActivity(Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE, ctx.packageName).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            })
        }
        FCard(Modifier.fillMaxWidth()) {
            Text(stringResource(R.string.fam_settings_switch), fontSize = 18.sp, fontWeight = FontWeight.Medium, color = FamilyColors.Ink)
            Text(stringResource(R.string.fam_settings_switch_hint), Modifier.padding(vertical = 6.dp), color = FamilyColors.Muted, fontSize = 15.sp)
            FButton(stringResource(R.string.fam_settings_switch), { vm.setMode(Prefs.MODE_PATIENT) })
        }
        Text(stringResource(R.string.fam_about), color = FamilyColors.Muted, fontSize = 13.sp)
    }
}
