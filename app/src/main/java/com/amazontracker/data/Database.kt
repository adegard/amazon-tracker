package com.amazontracker.data

import androidx.room.*
import java.util.Date

@Entity(tableName = "tracked_products")
data class TrackedProduct(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val asin: String,
    val name: String,
    val imageUrl: String = "",
    val currentPrice: Double = 0.0,
    val lowestPrice: Double = 0.0,
    val highestPrice: Double = 0.0,
    val targetPrice: Double = 0.0,
    val alertEnabled: Boolean = false,
    val dateAdded: Long = System.currentTimeMillis(),
    val lastChecked: Long = System.currentTimeMillis(),
    val url: String = ""
)

@Entity(
    tableName = "price_history",
    foreignKeys = [ForeignKey(
        entity = TrackedProduct::class,
        parentColumns = ["id"],
        childColumns = ["productId"],
        onDelete = ForeignKey.CASCADE
    )],
    indices = [Index("productId"), Index("timestamp")]
)
data class PriceEntry(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val productId: Long,
    val price: Double,
    val currency: String = "USD",
    val timestamp: Long = System.currentTimeMillis()
)

@Entity(tableName = "price_alerts")
data class PriceAlert(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val productId: Long,
    val targetPrice: Double,
    val isAbove: Boolean = false,
    val isActive: Boolean = true,
    val createdAt: Long = System.currentTimeMillis()
)

@Dao
interface TrackedProductDao {
    @Query("SELECT * FROM tracked_products ORDER BY lastChecked DESC")
    fun getAll(): kotlinx.coroutines.flow.Flow<List<TrackedProduct>>

    @Query("SELECT * FROM tracked_products WHERE id = :id")
    suspend fun getById(id: Long): TrackedProduct?

    @Query("SELECT * FROM tracked_products WHERE asin = :asin")
    suspend fun getByAsin(asin: String): TrackedProduct?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(product: TrackedProduct): Long

    @Update
    suspend fun update(product: TrackedProduct)

    @Delete
    suspend fun delete(product: TrackedProduct)

    @Query("DELETE FROM tracked_products WHERE id = :id")
    suspend fun deleteById(id: Long)
}

@Dao
interface PriceHistoryDao {
    @Query("SELECT * FROM price_history WHERE productId = :productId ORDER BY timestamp DESC")
    fun getHistory(productId: Long): kotlinx.coroutines.flow.Flow<List<PriceEntry>>

    @Query("SELECT * FROM price_history WHERE productId = :productId ORDER BY timestamp ASC")
    suspend fun getHistoryList(productId: Long): List<PriceEntry>

    @Insert
    suspend fun insert(entry: PriceEntry): Long

    @Query("DELETE FROM price_history WHERE productId = :productId")
    suspend fun deleteForProduct(productId: Long)
}

@Dao
interface PriceAlertDao {
    @Query("SELECT * FROM price_alerts WHERE isActive = 1")
    fun getActiveAlerts(): kotlinx.coroutines.flow.Flow<List<PriceAlert>>

    @Query("SELECT * FROM price_alerts WHERE productId = :productId")
    fun getAlertsForProduct(productId: Long): kotlinx.coroutines.flow.Flow<List<PriceAlert>>

    @Insert
    suspend fun insert(alert: PriceAlert): Long

    @Update
    suspend fun update(alert: PriceAlert)

    @Delete
    suspend fun delete(alert: PriceAlert)
}
