package com.carecircle.ui

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.carecircle.R
import com.carecircle.data.BackupCrypto
import com.carecircle.ui.theme.Palette
import java.text.DateFormat
import java.util.Date

/** The restore preview ("People: 2, medicines: 9, ... exported <date>") and the password prompt for an encrypted file. */
@Composable
fun RestoreDialogs(vm: AppViewModel) {
    val preview by vm.restorePreview.collectAsStateWithLifecycle()
    val needPassword by vm.needPassword.collectAsStateWithLifecycle()
    val wrong by vm.wrongPassword.collectAsStateWithLifecycle()
    var pw by remember { mutableStateOf("") }

    preview?.let { f ->
        AlertDialog(
            onDismissRequest = vm::cancelRestore,
            title = { Text(stringResource(R.string.data_restore_confirm_title)) },
            text = {
                Text(
                    stringResource(
                        R.string.restore_counts, f.people.size, f.medicines.size, f.doses.size,
                        DateFormat.getDateInstance(DateFormat.MEDIUM).format(Date(f.exportedAt)),
                    ),
                )
            },
            confirmButton = { TextButton(onClick = vm::confirmRestore) { Text(stringResource(R.string.data_restore_replace)) } },
            dismissButton = { TextButton(onClick = vm::cancelRestore) { Text(stringResource(R.string.action_cancel)) } },
            containerColor = Palette.Surface,
        )
    }
    needPassword?.let {
        AlertDialog(
            onDismissRequest = { pw = ""; vm.cancelPassword() },
            title = { Text(stringResource(R.string.backup_enter_password_title)) },
            text = {
                androidx.compose.foundation.layout.Column {
                    Text(stringResource(R.string.backup_enter_password_text))
                    AppField(pw, { pw = it }, stringResource(R.string.backup_password), androidx.compose.ui.Modifier, isError = wrong)
                    if (wrong) Text(stringResource(R.string.backup_wrong_password), color = Palette.Bad)
                }
            },
            confirmButton = { TextButton(onClick = { val p = pw; pw = ""; vm.submitPassword(p) }, enabled = pw.isNotEmpty()) { Text(stringResource(R.string.backup_unlock)) } },
            dismissButton = { TextButton(onClick = { pw = ""; vm.cancelPassword() }) { Text(stringResource(R.string.action_cancel)) } },
            containerColor = Palette.Surface,
        )
    }
}

/** Optional password for a backup file. The password lives only in memory until the file is written. */
@Composable
fun ExportPasswordDialog(onChoose: (String?) -> Unit, onCancel: () -> Unit) {
    var pw by remember { mutableStateOf("") }
    val ok = BackupCrypto.validPassword(pw)
    AlertDialog(
        onDismissRequest = onCancel,
        title = { Text(stringResource(R.string.backup_protect_title)) },
        text = {
            androidx.compose.foundation.layout.Column {
                Text(stringResource(R.string.backup_protect_text))
                AppField(pw, { pw = it }, stringResource(R.string.backup_password), androidx.compose.ui.Modifier, isError = pw.isNotEmpty() && !ok)
                if (pw.isNotEmpty() && !ok) Text(stringResource(R.string.backup_password_short, BackupCrypto.MIN_PASSWORD), color = Palette.Bad)
            }
        },
        confirmButton = { TextButton(onClick = { onChoose(pw) }, enabled = ok) { Text(stringResource(R.string.backup_save_encrypted)) } },
        dismissButton = {
            androidx.compose.foundation.layout.Row {
                TextButton(onClick = onCancel) { Text(stringResource(R.string.action_cancel)) }
                TextButton(onClick = { onChoose(null) }) { Text(stringResource(R.string.backup_save_plain)) }
            }
        },
        containerColor = Palette.Surface,
    )
}
