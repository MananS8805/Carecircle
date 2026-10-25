package com.carecircle.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.carecircle.R
import com.carecircle.data.Prefs
import com.carecircle.ui.theme.LogoMark
import com.carecircle.ui.theme.Muted
import com.carecircle.ui.theme.Palette
import com.carecircle.ui.theme.SoftCard

/** First launch: are you tracking medicines, or following someone who is? The choice can be changed in Settings. */
@Composable
fun RoleChooser(onPick: (String) -> Unit) {
    Column(Modifier.fillMaxSize().statusBarsPadding().padding(20.dp), verticalArrangement = Arrangement.Center) {
        LogoMark(56.dp)
        Spacer(Modifier.height(16.dp))
        Text(stringResource(R.string.role_title), style = MaterialTheme.typography.headlineLarge)
        Muted(stringResource(R.string.role_hint), Modifier.padding(top = 6.dp, bottom = 20.dp))
        SoftCard(Modifier.fillMaxWidth(), onClick = { onPick(Prefs.MODE_PATIENT) }) {
            Text(stringResource(R.string.role_patient_title), style = MaterialTheme.typography.titleLarge)
            Muted(stringResource(R.string.role_patient_text), Modifier.padding(top = 4.dp))
        }
        Spacer(Modifier.height(14.dp))
        SoftCard(Modifier.fillMaxWidth(), onClick = { onPick(Prefs.MODE_FAMILY) }, tint = Palette.OrangeSoft) {
            Text(stringResource(R.string.role_family_title), style = MaterialTheme.typography.titleLarge)
            Muted(stringResource(R.string.role_family_text), Modifier.padding(top = 4.dp))
        }
    }
}
