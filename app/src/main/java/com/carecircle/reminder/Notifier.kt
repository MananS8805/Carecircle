package com.carecircle.reminder

import android.Manifest
import android.annotation.SuppressLint
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.carecircle.MainActivity
import com.carecircle.AppLocale
import com.carecircle.R
import com.carecircle.data.DoseEvent
import com.carecircle.data.Medicine
import com.carecircle.domain.Escalation
import com.carecircle.domain.FoodTiming
import com.carecircle.ui.labelRes

object Notifier {
    const val CH_DOSE = "doses"
    const val CH_ALERT = "alerts"
    const val CH_URGENT = "doses_urgent"

    fun ensureChannels(ctx: Context) {
        val nm = ctx.getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(NotificationChannel(CH_DOSE, ctx.getString(R.string.channel_doses), NotificationManager.IMPORTANCE_HIGH))
        nm.createNotificationChannel(NotificationChannel(CH_ALERT, ctx.getString(R.string.channel_alerts), NotificationManager.IMPORTANCE_HIGH))
        // Final reminder before the family is told: alarm sound and a strong vibration.
        nm.createNotificationChannel(
            NotificationChannel(CH_URGENT, ctx.getString(R.string.channel_urgent), NotificationManager.IMPORTANCE_HIGH).apply {
                enableVibration(true)
                vibrationPattern = longArrayOf(0, 500, 250, 500, 250, 800)
                setSound(
                    android.media.RingtoneManager.getDefaultUri(android.media.RingtoneManager.TYPE_ALARM),
                    android.media.AudioAttributes.Builder().setUsage(android.media.AudioAttributes.USAGE_ALARM)
                        .setContentType(android.media.AudioAttributes.CONTENT_TYPE_SONIFICATION).build(),
                )
            },
        )
    }

    private fun canPost(ctx: Context) =
        android.os.Build.VERSION.SDK_INT < 33 ||
            ContextCompat.checkSelfPermission(ctx, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED

    private fun openApp(ctx: Context) = PendingIntent.getActivity(
        ctx, 0, Intent(ctx, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
    )

    /** Posts (or re-posts after a snooze) the reminder for one dose. The notification id is derived from the dose id. */
    @SuppressLint("MissingPermission") // canPost() checks POST_NOTIFICATIONS first
    fun postDose(base: Context, dose: DoseEvent, med: Medicine, personLabel: String? = null, level: Int = 0) {
        if (!canPost(base)) return
        val ctx = AppLocale.wrap(base)
        ensureChannels(ctx)
        val flags = PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        val taken = PendingIntent.getBroadcast(
            ctx, dose.id.toInt(), Intent(ctx, TakenActionReceiver::class.java).putExtra(EXTRA_DOSE_ID, dose.id), flags,
        )
        val snooze = PendingIntent.getBroadcast(
            ctx, dose.id.toInt(),
            Intent(ctx, SnoozeReceiver::class.java).setAction(SnoozeReceiver.ACTION_SNOOZE).putExtra(EXTRA_DOSE_ID, dose.id), flags,
        )
        val doseLabel = med.doseText.ifBlank { ctx.getString(R.string.notif_dose_fallback) }
        val food = FoodTiming.fromKey(med.foodTiming)
        val base0 = if (food == FoodTiming.NONE) ctx.getString(R.string.notif_dose_text, doseLabel)
        else ctx.getString(R.string.notif_dose_text_food, doseLabel, ctx.getString(food.labelRes()))
        // Repeats tell the patient how long is left before the family is told.
        val text = if (level >= 1) base0 + "\n" + ctx.getString(R.string.notif_family_soon, Escalation.minutesUntilAlert(level)) else base0
        val n = NotificationCompat.Builder(ctx, if (level >= 2) CH_URGENT else CH_DOSE)
            .setSmallIcon(R.drawable.ic_notif).setContentTitle(if (personLabel == null) ctx.getString(R.string.notif_time_for, med.name) else ctx.getString(R.string.notif_time_for_person, med.name, personLabel)).setContentText(text)
            .setColor(0xFFF26A21.toInt())
            .setPriority(NotificationCompat.PRIORITY_HIGH).setCategory(NotificationCompat.CATEGORY_REMINDER)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text)).setOnlyAlertOnce(false)
            .setContentIntent(openApp(ctx)).setAutoCancel(true)
            .addAction(0, ctx.getString(R.string.action_taken), taken)
            .addAction(0, ctx.getString(R.string.action_snooze), snooze).build()
        NotificationManagerCompat.from(ctx).notify(notifId(dose.id), n)
    }

    /** Tapping opens WhatsApp (or any browser fallback) with a pre-filled message. No backend involved. */
    @SuppressLint("MissingPermission") // canPost() checks POST_NOTIFICATIONS first
    fun linkAlert(ctx: Context, id: Int, title: String, text: String, url: String) {
        if (!canPost(ctx)) return
        ensureChannels(ctx)
        val pi = PendingIntent.getActivity(
            ctx, id, Intent(Intent.ACTION_VIEW, Uri.parse(url)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val n = NotificationCompat.Builder(ctx, CH_ALERT)
            .setSmallIcon(R.drawable.ic_notif).setContentTitle(title).setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setPriority(NotificationCompat.PRIORITY_HIGH).setContentIntent(pi).setAutoCancel(true).build()
        NotificationManagerCompat.from(ctx).notify(id, n)
    }

    /** A notification that just opens the app, used for updates about someone the family member follows. */
    @SuppressLint("MissingPermission") // canPost() checks POST_NOTIFICATIONS first
    fun plainAlert(base: Context, id: Int, title: String, text: String) {
        if (!canPost(base)) return
        val ctx = AppLocale.wrap(base)
        ensureChannels(ctx)
        val n = NotificationCompat.Builder(ctx, CH_ALERT)
            .setSmallIcon(R.drawable.ic_notif).setContentTitle(title).setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text)).setColor(0xFFF26A21.toInt())
            .setPriority(NotificationCompat.PRIORITY_HIGH).setContentIntent(openApp(ctx)).setAutoCancel(true).build()
        NotificationManagerCompat.from(ctx).notify(id, n)
    }

    fun cancelDose(ctx: Context, doseId: Long) = NotificationManagerCompat.from(ctx).cancel(notifId(doseId))

    fun notifId(doseId: Long) = (doseId % 1_000_000).toInt() + 1000
    const val EXTRA_DOSE_ID = "doseId"
    const val EXTRA_MED_ID = "medId"
    const val EXTRA_LEVEL = "level"
    const val EXTRA_AT = "scheduledAt"
}
