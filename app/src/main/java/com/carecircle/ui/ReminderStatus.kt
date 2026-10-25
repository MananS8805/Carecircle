package com.carecircle.ui

import android.Manifest
import android.app.AlarmManager
import android.content.Context
import android.content.pm.PackageManager
import android.os.PowerManager
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner

/** What the phone currently allows. Re-read whenever the app comes back to the foreground. */
data class ReminderStatus(val notifications: Boolean, val exactAlarms: Boolean, val batteryUnrestricted: Boolean) {
    /** Battery optimisation is only a hint, so it does not count towards the warning banner. */
    val ok get() = notifications && exactAlarms
}

fun readReminderStatus(ctx: Context): ReminderStatus {
    val notif = android.os.Build.VERSION.SDK_INT < 33 ||
        ContextCompat.checkSelfPermission(ctx, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
    val exact = android.os.Build.VERSION.SDK_INT < 31 || ctx.getSystemService(AlarmManager::class.java).canScheduleExactAlarms()
    val battery = ctx.getSystemService(PowerManager::class.java).isIgnoringBatteryOptimizations(ctx.packageName)
    return ReminderStatus(notif, exact, battery)
}

@Composable
fun rememberReminderStatus(): State<ReminderStatus> {
    val ctx = LocalContext.current
    val state = remember { mutableStateOf(readReminderStatus(ctx)) }
    val owner = LocalLifecycleOwner.current
    DisposableEffect(owner) {
        val obs = LifecycleEventObserver { _, e -> if (e == Lifecycle.Event.ON_RESUME) state.value = readReminderStatus(ctx) }
        owner.lifecycle.addObserver(obs)
        onDispose { owner.lifecycle.removeObserver(obs) }
    }
    return state
}
