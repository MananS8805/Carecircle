package com.carecircle.reminder

import android.Manifest
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
import com.carecircle.R
import com.carecircle.data.DoseEvent
import com.carecircle.data.Medicine

object Notifier {
    const val CH_DOSE = "doses"
    const val CH_ALERT = "alerts"

    fun ensureChannels(ctx: Context) {
        val nm = ctx.getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(NotificationChannel(CH_DOSE, ctx.getString(R.string.channel_doses), NotificationManager.IMPORTANCE_HIGH))
        nm.createNotificationChannel(NotificationChannel(CH_ALERT, ctx.getString(R.string.channel_alerts), NotificationManager.IMPORTANCE_HIGH))
    }

    private fun canPost(ctx: Context) =
        android.os.Build.VERSION.SDK_INT < 33 ||
            ContextCompat.checkSelfPermission(ctx, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED

    private fun openApp(ctx: Context) = PendingIntent.getActivity(
        ctx, 0, Intent(ctx, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
    )

    /** Posts (or re-posts after a snooze) the reminder for one dose. The notification id is derived from the dose id. */
    fun postDose(ctx: Context, dose: DoseEvent, med: Medicine) {
        if (!canPost(ctx)) return
        ensureChannels(ctx)
        val flags = PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        val taken = PendingIntent.getBroadcast(
            ctx, dose.id.toInt(), Intent(ctx, TakenActionReceiver::class.java).putExtra(EXTRA_DOSE_ID, dose.id), flags,
        )
        val snooze = PendingIntent.getBroadcast(
            ctx, dose.id.toInt(),
            Intent(ctx, SnoozeReceiver::class.java).setAction(SnoozeReceiver.ACTION_SNOOZE).putExtra(EXTRA_DOSE_ID, dose.id), flags,
        )
        val text = ctx.getString(R.string.notif_dose_text, med.doseText.ifBlank { ctx.getString(R.string.notif_dose_fallback) })
        val n = NotificationCompat.Builder(ctx, CH_DOSE)
            .setSmallIcon(R.drawable.ic_notif).setContentTitle(ctx.getString(R.string.notif_time_for, med.name)).setContentText(text)
            .setColor(0xFFF26A21.toInt())
            .setPriority(NotificationCompat.PRIORITY_HIGH).setCategory(NotificationCompat.CATEGORY_REMINDER)
            .setContentIntent(openApp(ctx)).setAutoCancel(true)
            .addAction(0, ctx.getString(R.string.action_taken), taken)
            .addAction(0, ctx.getString(R.string.action_snooze), snooze).build()
        NotificationManagerCompat.from(ctx).notify(notifId(dose.id), n)
    }

    /** Tapping opens WhatsApp (or any browser fallback) with a pre-filled message. No backend involved. */
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

    fun cancelDose(ctx: Context, doseId: Long) = NotificationManagerCompat.from(ctx).cancel(notifId(doseId))

    fun notifId(doseId: Long) = (doseId % 1_000_000).toInt() + 1000
    const val EXTRA_DOSE_ID = "doseId"
    const val EXTRA_MED_ID = "medId"
    const val EXTRA_AT = "scheduledAt"
}
