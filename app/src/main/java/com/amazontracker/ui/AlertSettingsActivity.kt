package com.amazontracker.ui

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.Switch
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
import com.amazontracker.data.TrackedProduct
import com.amazontracker.util.UIUtils
import com.google.android.material.bottomnavigation.BottomNavigationView
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class AlertSettingsActivity : AppCompatActivity() {

    private lateinit var database: AppDatabase
    private lateinit var recyclerView: RecyclerView
    private lateinit var emptyText: TextView
    private lateinit var bottomNav: BottomNavigationView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_alert_settings)

        val toolbar = findViewById<com.google.android.material.appbar.MaterialToolbar>(R.id.toolbar)
        setSupportActionBar(toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.title = "Price Alerts"

        database = AppDatabase.getInstance(this)
        recyclerView = findViewById(R.id.alertsRecyclerView)
        emptyText = findViewById(R.id.emptyText)
        bottomNav = findViewById(R.id.bottomNav)

        recyclerView.layoutManager = LinearLayoutManager(this)

        setupBottomNav()
        loadAlerts()
    }

    override fun onResume() {
        super.onResume()
        bottomNav.selectedItemId = R.id.nav_alerts
    }

    private fun setupBottomNav() {
        bottomNav.setOnItemSelectedListener { item ->
            when (item.itemId) {
                R.id.nav_home -> {
                    startActivity(Intent(this, MainActivity::class.java))
                    finish()
                    true
                }
                R.id.nav_track -> {
                    startActivity(Intent(this, MainActivity::class.java).apply {
                        putExtra("action", "track")
                    })
                    finish()
                    true
                }
                R.id.nav_tracked -> {
                    startActivity(Intent(this, TrackedProductsActivity::class.java))
                    finish()
                    true
                }
                R.id.nav_alerts -> true
                R.id.nav_more -> {
                    startActivity(Intent(this, MainActivity::class.java).apply {
                        putExtra("action", "more")
                    })
                    finish()
                    true
                }
                else -> false
            }
        }
    }

    private fun loadAlerts() {
        lifecycleScope.launch {
            val alerts = withContext(Dispatchers.IO) {
                database.priceAlertDao().getActiveAlerts().first()
            }

            val alertsWithProducts = alerts.mapNotNull { alert ->
                val product = withContext(Dispatchers.IO) {
                    database.trackedProductDao().getById(alert.productId)
                }
                if (product != null) Pair(alert, product) else null
            }

            if (alertsWithProducts.isEmpty()) {
                emptyText.visibility = View.VISIBLE
                recyclerView.visibility = View.GONE
            } else {
                emptyText.visibility = View.GONE
                recyclerView.visibility = View.VISIBLE
                recyclerView.adapter = AlertsAdapter(alertsWithProducts)
            }
        }
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.alerts_menu, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            android.R.id.home -> { finish(); true }
            R.id.action_add_alert -> {
                Toast.makeText(this, "Go to a product page and tap Track to set alerts", Toast.LENGTH_LONG).show()
                true
            }
            R.id.action_delete_all -> {
                deleteAllAlerts()
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }

    private fun deleteAllAlerts() {
        lifecycleScope.launch {
            val alerts = withContext(Dispatchers.IO) {
                database.priceAlertDao().getActiveAlerts().first()
            }
            if (alerts.isEmpty()) {
                Toast.makeText(this@AlertSettingsActivity, "No alerts to delete", Toast.LENGTH_SHORT).show()
                return@launch
            }
            AlertDialog.Builder(this@AlertSettingsActivity)
                .setTitle("Delete All Alerts")
                .setMessage("Remove all ${alerts.size} price alerts?")
                .setPositiveButton("Delete All") { _, _ ->
                    lifecycleScope.launch {
                        withContext(Dispatchers.IO) {
                            for (alert in alerts) database.priceAlertDao().delete(alert)
                        }
                        Toast.makeText(this@AlertSettingsActivity, "All alerts deleted", Toast.LENGTH_SHORT).show()
                        loadAlerts()
                    }
                }
                .setNegativeButton("Cancel", null)
                .show()
        }
    }

    inner class AlertsAdapter(
        private val alerts: List<Pair<PriceAlert, TrackedProduct>>
    ) : RecyclerView.Adapter<AlertsAdapter.ViewHolder>() {

        inner class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
            val productNameText: TextView = view.findViewById(R.id.alertProductName)
            val alertDetailText: TextView = view.findViewById(R.id.alertDetailText)
            val currentPriceText: TextView = view.findViewById(R.id.alertCurrentPrice)
            val activeSwitch: Switch = view.findViewById(R.id.alertActiveSwitch)
            val deleteButton: ImageButton = view.findViewById(R.id.deleteAlertButton)
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
            val view = LayoutInflater.from(parent.context)
                .inflate(R.layout.item_alert, parent, false)
            return ViewHolder(view)
        }

        override fun onBindViewHolder(holder: ViewHolder, position: Int) {
            val (alert, product) = alerts[position]

            holder.productNameText.text = product.name
            val direction = if (alert.isAbove) "above" else "below"
            holder.alertDetailText.text = "Alert when price goes $direction ${UIUtils.formatPrice(alert.targetPrice)}"
            holder.currentPriceText.text = "Current: ${UIUtils.formatPrice(product.currentPrice)}"
            holder.activeSwitch.isChecked = alert.isActive

            holder.activeSwitch.setOnCheckedChangeListener { _, isChecked ->
                lifecycleScope.launch {
                    withContext(Dispatchers.IO) {
                        database.priceAlertDao().update(alert.copy(isActive = isChecked))
                    }
                }
            }

            holder.deleteButton.setOnClickListener {
                AlertDialog.Builder(this@AlertSettingsActivity)
                    .setTitle("Delete Alert")
                    .setMessage("Remove this price alert?")
                    .setPositiveButton("Delete") { _, _ ->
                        lifecycleScope.launch {
                            withContext(Dispatchers.IO) {
                                database.priceAlertDao().delete(alert)
                            }
                            Toast.makeText(this@AlertSettingsActivity, "Alert deleted", Toast.LENGTH_SHORT).show()
                            loadAlerts()
                        }
                    }
                    .setNegativeButton("Cancel", null)
                    .show()
            }
        }

        override fun getItemCount() = alerts.size
    }
}
