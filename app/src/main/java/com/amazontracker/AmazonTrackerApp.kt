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

    private fun schedulePriceChecks() {
        val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .build()

        val workRequest = PeriodicWorkRequestBuilder<PriceCheckWorker>(
            1, TimeUnit.HOURS, 15, TimeUnit.MINUTES
        )
            .setConstraints(constraints)
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 15, TimeUnit.MINUTES)
            .build()

        WorkManager.getInstance(this).enqueueUniquePeriodicWork(
            "price_check",
            ExistingPeriodicWorkPolicy.KEEP,
            workRequest
        )
    }
}
