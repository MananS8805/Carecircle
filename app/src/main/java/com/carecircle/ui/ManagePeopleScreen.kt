package com.carecircle.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PersonAdd
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.carecircle.R
import com.carecircle.data.Person
import com.carecircle.data.Persons
import com.carecircle.ui.theme.BigButton
import com.carecircle.ui.theme.Muted
import com.carecircle.ui.theme.Palette
import com.carecircle.ui.theme.SoftCard

/** Add, rename, edit contacts of, and delete people. Contacts never leave the phone except in a message you send. */
@Composable
fun ManagePeopleScreen(vm: AppViewModel, onBack: () -> Unit) {
    val people by vm.people.collectAsStateWithLifecycle()
    var deleting by remember { mutableStateOf<Person?>(null) }

    LazyColumn(
        Modifier.fillMaxSize().statusBarsPadding(),
        contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item { TextButton(onClick = onBack) { Text(stringResource(R.string.action_back)) } }
        item { Text(stringResource(R.string.people_manage), style = MaterialTheme.typography.headlineLarge) }
        item { Muted(stringResource(R.string.people_intro)) }
        items(people, key = { it.id }) { p ->
            PersonEditor(p, people, vm, canDelete = people.size > 1, onChange = vm::updatePerson, onDelete = { deleting = p })
        }
        item {
            val newName = stringResource(R.string.people_new_name, people.size + 1)
            BigButton(stringResource(R.string.people_add), { vm.addPerson(newName) }, icon = Icons.Default.PersonAdd)
        }
    }

    deleting?.let { p ->
        var count by remember(p.id) { mutableIntStateOf(0) }
        LaunchedEffect(p.id) { count = vm.medicineCount(p.id) }
        AlertDialog(
            onDismissRequest = { deleting = null },
            title = { Text(stringResource(R.string.people_delete_title, p.name)) },
            text = { Text(pluralStringResource(R.plurals.people_delete_text, count, count)) },
            confirmButton = { TextButton(onClick = { vm.deletePerson(p.id); deleting = null }) { Text(stringResource(R.string.action_delete), color = Palette.Bad) } },
            dismissButton = { TextButton(onClick = { deleting = null }) { Text(stringResource(R.string.action_cancel)) } },
            containerColor = Palette.Surface,
        )
    }
}

@Composable
private fun PersonEditor(p: Person, all: List<Person>, vm: AppViewModel, canDelete: Boolean, onChange: (Person) -> Unit, onDelete: () -> Unit) {
    // Edits are applied as you type; the key keeps the fields in step with the saved person.
    var name by remember(p.id) { mutableStateOf(p.name) }
    var caregiver by remember(p.id) { mutableStateOf(p.caregiverPhone) }
    var pharmacy by remember(p.id) { mutableStateOf(p.pharmacyPhone) }
    var caregiver2 by remember(p.id) { mutableStateOf(p.caregiver2Phone.orEmpty()) }
    fun push(n: String = name, c: String = caregiver, ph: String = pharmacy, c2: String = caregiver2) =
        onChange(p.copy(name = n.take(Persons.MAX_NAME), caregiverPhone = c, pharmacyPhone = ph, caregiver2Phone = c2.ifBlank { null }))
    SoftCard(Modifier.fillMaxWidth()) {
        Text(displayName(p, all), style = MaterialTheme.typography.titleLarge)
        PhoneField(name, { name = it.take(Persons.MAX_NAME); push(n = name) }, stringResource(R.string.field_person_name), KeyboardType.Text)
        PhoneField(caregiver, { caregiver = it; push(c = it) }, stringResource(R.string.field_caregiver), KeyboardType.Phone)
        PhoneField(caregiver2, { caregiver2 = it; push(c2 = it) }, stringResource(R.string.field_caregiver2), KeyboardType.Phone)
        PhoneField(pharmacy, { pharmacy = it; push(ph = it) }, stringResource(R.string.field_pharmacy), KeyboardType.Phone)
        AllergySection(p.id, vm)
        if (canDelete) TextButton(onClick = onDelete) { Text(stringResource(R.string.people_delete), color = Palette.Bad) }
        else Muted(stringResource(R.string.people_last_one), Modifier.padding(top = 6.dp))
    }
}

@Composable
private fun PhoneField(value: String, onChange: (String) -> Unit, label: String, type: KeyboardType) {
    OutlinedTextField(
        value, onChange, Modifier.fillMaxWidth().padding(top = 8.dp), label = { Text(label) }, singleLine = true,
        shape = MaterialTheme.shapes.medium, keyboardOptions = KeyboardOptions(keyboardType = type),
    )
}
