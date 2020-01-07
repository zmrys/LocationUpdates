package com.jacknephilim.locationupdates

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.graphics.Bitmap
import android.media.RingtoneManager
import android.os.Build
import android.provider.Settings
import androidx.annotation.ColorRes
import androidx.annotation.DrawableRes
import androidx.annotation.RequiresApi
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import androidx.core.content.getSystemService

class EzNotification(val context: Context, val data: EzNotificationData) {

    protected var notificationManager: NotificationManager? = context.getSystemService()

    init {
        if (isAndroidOOrHigher()) {
            createNotificationChannel()
        }
    }

    private fun isAndroidOOrHigher(): Boolean {
        return Build.VERSION.SDK_INT >= Build.VERSION_CODES.O
    }

    fun getNotification(): Notification {
        return NotificationCompat.Builder(context, data.channelId)
            .setColor(ContextCompat.getColor(context, data.color))
            .setSmallIcon(data.smallIcon)
            .setLargeIcon(data.largeIcon)
            .setContentIntent(data.intent)
            .setContentTitle(data.title)
            .setContentText(data.text)
            .setPriority(data.priority)
            .setOngoing(data.ongoing)
            .setTicker(data.ticker)
            .setSound(Settings.System.DEFAULT_NOTIFICATION_URI)
            .setAutoCancel(true)
            .setWhen(data.setWhen)
            .addAction(
                data.onServiceAction.icon,
                data.onServiceAction.title,
                data.onServiceAction.intent
            )
            .addAction(
                data.onActivityAction.icon,
                data.onActivityAction.title,
                data.onActivityAction.intent
            )
            .build()
    }


    fun show(id: Int) {
        val noti = NotificationCompat.Builder(context, data.channelId)
            .setColor(ContextCompat.getColor(context, data.color))
            .setSmallIcon(data.smallIcon)
            .setLargeIcon(data.largeIcon)
            .setContentIntent(data.intent)
            .setContentTitle(data.title)
            .setContentText(data.text)
            .setPriority(data.priority)
            .setOngoing(data.ongoing)
            .setTicker(data.ticker)
            .setSound(Settings.System.DEFAULT_NOTIFICATION_URI)
            .setAutoCancel(true)
            .setWhen(data.setWhen)
            .addAction(
                data.onServiceAction.icon,
                data.onServiceAction.title,
                data.onServiceAction.intent
            )
            .addAction(
                data.onActivityAction.icon,
                data.onActivityAction.title,
                data.onActivityAction.intent
            )
            .build()
        notificationManager?.notify(id, noti)

        val uri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
        RingtoneManager.getRingtone(context, uri).play()
    }

    fun show(id: Int,notification: Notification){
        notificationManager?.notify(id, notification)
        val uri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
        RingtoneManager.getRingtone(context, uri).play()
    }

    fun update(id: Int) {
//        notificationManager?.notify(id, noti)
    }

    @RequiresApi(Build.VERSION_CODES.O)
    private fun createNotificationChannel() {
        if (notificationManager!!.getNotificationChannel(data.channelId) == null) {
            val notificationChannel = NotificationChannel(
                data.channelId,
                data.channelName,
                NotificationManager.IMPORTANCE_DEFAULT
            )

            notificationChannel.description = data.channelDescription
            notificationManager?.createNotificationChannel(notificationChannel)
        }
    }

}

data class EzNotificationData(
    val channelId: String = "Default",
    val channelName: String = "Default",
    val channelDescription: String = "Default",
    @ColorRes val color: Int = R.color.colorAccent,
    @DrawableRes val smallIcon: Int,
    val largeIcon: Bitmap? = null,
    val title: String = "",
    val text: String = "",
    val ongoing: Boolean = false,
    val priority: Int = Notification.PRIORITY_HIGH,
    val ticker: String = "",
    val setWhen: Long = 0,
    val onServiceAction: NotificationAction,
    val onActivityAction: NotificationAction,
    val intent: PendingIntent? = null
)


data class NotificationAction(
    val icon: Int, val title: String, val intent: PendingIntent
)