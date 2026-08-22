package com.amazontracker

import android.app.Application
import androidx.work.*
import com.amazontracker.data.AppDatabase
import com.amazontracker.service.PriceCheckWorker
import com.amazontracker.util.NotificationHelper
import java.util.concurrent.TimeUnit

class AmazonTrackerApp : Application() {

    val database: AppDatabase by lazy { AppDatabase.getInstance(this) }

    override fun onCreate() {
        super.onCreate()
        NotificationHelper.createChannel(this)
        schedulePriceChecks()
    }

    fun schedulePriceChecks() {
        val prefs = getSharedPreferences("amazon_tracker_prefs", MODE_PRIVATE)
        val intervalHours = prefs.getInt("check_interval_hours", 6)

        val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .setRequiresBatteryNotLow(true)
            .build()

        val workRequest = PeriodicWorkRequestBuilder<PriceCheckWorker>(
            intervalHours.toLong(), TimeUnit.HOURS, 15, TimeUnit.MINUTES
        )
            .setConstraints(constraints)
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 15, TimeUnit.MINUTES)
            .build()

        WorkManager.getInstance(this).enqueueUniquePeriodicWork(
            "price_check",
            ExistingPeriodicWorkPolicy.UPDATE,
            workRequest
        )
    }
}
