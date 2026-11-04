package com.carecircle

import android.content.Context
import com.carecircle.data.BackupService
import com.carecircle.data.Prefs
import kotlinx.coroutines.runBlocking

/**
 * Test hook: puts the app in a known state before an instrumented test. It lives only in the debug source set, so it cannot
 * exist in a release build; `scripts/check-release.sh` looks for this class name in the release dex and fails if it is there.
 */
object DebugReset {
    /** Wipes every registered store, then picks the patient app. [guideDone] true skips the first-run guide. */
    fun reset(ctx: Context, guideDone: Boolean = true) {
        runBlocking { BackupService(ctx).deleteAll() }
        Prefs(ctx).apply {
            appMode = Prefs.MODE_PATIENT
            this.guideDone = guideDone
            onboardingDone = guideDone
            if (guideDone) whatsNewSeen = BuildConfig.VERSION_CODE
        }
    }
}
