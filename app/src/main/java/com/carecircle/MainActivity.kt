package com.carecircle

import android.Manifest
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.LaunchedEffect
import androidx.lifecycle.viewmodel.compose.viewModel
import com.carecircle.reminder.Notifier
import com.carecircle.reminder.ReminderScheduler
import com.carecircle.ui.AppViewModel
import com.carecircle.ui.CareCircleApp

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Notifier.ensureChannels(this)
        setContent {
            MaterialTheme {
                val vm: AppViewModel = viewModel()
                val perm = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {}
                LaunchedEffect(Unit) {
                    if (android.os.Build.VERSION.SDK_INT >= 33) perm.launch(Manifest.permission.POST_NOTIFICATIONS)
                    ReminderScheduler.scheduleAll(applicationContext)
                }
                CareCircleApp(vm)
            }
        }
    }
}
