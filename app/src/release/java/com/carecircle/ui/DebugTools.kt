package com.carecircle.ui

import androidx.compose.runtime.Composable

/** Release builds have no debug tools: this does nothing. The real one lives in the debug source set. */
object DebugTools {
    @Composable
    fun CrashButton() {}
}
