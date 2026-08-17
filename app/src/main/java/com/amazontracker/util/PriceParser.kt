package com.amazontracker.util

import org.jsoup.Jsoup
import org.jsoup.nodes.Document

object PriceParser {

    data class ParsedPrice(
        val price: Double,
        val currency: String,
        val productName: String,
        val imageUrl: String,
        val asin: String
    )

    fun extractAsin(url: String): String? {
        val patterns = listOf(
            Regex("/dp/([A-Z0-9]{10})"),
            Regex("/gp/product/([A-Z0-9]{10})"),
            Regex("/ASIN/([A-Z0-9]{10})"),
            Regex("ASIN=([A-Z0-9]{10})"),
            Regex("/product/([A-Z0-9]{10})")
        )
        for (pattern in patterns) {
            val match = pattern.find(url)
            if (match != null) return match.groupValues[1]
        }
        return null
    }

    fun isProductPage(url: String): Boolean {
        return extractAsin(url) != null
    }

    fun parsePrice(html: String, url: String): ParsedPrice? {
        return try {
            val doc: Document = Jsoup.parse(html)
            val asin = extractAsin(url) ?: return null

            // Extract price - try multiple selectors
            val priceSelectors = listOf(
                "span.a-price-whole",
                "#priceblock_ourprice",
                "#priceblock_dealprice",
                ".a-price .a-offscreen",
                "#price_inside_buybox",
                "#newBuyBoxPrice",
                "span.a-price [data-a-color='price']",
                "#corePrice_feature_div span.a-offscreen",
                "#apex_offerDisplay_desktop span.a-offscreen"
            )

            var priceText = ""
            for (selector in priceSelectors) {
                val el = doc.selectFirst(selector)
                if (el != null) {
                    priceText = el.text()
                    break
                }
            }

            val price = parsePriceValue(priceText)
            if (price <= 0) return null

            // Extract currency
            val currency = when {
                priceText.contains("$") || priceText.contains("USD") -> "USD"
                priceText.contains("£") || priceText.contains("GBP") -> "GBP"
                priceText.contains("€") || priceText.contains("EUR") -> "EUR"
                priceText.contains("CAD") -> "CAD"
                else -> "USD"
            }

            // Extract name
            val nameSelectors = listOf("#productTitle", "h1.a-size-large")
            var productName = ""
            for (selector in nameSelectors) {
                val el = doc.selectFirst(selector)
                if (el != null) {
                    productName = el.text().trim()
                    break
                }
            }

            // Extract image
            val imageSelectors = listOf(
                "#landingImage",
                "#imgBlkFront",
                "#main-image",
                "img[data-old-hires]"
            )
            var imageUrl = ""
            for (selector in imageSelectors) {
                val el = doc.selectFirst(selector)
                if (el != null) {
                    imageUrl = el.attr("src")
                    if (imageUrl.isNotEmpty()) break
                    imageUrl = el.attr("data-old-hires")
                    if (imageUrl.isNotEmpty()) break
                }
            }

            ParsedPrice(
                price = price,
                currency = currency,
                productName = productName.ifEmpty { "Unknown Product" },
                imageUrl = imageUrl,
                asin = asin
            )
        } catch (e: Exception) {
            null
        }
    }

    private fun parsePriceValue(text: String): Double {
        val cleaned = text.replace(Regex("[^0-9.,]"), "")
            .replace(",", "")
            .trim()
        return try {
            cleaned.toDoubleOrNull() ?: 0.0
        } catch (e: NumberFormatException) {
            0.0
        }
    }
}
