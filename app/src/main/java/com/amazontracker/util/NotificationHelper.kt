package com.amazontracker.util

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import com.amazontracker.R
import com.amazontracker.ui.MainActivity

object NotificationHelper {

    const val CHANNEL_ID = "price_alerts"
    const val CHANNEL_NAME = "Price Alerts"

    fun createChannel(context: Context) {
        val channel = NotificationChannel(
            CHANNEL_ID,
            CHANNEL_NAME,
            NotificationManager.IMPORTANCE_HIGH
        ).apply {
            description = "Alerts when tracked product prices change"
            enableVibration(true)
        }
        val manager = context.getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(channel)
    }

    fun showPriceAlert(
        context: Context,
        notificationId: Int,
        productName: String,
        currentPrice: Double,
        targetPrice: Double,
        isAbove: Boolean,
        currency: String = "EUR"
    ) {
        val formatted = UIUtils.formatPrice(currentPrice, currency)
        val targetFormatted = UIUtils.formatPrice(targetPrice, currency)

        val title = if (isAbove) "Price increased!" else "Price dropped!"
        val body = if (isAbove) {
            "\"$productName\" is now $formatted (was above $targetFormatted)"
        } else {
            "\"$productName\" is now $formatted (target was $targetFormatted)"
        }

        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pendingIntent = PendingIntent.getActivity(
            context, notificationId, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_dialog_alert)
            .setContentTitle(title)
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .build()

        val manager = context.getSystemService(NotificationManager::class.java)
        manager.notify(notificationId, notification)
    }

    fun showPriceDropNotification(
        context: Context,
        notificationId: Int,
        productName: String,
        oldPrice: Double,
        newPrice: Double,
        percentDrop: Double,
        currency: String = "EUR"
    ) {
        val formatted = UIUtils.formatPrice(newPrice, currency)
        val title = "Price dropped by %.1f%%!".format(percentDrop)
        val body = "\"$productName\" dropped to $formatted"

        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pendingIntent = PendingIntent.getActivity(
            context, notificationId, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle(title)
            .setContentText(body)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .build()

        val manager = context.getSystemService(NotificationManager::class.java)
        manager.notify(notificationId, notification)
    }
}
