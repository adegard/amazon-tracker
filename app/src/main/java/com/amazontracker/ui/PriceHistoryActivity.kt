package com.amazontracker.ui

import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.view.Menu
import android.view.MenuItem
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.amazontracker.R
import com.amazontracker.data.AppDatabase
import com.amazontracker.data.PriceAlert
import com.amazontracker.data.PriceEntry
import com.amazontracker.data.TrackedProduct
import com.amazontracker.util.UIUtils
import com.google.android.material.bottomnavigation.BottomNavigationView
import com.google.android.material.button.MaterialButton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.abs

class PriceHistoryActivity : AppCompatActivity() {

    private lateinit var database: AppDatabase
    private lateinit var recyclerView: RecyclerView
    private lateinit var productNameText: TextView
    private lateinit var currentPriceText: TextView
    private lateinit var lowestPriceText: TextView
    private lateinit var highestPriceText: TextView
    private lateinit var priceRangeText: TextView
    private lateinit var addAlertButton: MaterialButton
    private lateinit var bottomNav: BottomNavigationView

    private var productId: Long = -1
    private var product: TrackedProduct? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_price_history)

        val toolbar = findViewById<com.google.android.material.appbar.MaterialToolbar>(R.id.toolbar)
        setSupportActionBar(toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)

        database = AppDatabase.getInstance(this)
        productId = intent.getLongExtra("product_id", -1)

        if (productId == -1L) { finish(); return }

        productNameText = findViewById(R.id.productNameText)
        currentPriceText = findViewById(R.id.currentPriceText)
        lowestPriceText = findViewById(R.id.lowestPriceText)
        highestPriceText = findViewById(R.id.highestPriceText)
        priceRangeText = findViewById(R.id.priceRangeText)
        addAlertButton = findViewById(R.id.addAlertButton)
        recyclerView = findViewById(R.id.historyRecyclerView)
        bottomNav = findViewById(R.id.bottomNav)

        recyclerView.layoutManager = LinearLayoutManager(this)

        addAlertButton.setOnClickListener { showAddAlertDialog() }

        setupBottomNav()
        loadProductData()
    }

    private fun setupBottomNav() {
        bottomNav.setOnItemSelectedListener { item ->
            when (item.itemId) {
                R.id.nav_home -> {
                    startActivity(Intent(this, MainActivity::class.java))
                    finish(); true
                }
                R.id.nav_track -> {
                    startActivity(Intent(this, MainActivity::class.java))
                    finish(); true
                }
                R.id.nav_tracked -> {
                    startActivity(Intent(this, TrackedProductsActivity::class.java))
                    finish(); true
                }
                R.id.nav_alerts -> {
                    startActivity(Intent(this, AlertSettingsActivity::class.java))
                    finish(); true
                }
                R.id.nav_more -> {
                    startActivity(Intent(this, MainActivity::class.java))
                    finish(); true
                }
                else -> false
            }
        }
    }

    private fun loadProductData() {
        lifecycleScope.launch {
            product = withContext(Dispatchers.IO) {
                database.trackedProductDao().getById(productId)
            }

            product?.let { prod ->
                productNameText.text = prod.name
                currentPriceText.text = "Current: ${UIUtils.formatPrice(prod.currentPrice)}"
                lowestPriceText.text = "Lowest: ${UIUtils.formatPrice(prod.lowestPrice)}"
                highestPriceText.text = "Highest: ${UIUtils.formatPrice(prod.highestPrice)}"

                val range = prod.highestPrice - prod.lowestPrice
                priceRangeText.text = "Range: ${UIUtils.formatPrice(range)}"

                supportActionBar?.title = prod.name
                loadHistory()
            }
        }
    }

    private fun loadHistory() {
        lifecycleScope.launch {
            val history = withContext(Dispatchers.IO) {
                database.priceHistoryDao().getHistory(productId).first()
            }
            recyclerView.adapter = PriceHistoryAdapter(history)
        }
    }

    private fun showAddAlertDialog() {
        val prod = product ?: return
        val input = android.widget.EditText(this).apply {
            hint = "Target price (e.g. ${UIUtils.formatPrice(prod.currentPrice * 0.9)})"
            inputType = android.text.InputType.TYPE_CLASS_NUMBER or android.text.InputType.TYPE_NUMBER_FLAG_DECIMAL
            setPadding(50, 30, 50, 30)
        }

        val options = arrayOf("Alert when price drops below", "Alert when price rises above")
        var isAbove = false

        AlertDialog.Builder(this)
            .setTitle("Set Price Alert")
            .setSingleChoiceItems(options, -1) { _, which -> isAbove = which == 1 }
            .setView(input)
            .setPositiveButton("Set Alert") { _, _ ->
                val targetPrice = input.text.toString().toDoubleOrNull()
                if (targetPrice != null && targetPrice > 0) {
                    lifecycleScope.launch {
                        withContext(Dispatchers.IO) {
                            database.priceAlertDao().insert(
                                PriceAlert(productId = productId, targetPrice = targetPrice, isAbove = isAbove)
                            )
                        }
                        Toast.makeText(
                            this@PriceHistoryActivity,
                            "Alert set: ${if (isAbove) "above" else "below"} ${UIUtils.formatPrice(targetPrice)}",
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.price_history_menu, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            android.R.id.home -> { finish(); true }
            R.id.action_add_alert -> { showAddAlertDialog(); true }
            R.id.action_share -> { shareProduct(); true }
            R.id.action_delete -> { deleteProduct(); true }
            else -> super.onOptionsItemSelected(item)
        }
    }

    private fun shareProduct() {
        val prod = product ?: return
        val shareIntent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_TEXT, "${prod.name}\n${UIUtils.formatPrice(prod.currentPrice)}\n${prod.url}")
        }
        startActivity(Intent.createChooser(shareIntent, "Share product"))
    }

    private fun deleteProduct() {
        val prod = product ?: return
        AlertDialog.Builder(this)
            .setTitle("Stop Tracking")
            .setMessage("Stop tracking \"${prod.name}\" and delete all history?")
            .setPositiveButton("Delete") { _, _ ->
                lifecycleScope.launch {
                    withContext(Dispatchers.IO) { database.trackedProductDao().deleteById(prod.id) }
                    finish()
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    inner class PriceHistoryAdapter(
        private val entries: List<PriceEntry>
    ) : RecyclerView.Adapter<PriceHistoryAdapter.ViewHolder>() {

        inner class ViewHolder(view: android.view.View) : RecyclerView.ViewHolder(view) {
            val priceText: TextView = view.findViewById(R.id.historyPriceText)
            val dateText: TextView = view.findViewById(R.id.historyDateText)
            val changeIndicator: TextView = view.findViewById(R.id.changeIndicator)
        }

        override fun onCreateViewHolder(parent: android.view.ViewGroup, viewType: Int): ViewHolder {
            val view = layoutInflater.inflate(R.layout.item_price_history, parent, false)
            return ViewHolder(view)
        }

        override fun onBindViewHolder(holder: ViewHolder, position: Int) {
            val entry = entries[position]
            holder.priceText.text = UIUtils.formatPrice(entry.price)
            holder.dateText.text = UIUtils.formatTimestamp(entry.timestamp)

            if (position > 0) {
                val prevPrice = entries[position - 1].price
                val diff = entry.price - prevPrice
                val pctChange = (diff / prevPrice) * 100

                when {
                    diff > 0 -> {
                        holder.changeIndicator.text = "+${UIUtils.formatPrice(diff)} (+%.1f%%)".format(pctChange)
                        holder.changeIndicator.setTextColor(Color.RED)
                    }
                    diff < 0 -> {
                        holder.changeIndicator.text = "-${UIUtils.formatPrice(abs(diff))} (%.1f%%)".format(pctChange)
                        holder.changeIndicator.setTextColor(Color.parseColor("#4CAF50"))
                    }
                    else -> {
                        holder.changeIndicator.text = "No change"
                        holder.changeIndicator.setTextColor(Color.GRAY)
                    }
                }
            } else {
                holder.changeIndicator.text = "First record"
                holder.changeIndicator.setTextColor(Color.GRAY)
            }
        }

        override fun getItemCount() = entries.size
    }
}
