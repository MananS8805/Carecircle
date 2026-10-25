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

object Notifier {
    const val CH_DOSE = "doses"
    const val CH_ALERT = "alerts"

    fun ensureChannels(ctx: Context) {
        val nm = ctx.getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(NotificationChannel(CH_DOSE, "Dose reminders", NotificationManager.IMPORTANCE_HIGH))
        nm.createNotificationChannel(NotificationChannel(CH_ALERT, "Missed dose & refill alerts", NotificationManager.IMPORTANCE_HIGH))
    }

    private fun canPost(ctx: Context) =
        android.os.Build.VERSION.SDK_INT < 33 ||
            ContextCompat.checkSelfPermission(ctx, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED

    private fun openApp(ctx: Context) = PendingIntent.getActivity(
        ctx, 0, Intent(ctx, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
    )

    fun doseDue(ctx: Context, doseId: Long, title: String, text: String) {
        if (!canPost(ctx)) return
        ensureChannels(ctx)
        val taken = PendingIntent.getBroadcast(
            ctx, doseId.toInt(), Intent(ctx, TakenActionReceiver::class.java).putExtra(EXTRA_DOSE_ID, doseId),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val n = NotificationCompat.Builder(ctx, CH_DOSE)
            .setSmallIcon(R.drawable.ic_notif).setContentTitle(title).setContentText(text)
            .setPriority(NotificationCompat.PRIORITY_HIGH).setCategory(NotificationCompat.CATEGORY_REMINDER)
            .setContentIntent(openApp(ctx)).setAutoCancel(true)
            .addAction(0, "Taken", taken).build()
        NotificationManagerCompat.from(ctx).notify(notifId(doseId), n)
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
