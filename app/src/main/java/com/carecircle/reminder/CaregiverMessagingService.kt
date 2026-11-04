package com.carecircle.reminder

import com.carecircle.R
import com.carecircle.AppLocale
import com.carecircle.data.AppDb
import com.carecircle.data.Prefs
import com.carecircle.data.PushTokens
import com.carecircle.domain.CaregiverAlert
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import java.time.LocalDate

/**
 * Family phone: receives the data-only "missed" push and builds the visible notification itself, from the dose time only.
 * Nothing in the push names a person or a medicine. Shown once per link, dose time and day.
 */
class CaregiverMessagingService : FirebaseMessagingService() {
    override fun onNewToken(token: String) {
        CoroutineScope(Dispatchers.IO).launch { PushTokens.sync(applicationContext, knownToken = token) }
    }

    override fun onMessageReceived(message: RemoteMessage) {
        if (message.data["type"] != "missed") return
        val label = CaregiverAlert.sanitizeLabel(message.data["slotLabel"]) ?: return
        val linkId = message.data["linkId"] ?: return
        val prefs = Prefs(this)
        val followed = runBlocking(Dispatchers.IO) { AppDb.get(this@CaregiverMessagingService).sync().following().map { it.linkId }.toSet() }
        val today = LocalDate.now()
        val key = CaregiverAlert.key(today, linkId, label)
        val alerted = CaregiverAlert.decode(prefs.alertedSlots)
        if (!CaregiverAlert.shouldNotify(prefs.pushEnabled, followed, linkId, key, alerted)) return
        prefs.alertedSlots = CaregiverAlert.encode(CaregiverAlert.remember(alerted, key, today))
        val lc = AppLocale.wrap(this)
        Notifier.caregiverAlert(this, key.hashCode(), lc.getString(R.string.notif_cg_title), lc.getString(R.string.notif_cg_text, label))
    }
}
