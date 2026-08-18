package com.amazontracker.ui

import android.annotation.SuppressLint
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.os.Build
import android.os.Bundle
import android.os.Message
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.webkit.*
import android.widget.EditText
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.amazontracker.R
import com.amazontracker.data.AppDatabase
import com.amazontracker.data.PriceAlert
import com.amazontracker.data.PriceEntry
import com.amazontracker.data.TrackedProduct
import com.amazontracker.util.PriceParser
import com.amazontracker.util.TrackerBlocker
import com.amazontracker.util.UIUtils
import com.google.android.material.bottomnavigation.BottomNavigationView
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MainActivity : AppCompatActivity() {

    private lateinit var webView: WebView
    private lateinit var progressBar: ProgressBar
    private lateinit var bottomNav: BottomNavigationView
    private lateinit var dealInfoBar: TextView
    private lateinit var database: AppDatabase
    private lateinit var prefs: SharedPreferences

    private var currentTrackedProduct: TrackedProduct? = null

    private val notifPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted -> }

    companion object {
        const val PREFS_NAME = "amazon_tracker_prefs"
        const val KEY_REGION = "region"
        val REGIONS = linkedMapOf(
            "it" to Region("it", "Amazon.it", "https://www.amazon.it", "amazon.it"),
            "com" to Region("com", "Amazon.com (US)", "https://www.amazon.com", "amazon.com"),
            "co.uk" to Region("co.uk", "Amazon.co.uk (UK)", "https://www.amazon.co.uk", "amazon.co.uk"),
            "de" to Region("de", "Amazon.de (DE)", "https://www.amazon.de", "amazon.de"),
            "fr" to Region("fr", "Amazon.fr (FR)", "https://www.amazon.fr", "amazon.fr"),
            "es" to Region("es", "Amazon.es (ES)", "https://www.amazon.es", "amazon.es"),
            "co.jp" to Region("co.jp", "Amazon.co.jp (JP)", "https://www.amazon.co.jp", "amazon.co.jp"),
            "com.au" to Region("com.au", "Amazon.com.au (AU)", "https://www.amazon.com.au", "amazon.com.au"),
            "in" to Region("in", "Amazon.in (IN)", "https://www.amazon.in", "amazon.in"),
            "com.br" to Region("com.br", "Amazon.com.br (BR)", "https://www.amazon.com.br", "amazon.com.br")
        )
    }

    data class Region(val code: String, val label: String, val url: String, val domain: String)

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        database = AppDatabase.getInstance(this)
        prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE)

        webView = findViewById(R.id.webView)
        progressBar = findViewById(R.id.progressBar)
        bottomNav = findViewById(R.id.bottomNav)
        dealInfoBar = findViewById(R.id.dealInfoBar)

        setSupportActionBar(findViewById(R.id.toolbar))

        setupWebView()
        setupBottomNav()
        requestNotificationPermission()

        when {
            intent?.hasExtra("load_url") == true -> {
                val url = intent.getStringExtra("load_url") ?: ""
                if (url.isNotEmpty()) webView.loadUrl(url)
            }
            intent?.action == Intent.ACTION_VIEW -> {
                intent.data?.let { uri -> webView.loadUrl(uri.toString()) }
            }
            else -> {
                val savedRegion = prefs.getString(KEY_REGION, null)
                val region = if (savedRegion != null && REGIONS.containsKey(savedRegion)) {
                    REGIONS[savedRegion]!!
                } else {
                    detectRegion()
                }
                webView.loadUrl(region.url)
            }
        }
    }

    private fun detectRegion(): Region {
        val locale = java.util.Locale.getDefault().country.uppercase()
        val regionCode = when (locale) {
            "IT" -> "it"
            "GB", "UK" -> "co.uk"
            "DE" -> "de"
            "FR" -> "fr"
            "ES" -> "es"
            "JP" -> "co.jp"
            "AU" -> "com.au"
            "IN" -> "in"
            "BR" -> "com.br"
            else -> "com"
        }
        prefs.edit().putString(KEY_REGION, regionCode).apply()
        return REGIONS[regionCode]!!
    }

    private fun requestNotificationPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, android.Manifest.permission.POST_NOTIFICATIONS)
                != PackageManager.PERMISSION_GRANTED
            ) {
                notifPermissionLauncher.launch(android.Manifest.permission.POST_NOTIFICATIONS)
            }
        }
    }

    @SuppressLint("SetJavaScriptEnabled")
    private fun setupWebView() {
        webView.settings.apply {
            javaScriptEnabled = true
            domStorageEnabled = true
            databaseEnabled = true
            setSupportZoom(true)
            builtInZoomControls = true
            displayZoomControls = false
            loadWithOverviewMode = true
            useWideViewPort = true
            allowContentAccess = true
            allowFileAccess = true
            mixedContentMode = WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
            cacheMode = WebSettings.LOAD_DEFAULT
            setSupportMultipleWindows(false)
            setGeolocationEnabled(false)
            userAgentString = "Mozilla/5.0 (Linux; Android 14; Pixel 8 Pro) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/125.0.0.0 Mobile Safari/537.36"
        }

        CookieManager.getInstance().apply {
            setAcceptCookie(true)
            setAcceptThirdPartyCookies(webView, true)
        }

        webView.webViewClient = object : WebViewClient() {
            override fun shouldOverrideUrlLoading(
                view: WebView?,
                request: WebResourceRequest?
            ): Boolean {
                return false
            }

            override fun onPageStarted(view: WebView?, url: String?, favicon: Bitmap?) {
                super.onPageStarted(view, url, favicon)
                progressBar.visibility = View.VISIBLE
                url?.let { checkIfProductPage(it) }
            }

            override fun onPageFinished(view: WebView?, url: String?) {
                super.onPageFinished(view, url)
                progressBar.visibility = View.GONE
                view?.evaluateJavascript(TrackerBlocker.getAntiDetectionJavaScript(), null)
                view?.evaluateJavascript(TrackerBlocker.getAppBannerRemovalJS(), null)
                url?.let { checkForTriggeredAlerts(it) }
                autoSetRegionFromUrl(url)
                if (url != null && PriceParser.isProductPage(url)) {
                    extractProductFromWebView { name, price, currency, image, listPrice, dealInfo, lowestPrice30d ->
                        runOnUiThread { updateDealInfoBar(name, price, currency, listPrice, dealInfo, lowestPrice30d) }
                    }
                } else {
                    runOnUiThread { dealInfoBar.visibility = View.GONE }
                }
            }
        }

        webView.webChromeClient = object : WebChromeClient() {
            override fun onProgressChanged(view: WebView?, newProgress: Int) {
                progressBar.progress = newProgress
                if (newProgress == 100) progressBar.visibility = View.GONE
            }
        }
    }

    private fun autoSetRegionFromUrl(url: String?) {
        if (url == null) return
        for ((code, region) in REGIONS) {
            if (url.contains(region.domain)) {
                val current = prefs.getString(KEY_REGION, null)
                if (current != code) {
                    prefs.edit().putString(KEY_REGION, code).apply()
                }
                break
            }
        }
    }

    private fun checkIfProductPage(url: String) {
        if (PriceParser.isProductPage(url)) {
            lifecycleScope.launch {
                val asin = PriceParser.extractAsin(url)
                if (asin != null) {
                    val existing = withContext(Dispatchers.IO) {
                        database.trackedProductDao().getByAsin(asin)
                    }
                    currentTrackedProduct = existing
                }
            }
        } else {
            currentTrackedProduct = null
        }
    }

    private fun checkForTriggeredAlerts(url: String) {
        lifecycleScope.launch {
            val asin = PriceParser.extractAsin(url) ?: return@launch
            val product = withContext(Dispatchers.IO) {
                database.trackedProductDao().getByAsin(asin)
            } ?: return@launch

            val history = withContext(Dispatchers.IO) {
                database.priceHistoryDao().getHistoryList(product.id)
            }
            if (history.isNotEmpty()) {
                val latest = history.last()
                val alerts = withContext(Dispatchers.IO) {
                    database.priceAlertDao().getAlertsForProduct(product.id).first()
                }
                for (alert in alerts) {
                    if (!alert.isActive) continue
                    val triggered = when {
                        alert.isAbove -> latest.price >= alert.targetPrice
                        else -> latest.price <= alert.targetPrice
                    }
                    if (triggered) {
                        showPriceAlertDialog(product.name, latest.price, alert.targetPrice, alert.isAbove)
                        break
                    }
                }
            }
        }
    }

    private fun updateDealInfoBar(name: String?, price: Double, currency: String, listPrice: Double, dealInfo: String, lowestPrice30d: Double) {
        if (price <= 0 && listPrice <= 0) {
            dealInfoBar.visibility = View.GONE
            return
        }
        val parts = mutableListOf<String>()
        if (price > 0) parts.add("Now: ${UIUtils.formatPrice(price, currency)}")
        if (listPrice > 0 && listPrice != price) {
            if (listPrice > price) {
                val pctOff = ((listPrice - price) / listPrice * 100).toInt()
                parts.add("List: ${UIUtils.formatPrice(listPrice, currency)} (-$pctOff%)")
            } else {
                parts.add("List: ${UIUtils.formatPrice(listPrice, currency)} (↑)")
            }
        }
        if (lowestPrice30d > 0 && lowestPrice30d != price && lowestPrice30d != listPrice) {
            if (price <= lowestPrice30d) {
                parts.add("Lowest in 30d: ${UIUtils.formatPrice(lowestPrice30d, currency)} ✓")
            } else {
                parts.add("Low 30d: ${UIUtils.formatPrice(lowestPrice30d, currency)}")
            }
        }
        if (parts.isEmpty()) {
            dealInfoBar.visibility = View.GONE
            return
        }
        dealInfoBar.text = parts.joinToString("  ·  ")
        dealInfoBar.visibility = View.VISIBLE
        if (listPrice > 0 && price > 0 && listPrice > price) {
            dealInfoBar.setTextColor(0xFF006600.toInt())
            dealInfoBar.setBackgroundColor(0x1A006600.toInt())
        } else if (lowestPrice30d > 0 && price <= lowestPrice30d) {
            dealInfoBar.setTextColor(0xFF006600.toInt())
            dealInfoBar.setBackgroundColor(0x1A006600.toInt())
        } else {
            dealInfoBar.setTextColor(0xFF663300.toInt())
            dealInfoBar.setBackgroundColor(0x1AFFBB66.toInt())
        }
    }

    private fun showPriceAlertDialog(
        productName: String, currentPrice: Double, targetPrice: Double, isAbove: Boolean
    ) {
        val message = if (isAbove) {
            "\"$productName\" has risen to ${UIUtils.formatPrice(currentPrice)} (target: ${UIUtils.formatPrice(targetPrice)})"
        } else {
            "\"$productName\" has dropped to ${UIUtils.formatPrice(currentPrice)} (target: ${UIUtils.formatPrice(targetPrice)})"
        }
        AlertDialog.Builder(this, R.style.AlertDialogCustom)
            .setTitle("Price Alert!")
            .setMessage(message)
            .setPositiveButton("OK", null)
            .setNegativeButton("Track Price") { _, _ ->
                startActivity(Intent(this, PriceHistoryActivity::class.java).apply {
                    putExtra("product_id", currentTrackedProduct?.id ?: -1)
                })
            }
            .show()
    }

    private fun setupBottomNav() {
        bottomNav.selectedItemId = R.id.nav_home
        bottomNav.setOnItemSelectedListener { item ->
            when (item.itemId) {
                R.id.nav_home -> true
                R.id.nav_track -> { trackCurrentProduct(); true }
                R.id.nav_tracked -> { startActivity(Intent(this, TrackedProductsActivity::class.java)); true }
                R.id.nav_alerts -> { startActivity(Intent(this, AlertSettingsActivity::class.java)); true }
                R.id.nav_more -> { showMoreOptions(); true }
                else -> false
            }
        }
    }

    private fun extractProductFromWebView(callback: (String?, Double, String, String, Double, String, Double) -> Unit) {
        val js = """
            (function() {
                var name = '';
                var price = 0;
                var currency = 'EUR';
                var image = '';
                var listPrice = 0;
                var dealInfo = '';

                var nameEl = document.querySelector('#productTitle');
                if (!nameEl) nameEl = document.querySelector('#title span');
                if (!nameEl) nameEl = document.querySelector('h1.a-size-large');
                if (!nameEl) nameEl = document.querySelector('span.product-title-word-break');
                if (!nameEl) nameEl = document.querySelector('[data-feature-name="title"] span');
                if (nameEl) name = nameEl.textContent.trim();
                if (!name) {
                    var metas = document.querySelectorAll('meta[name="title"], meta[property="og:title"]');
                    for (var m = 0; m < metas.length; m++) {
                        var v = metas[m].getAttribute('content');
                        if (v && v.trim()) { name = v.trim().split('|')[0].trim(); break; }
                    }
                }
                if (!name) {
                    var h1s = document.querySelectorAll('h1');
                    for (var h = 0; h < h1s.length; h++) {
                        var ht = h1s[h].textContent.trim();
                        if (ht.length > 5 && ht.length < 300) { name = ht; break; }
                    }
                }

                var priceText = '';
                var selectors = [
                    '#corePrice_feature_div .a-price .a-offscreen',
                    '.priceToPay .a-offscreen',
                    '#apex_offerDisplay_desktop .a-offscreen',
                    '.a-price .a-offscreen',
                    '#priceblock_ourprice',
                    '#priceblock_dealprice',
                    '#price_inside_buybox',
                    '#newBuyBoxPrice',
                    '#tp_price_block_total_price_ww .a-offscreen',
                    '.apexPriceToPay .a-offscreen',
                    '#corePrice_desktop .a-price .a-offscreen',
                    '#price .a-color-price',
                    '#buybox .a-price .a-offscreen'
                ];
                for (var i = 0; i < selectors.length; i++) {
                    var el = document.querySelector(selectors[i]);
                    if (el && el.textContent.trim()) {
                        priceText = el.textContent.trim();
                        break;
                    }
                }
                if (!priceText) {
                    var allPrices = document.querySelectorAll('[data-a-color="price"] .a-offscreen, .a-price.aok-hidden ~ .a-offscreen');
                    for (var j = 0; j < allPrices.length; j++) {
                        if (allPrices[j].textContent.trim()) {
                            priceText = allPrices[j].textContent.trim();
                            break;
                        }
                    }
                }
                if (!priceText) {
                    var spans = document.querySelectorAll('span.a-offscreen');
                    for (var k = 0; k < spans.length; k++) {
                        var t = spans[k].textContent.trim();
                        if (t && /[\$\u20ac\u00a3\u00a5]/.test(t) || /\d+[,\.]\d{2}/.test(t)) {
                            priceText = t;
                            break;
                        }
                    }
                }
                if (priceText) {
                    var cleaned = priceText.replace(/[^\d,\.]/g, '').trim();
                    if (cleaned.indexOf(',') > 0 && cleaned.indexOf('.') > 0) {
                        cleaned = cleaned.replace(/\./g, '');
                    }
                    cleaned = cleaned.replace(',', '.');
                    price = parseFloat(cleaned) || 0;

                    if (priceText.includes('\u20ac')) currency = 'EUR';
                    else if (priceText.includes('\u00a3')) currency = 'GBP';
                    else if (priceText.includes('\u00a5') || priceText.includes('JPY')) currency = 'JPY';
                    else if (priceText.includes('R$')) currency = 'BRL';
                    else if (priceText.includes('\u20b9')) currency = 'INR';
                    else if (priceText.includes('A$')) currency = 'AUD';
                    else if (priceText.includes('CA$')) currency = 'CAD';
                    else currency = 'USD';
                }

                var listPriceEl = document.querySelector('.a-price.a-text-price .a-offscreen')
                               || document.querySelector('#listPrice')
                               || document.querySelector('#priceblock_listprice')
                               || document.querySelector('.basisPrice .a-offscreen')
                               || document.querySelector('.a-text-strike')
                               || document.querySelector('#priceblock_ourprice + .a-text-strike');
                if (!listPriceEl) {
                    var cpDiv = document.querySelector('#corePrice_feature_div') || document.querySelector('#corePrice_desktop');
                    if (cpDiv) {
                        var tp = cpDiv.querySelectorAll('.a-text-price .a-offscreen');
                        if (tp.length > 0) listPriceEl = tp[0];
                    }
                }
                if (!listPriceEl) {
                    var allTextPrices = document.querySelectorAll('.a-text-price .a-offscreen');
                    for (var lp = 0; lp < allTextPrices.length; lp++) {
                        var lpt = allTextPrices[lp].textContent.trim();
                        var lpc = lpt.replace(/[^\d,\.]/g, '').replace(',', '.');
                        if (parseFloat(lpc) > 0) { listPriceEl = allTextPrices[lp]; break; }
                    }
                }
                if (listPriceEl) {
                    var lpText = listPriceEl.textContent.trim();
                    var lpCleaned = lpText.replace(/[^\d,\.]/g, '').trim();
                    if (lpCleaned.indexOf(',') > 0 && lpCleaned.indexOf('.') > 0) {
                        lpCleaned = lpCleaned.replace(/\./g, '');
                    }
                    lpCleaned = lpCleaned.replace(',', '.');
                    listPrice = parseFloat(lpCleaned) || 0;
                }

                var lowestPrice30d = 0;
                var allEls = document.querySelectorAll('*');
                for (var e = 0; e < allEls.length && e < 5000; e++) {
                    var txt = allEls[e].textContent || '';
                    if (txt.length < 200 && txt.length > 3) {
                        if (/prezzo\s+(pi[ùu]\s+basso|mediano)/i.test(txt) ||
                            /lowest\s+price/i.test(txt) || /median/i.test(txt)) {
                            var nums = txt.match(/\d+[,\.]\d{2}/g);
                            if (nums && nums.length > 0) {
                                var val = nums[nums.length - 1].replace(',', '.');
                                var parsed = parseFloat(val);
                                if (parsed > 0) {
                                    if (/pi[ùu]\s+basso|lowest/i.test(txt)) lowestPrice30d = parsed;
                                    else if (listPrice <= 0) listPrice = parsed;
                                }
                            }
                        }
                    }
                }
                if (lowestPrice30d <= 0) {
                    var savingsPct = document.querySelector('.savingsPercentage');
                    if (savingsPct && listPrice > 0 && price > 0) {
                        lowestPrice30d = price;
                    }
                }

                var savingsEl = document.querySelector('.savingsPercentage')
                             || document.querySelector('#dealprice_savings')
                             || document.querySelector('.a-color-price .a-size-large');
                if (savingsEl) dealInfo = savingsEl.textContent.trim();

                if (listPrice > 0 && price > 0 && listPrice > price) {
                    var pctOff = Math.round(((listPrice - price) / listPrice) * 100);
                    dealInfo = pctOff + '% off';
                } else if (!dealInfo && price > 0) {
                    dealInfo = 'Current price';
                }

                var imgEl = document.querySelector('#landingImage')
                         || document.querySelector('#imgBlkFront')
                         || document.querySelector('#main-image')
                         || document.querySelector('#mainImage')
                         || document.querySelector('img[data-old-hires]');
                if (imgEl) {
                    image = imgEl.getAttribute('data-old-hires')
                         || imgEl.getAttribute('data-dynamic-image')
                         || imgEl.getAttribute('src')
                         || '';
                }

                return JSON.stringify({
                    name: name, price: price, currency: currency, image: image,
                    listPrice: listPrice, dealInfo: dealInfo, lowestPrice30d: lowestPrice30d
                });
            })()
        """.trimIndent()
        webView.evaluateJavascript(js) { result ->
            try {
                val unescaped = result
                    ?.removeSurrounding("\"")
                    ?.replace("\\\"", "\"")
                    ?.replace("\\/", "/")
                    ?.replace("\\\\", "\\")
                val json = org.json.JSONObject(unescaped ?: "{}")
                callback(
                    json.optString("name", "").ifEmpty { null },
                    json.optDouble("price", 0.0),
                    json.optString("currency", "EUR"),
                    json.optString("image", ""),
                    json.optDouble("listPrice", 0.0),
                    json.optString("dealInfo", ""),
                    json.optDouble("lowestPrice30d", 0.0)
                )
            } catch (e: Exception) {
                callback(null, 0.0, "EUR", "", 0.0, "", 0.0)
            }
        }
    }

    private fun trackCurrentProduct() {
        val url = webView.url ?: return
        if (!PriceParser.isProductPage(url)) {
            Toast.makeText(this, "Not a product page", Toast.LENGTH_SHORT).show()
            return
        }

        lifecycleScope.launch {
            val asin = PriceParser.extractAsin(url) ?: return@launch
            val existing = withContext(Dispatchers.IO) {
                database.trackedProductDao().getByAsin(asin)
            }

            if (existing != null) {
                startActivity(Intent(this@MainActivity, PriceHistoryActivity::class.java).apply {
                    putExtra("product_id", existing.id)
                })
                return@launch
            }

            withContext(Dispatchers.Main) {
                extractProductFromWebView { name, price, currency, image, listPrice, dealInfo, lowestPrice30d ->
                    lifecycleScope.launch {
                        val product = TrackedProduct(
                            asin = asin,
                            name = name ?: "Unknown Product",
                            currentPrice = price,
                            lowestPrice = price,
                            highestPrice = price,
                            imageUrl = image,
                            url = url
                        )

                        val id = withContext(Dispatchers.IO) {
                            val id = database.trackedProductDao().insert(product)
                            if (price > 0) {
                                database.priceHistoryDao().insert(
                                    PriceEntry(productId = id, price = price, currency = currency)
                                )
                                val autoTarget = price * 0.95
                                database.priceAlertDao().insert(
                                    PriceAlert(productId = id, targetPrice = autoTarget, isAbove = false)
                                )
                            }
                            id
                        }

                        currentTrackedProduct = product.copy(id = id)
                        val dealLine = if (listPrice > 0 && price > 0 && listPrice > price) {
                            "\nList: ${UIUtils.formatPrice(listPrice, currency)} → Now: ${UIUtils.formatPrice(price, currency)} ($dealInfo)"
                        } else ""
                        val msg = if (price > 0) {
                            "Tracking: ${product.name}\nAlert if drops below ${UIUtils.formatPrice(price * 0.95, currency)}$dealLine"
                        } else {
                            "Tracking: ${product.name} (price will update on next check)"
                        }
                        Toast.makeText(this@MainActivity, msg, Toast.LENGTH_LONG).show()
                    }
                }
            }
        }
    }

    private fun showMoreOptions() {
        val options = arrayOf("Switch Region", "Add URL")
        AlertDialog.Builder(this)
            .setTitle("Options")
            .setItems(options) { _, which ->
                when (which) {
                    0 -> showRegionPicker()
                    1 -> showAddUrlDialog()
                }
            }
            .show()
    }

    fun showRegionPicker() {
        val regionLabels = REGIONS.values.map { it.label }.toTypedArray()
        val currentRegion = prefs.getString(KEY_REGION, "com") ?: "com"
        val currentIndex = REGIONS.keys.indexOf(currentRegion)

        AlertDialog.Builder(this)
            .setTitle("Select Amazon Region")
            .setSingleChoiceItems(regionLabels, currentIndex) { dialog, which ->
                val selectedCode = REGIONS.keys.elementAt(which)
                val region = REGIONS[selectedCode]!!
                prefs.edit().putString(KEY_REGION, selectedCode).apply()
                webView.loadUrl(region.url)
                dialog.dismiss()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun showAddUrlDialog() {
        val input = EditText(this).apply {
            hint = "https://www.amazon.it/dp/..."
            setPadding(50, 30, 50, 30)
        }
        AlertDialog.Builder(this)
            .setTitle("Add Product URL")
            .setView(input)
            .setPositiveButton("Go") { _, _ ->
                val url = input.text.toString().trim()
                if (url.isNotEmpty()) webView.loadUrl(url)
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.main_menu, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            R.id.action_track -> { trackCurrentProduct(); true }
            R.id.action_history -> {
                currentTrackedProduct?.let {
                    startActivity(Intent(this, PriceHistoryActivity::class.java).apply {
                        putExtra("product_id", it.id)
                    })
                } ?: Toast.makeText(this, "No product tracked", Toast.LENGTH_SHORT).show()
                true
            }
            R.id.action_region -> { showRegionPicker(); true }
            else -> super.onOptionsItemSelected(item)
        }
    }

    @Suppress("DEPRECATION")
    override fun onBackPressed() {
        if (webView.canGoBack()) webView.goBack()
        else super.onBackPressed()
    }

    override fun onDestroy() {
        webView.destroy()
        super.onDestroy()
    }
}
