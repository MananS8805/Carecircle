package com.carecircle.reminder

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.carecircle.AppLocale
import com.carecircle.R
import com.carecircle.data.CareSync
import com.carecircle.data.Prefs
import com.carecircle.domain.Acks
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.ZoneOffset

/**
 * The "Mark as handled" button on a missed-dose alert. Works without opening the app: it writes one acknowledgement
 * (queued by Firestore when offline) and replaces the alert with a quiet "Marked as handled". A second tap changes nothing.
 */
class AckReceiver : BroadcastReceiver() {
    override fun onReceive(ctx: Context, intent: Intent) {
        val linkId = intent.getStringExtra(EXTRA_LINK) ?: return
        val slotId = intent.getStringExtra(EXTRA_SLOT) ?: return
        val notifId = intent.getIntExtra(EXTRA_NOTIF, 0)
        val key = Acks.slotKeyFromId(slotId) ?: return
        val pending = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val prefs = Prefs(ctx)
                val mine = prefs.ackedSlots.lines().filter { it.isNotBlank() }
                if (key !in mine) {
                    CareSync.writeAck(linkId, key)
                    com.carecircle.data.DiagLog.log(ctx, com.carecircle.domain.DiagCode.ACK_SENT)
                    prefs.ackedSlots = Acks.pruneOld(mine + key, LocalDate.now(ZoneOffset.UTC)).joinToString("\n")
                }
                val lc = AppLocale.wrap(ctx)
                Notifier.caregiverAlert(ctx, notifId, lc.getString(R.string.ack_done_title), lc.getString(R.string.ack_done_text))
            } finally {
                pending.finish()
            }
        }
    }

    companion object {
        const val EXTRA_LINK = "linkId"
        const val EXTRA_SLOT = "slotId"
        const val EXTRA_NOTIF = "notifId"
    }
}
