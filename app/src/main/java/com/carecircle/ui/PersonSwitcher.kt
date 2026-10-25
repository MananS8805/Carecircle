package com.carecircle.ui

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.carecircle.R
import com.carecircle.data.Person
import com.carecircle.data.Prefs
import com.carecircle.ui.theme.Palette

/** Chips to switch between people. Hidden when there is only one person. Same names show with their order number. */
@Composable
fun PersonSwitcher(vm: AppViewModel, allowAll: Boolean, modifier: Modifier = Modifier) {
    val people by vm.people.collectAsStateWithLifecycle()
    val selection by vm.selection.collectAsStateWithLifecycle()
    val scope by vm.scopePerson.collectAsStateWithLifecycle()
    if (people.size < 2) return
    val activeId = if (allowAll && selection == Prefs.ALL_PEOPLE) Prefs.ALL_PEOPLE else scope?.id
    LazyRow(modifier, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        if (allowAll) item {
            PersonChip(stringResource(R.string.people_all), activeId == Prefs.ALL_PEOPLE) { vm.selectPerson(Prefs.ALL_PEOPLE) }
        }
        items(people, key = { it.id }) { p ->
            PersonChip(displayName(p, people), activeId == p.id) { vm.selectPerson(p.id) }
        }
    }
}

@Composable
private fun PersonChip(label: String, selected: Boolean, onClick: () -> Unit) {
    FilterChip(
        selected = selected, onClick = onClick, label = { Text(label) },
        modifier = Modifier.heightIn(min = 48.dp).padding(vertical = 0.dp), shape = CircleShape,
        colors = FilterChipDefaults.filterChipColors(
            selectedContainerColor = Palette.Ink, selectedLabelColor = Color.White, containerColor = Palette.Surface,
        ),
    )
}

/** Duplicate names are allowed; the later ones get their position, e.g. "Mom (2)". */
fun displayName(p: Person, all: List<Person>): String {
    val same = all.filter { it.name == p.name }
    return if (same.size < 2) p.name else "${p.name} (${same.indexOf(p) + 1})"
}
