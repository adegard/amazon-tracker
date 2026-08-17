package com.amazontracker.service

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.amazontracker.data.AppDatabase
import com.amazontracker.data.PriceAlert
import com.amazontracker.data.PriceEntry
import com.amazontracker.util.NotificationHelper
import com.amazontracker.util.PriceParser
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.TimeUnit

class PriceCheckWorker(
    context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {

    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .followRedirects(true)
        .followSslRedirects(true)
        .addInterceptor { chain ->
            val request = chain.request().newBuilder()
                .header("User-Agent", "Mozilla/5.0 (Linux; Android 14; Pixel 8 Pro) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/125.0.0.0 Mobile Safari/537.36")
                .header("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8")
                .header("Accept-Language", "it-IT,it;q=0.9,en-US;q=0.8,en;q=0.7")
                .build()
            chain.proceed(request)
        }
        .build()

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        try {
            val db = AppDatabase.getInstance(applicationContext)
            val products = db.trackedProductDao().getAll().first()

            for ((index, product) in products.withIndex()) {
                try {
                    checkProductPrice(product, db, index)
                } catch (_: Exception) {}
            }
            Result.success()
        } catch (_: Exception) {
            Result.retry()
        }
    }

    private suspend fun checkProductPrice(
        product: com.amazontracker.data.TrackedProduct,
        db: AppDatabase,
        notificationIndex: Int
    ) {
        val request = Request.Builder().url(product.url).build()
        val response = client.newCall(request).execute()
        val html = response.body?.string() ?: return
        val parsed = PriceParser.parsePrice(html, product.url) ?: return

        val oldPrice = product.currentPrice
        val newPrice = parsed.price
        if (newPrice <= 0) return

        db.priceHistoryDao().insert(
            PriceEntry(productId = product.id, price = newPrice, currency = parsed.currency)
        )

        val updatedProduct = product.copy(
            currentPrice = newPrice,
            lowestPrice = if (product.lowestPrice == 0.0 || newPrice < product.lowestPrice) newPrice else product.lowestPrice,
            highestPrice = if (newPrice > product.highestPrice) newPrice else product.highestPrice,
            lastChecked = System.currentTimeMillis(),
            name = if (parsed.productName.isNotEmpty()) parsed.productName else product.name
        )
        db.trackedProductDao().update(updatedProduct)

        if (oldPrice > 0 && oldPrice != newPrice) {
            val percentChange = ((oldPrice - newPrice) / oldPrice) * 100

            if (percentChange >= 5.0) {
                NotificationHelper.showPriceDropNotification(
                    applicationContext,
                    (product.id * 100 + 1).toInt(),
                    product.name,
                    oldPrice,
                    newPrice,
                    percentChange,
                    parsed.currency
                )
            }

            val alerts = db.priceAlertDao().getAlertsForProduct(product.id).first()
            for (alert in alerts) {
                if (!alert.isActive) continue
                val triggered = when {
                    alert.isAbove -> newPrice >= alert.targetPrice
                    else -> newPrice <= alert.targetPrice
                }
                if (triggered) {
                    NotificationHelper.showPriceAlert(
                        applicationContext,
                        (product.id * 100 + 2).toInt(),
                        product.name,
                        newPrice,
                        alert.targetPrice,
                        alert.isAbove,
                        parsed.currency
                    )
                    db.priceAlertDao().update(alert.copy(isActive = false))
                }
            }

            val autoAlerts = db.priceAlertDao().getAlertsForProduct(product.id).first()
            val hasAutoAlert = autoAlerts.any { it.targetPrice == 0.0 && !it.isAbove }
            if (!hasAutoAlert && oldPrice > 0) {
                val autoTarget = oldPrice * 0.95
                if (newPrice <= autoTarget) {
                    db.priceAlertDao().insert(
                        PriceAlert(productId = product.id, targetPrice = autoTarget, isAbove = false)
                    )
                    NotificationHelper.showPriceDropNotification(
                        applicationContext,
                        (product.id * 100 + 3).toInt(),
                        product.name,
                        oldPrice,
                        newPrice,
                        percentChange,
                        parsed.currency
                    )
                }
            }
        }
    }
}
