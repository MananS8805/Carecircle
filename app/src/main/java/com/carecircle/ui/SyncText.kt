package com.carecircle.ui

import android.content.Context
import com.carecircle.R
import com.carecircle.data.SyncEventDoc
import com.carecircle.domain.SyncType

/** One line for a family-sync event, in the language of the phone that shows it. */
fun describeEvent(ctx: Context, e: SyncEventDoc): String = if (e.personName.isBlank()) describePlain(ctx, e) else when (e.type) {
    SyncType.TAKEN -> ctx.getString(R.string.sync_ev_taken, e.personName, e.medicine, Fmt.time(e.at))
    SyncType.MISSED -> ctx.getString(R.string.sync_ev_missed, e.personName, e.medicine, Fmt.time(if (e.scheduledAt > 0) e.scheduledAt else e.at))
    SyncType.SKIPPED -> ctx.getString(R.string.sync_ev_skipped, e.personName, e.medicine)
    SyncType.SUMMARY -> ctx.getString(R.string.sync_ev_summary, e.personName, e.taken, e.total)
}

/** Without names (the default): "Dose taken at 8:05 AM", where the shared medicine field holds only the dose time. */
private fun describePlain(ctx: Context, e: SyncEventDoc): String = when (e.type) {
    SyncType.TAKEN -> ctx.getString(R.string.sync_plain_taken, e.medicine.ifBlank { Fmt.time(e.scheduledAt.takeIf { it > 0 } ?: e.at) }, Fmt.time(e.at))
    SyncType.MISSED -> ctx.getString(R.string.sync_plain_missed, e.medicine.ifBlank { Fmt.time(e.scheduledAt.takeIf { it > 0 } ?: e.at) })
    SyncType.SKIPPED -> ctx.getString(R.string.sync_plain_skipped, e.medicine.ifBlank { Fmt.time(e.scheduledAt.takeIf { it > 0 } ?: e.at) })
    SyncType.SUMMARY -> ctx.getString(R.string.sync_plain_summary, e.taken, e.total)
}
