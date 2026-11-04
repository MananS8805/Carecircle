package com.carecircle.ui

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.carecircle.ui.theme.SoftButton

/** Debug builds only: a button that crashes the app, to test the opt-in local crash log. The release build has an empty version. */
object DebugTools {
    @Composable
    fun CrashButton() {
        SoftButton("Crash now (debug build only)", { throw IllegalStateException("Deliberate crash from the debug menu") }, Modifier.fillMaxWidth())
    }
}
