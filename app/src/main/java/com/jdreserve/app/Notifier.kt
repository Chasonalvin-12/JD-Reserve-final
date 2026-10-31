package com.jdreserve.app

import android.Manifest
import android.app.Activity
import android.app.Application
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build

/**
 * Push-style notifications for guests: booking received, confirmed, declined, cancelled.
 *
 * These are LOCAL notifications raised by the app itself (this lab has no server). Swapping in
 * Firebase Cloud Messaging later only changes where the event comes from; the channel, permission
 * and display code below stay the same.
 */
object Notifier {
    const val CHANNEL_ID = "booking_updates"
    const val REQUEST_CODE_PERMISSION = 4101

    private var askedThisRun = false

    fun createChannel(context: Context) {
        val manager = context.getSystemService(NotificationManager::class.java) ?: return
        val channel = NotificationChannel(CHANNEL_ID, "Booking updates", NotificationManager.IMPORTANCE_HIGH)
        channel.description = "Confirmations, declines and reminders about your J & D Reserve bookings"
        manager.createNotificationChannel(channel)
    }

    /** Android 13+ (API 33) needs the POST_NOTIFICATIONS permission; older versions do not. */
    fun canPost(context: Context): Boolean =
        Build.VERSION.SDK_INT < 33 ||
            context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED

    /** Asks once per app run, and only when the permission is actually missing. */
    fun askPermissionIfNeeded(activity: Activity) {
        if (askedThisRun || canPost(activity)) return
        askedThisRun = true
        if (Build.VERSION.SDK_INT >= 33) {
            activity.requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), REQUEST_CODE_PERMISSION)
        }
    }

    fun show(context: Context, n: AppNotification) {
        if (!canPost(context)) return
        val manager = context.getSystemService(NotificationManager::class.java) ?: return

        val open = Intent(context, MyBookingsActivity::class.java)
        open.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        val pending = PendingIntent.getActivity(
            context, n.id, open, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val notification = Notification.Builder(context, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle(n.title)
            .setContentText(n.body)
            .setStyle(Notification.BigTextStyle().bigText(n.body))
            .setContentIntent(pending)
            .setAutoCancel(true)
            .build()
        manager.notify(n.id, notification)
    }
}

/** Runs once when the app process starts: creates the channel and connects the repository to Notifier. */
class JDApp : Application() {
    override fun onCreate() {
        super.onCreate()
        Notifier.createChannel(this)
        Repository.notificationSink = { n ->
            // Staff alerts stay in the in-app inbox; only guest alerts become system notifications.
            if (n.audience != Repository.STAFF_AUDIENCE) Notifier.show(this, n)
        }
    }
}
