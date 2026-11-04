package com.carecircle.reminder

import com.carecircle.R
import com.carecircle.AppLocale
import com.carecircle.data.AppDb
import com.carecircle.data.Prefs
import com.carecircle.data.PushTokens
import com.carecircle.domain.AlertDecision
import com.carecircle.domain.CaregiverAlert
import com.carecircle.domain.HeldAlert
import com.carecircle.domain.HeldAlerts
import com.carecircle.domain.QuietHours
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import java.time.LocalDate
import java.time.ZoneId

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
        // Quiet hours and snooze hold the alert for one digest; a push that arrives far too late is dropped.
        when (QuietHours.shouldShow(System.currentTimeMillis(), ZoneId.systemDefault(), QuietDigest.settings(this), message.sentTime)) {
            AlertDecision.DROP -> return
            AlertDecision.HOLD -> {
                prefs.heldAlerts = HeldAlerts.encode(HeldAlerts.add(HeldAlerts.decode(prefs.heldAlerts), HeldAlert(today, label, linkId)))
                QuietDigest.scheduleRelease(this)
                return
            }
            AlertDecision.SHOW -> {}
        }
        val lc = AppLocale.wrap(this)
        Notifier.caregiverAlert(this, key.hashCode(), lc.getString(R.string.notif_cg_title), lc.getString(R.string.notif_cg_text, label))
    }
}
