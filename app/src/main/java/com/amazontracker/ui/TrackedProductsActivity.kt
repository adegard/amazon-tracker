package com.amazontracker.ui

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.amazontracker.R
import com.amazontracker.data.AppDatabase
import com.amazontracker.data.TrackedProduct
import com.amazontracker.util.UIUtils
import com.google.android.material.bottomnavigation.BottomNavigationView
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class TrackedProductsActivity : AppCompatActivity() {

    private lateinit var database: AppDatabase
    private lateinit var recyclerView: RecyclerView
    private lateinit var emptyText: TextView
    private lateinit var bottomNav: BottomNavigationView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_tracked_products)

        val toolbar = findViewById<com.google.android.material.appbar.MaterialToolbar>(R.id.toolbar)
        setSupportActionBar(toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.title = "Tracked Products"

        database = AppDatabase.getInstance(this)
        recyclerView = findViewById(R.id.trackedRecyclerView)
        emptyText = findViewById(R.id.emptyText)
        bottomNav = findViewById(R.id.bottomNav)

        recyclerView.layoutManager = LinearLayoutManager(this)

        setupBottomNav()
        loadProducts()
    }

    override fun onResume() {
        super.onResume()
        bottomNav.selectedItemId = R.id.nav_tracked
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
                R.id.nav_tracked -> true
                R.id.nav_alerts -> {
                    startActivity(Intent(this, AlertSettingsActivity::class.java))
                    finish()
                    true
                }
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

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.alerts_menu, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            android.R.id.home -> { finish(); true }
            R.id.action_add_alert -> {
                startActivity(Intent(this, MainActivity::class.java))
                finish()
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }

    private fun loadProducts() {
        lifecycleScope.launch {
            val products = withContext(Dispatchers.IO) {
                database.trackedProductDao().getAll().first()
            }

            if (products.isEmpty()) {
                emptyText.visibility = View.VISIBLE
                recyclerView.visibility = View.GONE
            } else {
                emptyText.visibility = View.GONE
                recyclerView.visibility = View.VISIBLE
                recyclerView.adapter = TrackedProductsAdapter(products)
            }
        }
    }

    inner class TrackedProductsAdapter(
        private val products: List<TrackedProduct>
    ) : RecyclerView.Adapter<TrackedProductsAdapter.ViewHolder>() {

        inner class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
            val nameText: TextView = view.findViewById(R.id.productNameText)
            val priceText: TextView = view.findViewById(R.id.productPriceText)
            val rangeText: TextView = view.findViewById(R.id.priceRangeText)
            val lastCheckedText: TextView = view.findViewById(R.id.lastCheckedText)
            val visitButton: ImageButton = view.findViewById(R.id.visitButton)
            val deleteButton: ImageButton = view.findViewById(R.id.deleteButton)
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
            val view = LayoutInflater.from(parent.context)
                .inflate(R.layout.item_tracked_product, parent, false)
            return ViewHolder(view)
        }

        override fun onBindViewHolder(holder: ViewHolder, position: Int) {
            val product = products[position]

            holder.nameText.text = product.name
            holder.priceText.text = UIUtils.formatPrice(product.currentPrice)
            holder.lastCheckedText.text = "Last checked: ${UIUtils.formatDate(product.lastChecked)}"

            val range = product.highestPrice - product.lowestPrice
            holder.rangeText.text = if (range > 0) {
                "Range: ${UIUtils.formatPrice(product.lowestPrice)} - ${UIUtils.formatPrice(product.highestPrice)}"
            } else {
                "First check"
            }

            holder.itemView.setOnClickListener {
                startActivity(Intent(this@TrackedProductsActivity, PriceHistoryActivity::class.java).apply {
                    putExtra("product_id", product.id)
                })
            }

            holder.visitButton.setOnClickListener {
                if (product.url.isNotBlank()) {
                    startActivity(Intent(this@TrackedProductsActivity, MainActivity::class.java).apply {
                        putExtra("load_url", product.url)
                        flags = Intent.FLAG_ACTIVITY_CLEAR_TOP
                    })
                } else {
                    Toast.makeText(this@TrackedProductsActivity, "No URL saved", Toast.LENGTH_SHORT).show()
                }
            }

            holder.deleteButton.setOnClickListener {
                AlertDialog.Builder(this@TrackedProductsActivity)
                    .setTitle("Remove Product")
                    .setMessage("Stop tracking \"${product.name}\"?")
                    .setPositiveButton("Remove") { _, _ ->
                        lifecycleScope.launch {
                            withContext(Dispatchers.IO) {
                                database.trackedProductDao().deleteById(product.id)
                            }
                            Toast.makeText(this@TrackedProductsActivity, "Removed: ${product.name}", Toast.LENGTH_SHORT).show()
                            loadProducts()
                        }
                    }
                    .setNegativeButton("Cancel", null)
                    .show()
            }
        }

        override fun getItemCount() = products.size
    }
}
