package com.carecircle

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.LaunchedEffect
import androidx.lifecycle.viewmodel.compose.viewModel
import com.carecircle.reminder.Notifier
import com.carecircle.reminder.ReminderScheduler
import com.carecircle.ui.AppViewModel
import com.carecircle.ui.CareCircleApp
import com.carecircle.ui.theme.CareCircleTheme

class MainActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        Notifier.ensureChannels(this)
        setContent {
            CareCircleTheme {
                val vm: AppViewModel = viewModel()
                LaunchedEffect(Unit) { ReminderScheduler.scheduleAll(applicationContext) }
                CareCircleApp(vm)
            }
        }
    }
}
