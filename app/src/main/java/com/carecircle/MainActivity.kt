package com.carecircle

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.carecircle.domain.Display
import com.carecircle.reminder.Notifier
import com.carecircle.reminder.ReminderScheduler
import com.carecircle.ui.AppViewModel
import com.carecircle.ui.CareCircleApp
import com.carecircle.ui.theme.CareCircleTheme
import com.carecircle.ui.theme.Palette

class MainActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        Notifier.ensureChannels(this)
        com.carecircle.reminder.FollowerWorker.ensure(this)
        setContent {
            val vm: AppViewModel = viewModel()
            val display by vm.display.collectAsStateWithLifecycle()
            Palette.highContrast = display.highContrast
            // The phone's font size times our text-size choice, capped at 2x in all. Applies at once, to both experiences.
            val base = LocalDensity.current
            val density = Density(base.density, Display.effectiveFontScale(base.fontScale, display.textSize))
            CompositionLocalProvider(LocalDensity provides density) {
                CareCircleTheme {
                    LaunchedEffect(Unit) {
                        ReminderScheduler.rescheduleAll(applicationContext, "app_open")
                        com.carecircle.reminder.RescheduleWorker.ensurePeriodic(applicationContext)
                        vm.refreshStatuses()
                    }
                    val mode by vm.appMode.collectAsStateWithLifecycle()
                    when (mode) {
                        com.carecircle.data.Prefs.MODE_FAMILY -> com.carecircle.ui.family.FamilyApp(vm)
                        com.carecircle.data.Prefs.MODE_PATIENT -> CareCircleApp(vm)
                        else -> com.carecircle.ui.RoleChooser(onPick = vm::setMode)
                    }
                }
            }
        }
    }
}
