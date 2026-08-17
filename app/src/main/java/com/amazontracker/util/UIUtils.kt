package com.amazontracker.util

import android.app.Activity
import android.view.View
import android.widget.Toast
import com.google.android.material.snackbar.Snackbar
import java.text.NumberFormat
import java.util.Locale

object UIUtils {

    fun formatPrice(price: Double, currency: String = "USD"): String {
        val locale = when (currency) {
            "GBP" -> Locale.UK
            "EUR" -> Locale.GERMANY
            "CAD" -> Locale.CANADA
            else -> Locale.US
        }
        val format = NumberFormat.getCurrencyInstance(locale)
        return format.format(price)
    }

    fun formatTimestamp(timestamp: Long): String {
        val sdf = java.text.SimpleDateFormat("MMM dd, yyyy HH:mm", Locale.getDefault())
        return sdf.format(java.util.Date(timestamp))
    }

    fun formatDate(timestamp: Long): String {
        val sdf = java.text.SimpleDateFormat("MMM dd, yyyy", Locale.getDefault())
        return sdf.format(java.util.Date(timestamp))
    }

    fun showToast(activity: Activity, message: String) {
        activity.runOnUiThread {
            Toast.makeText(activity, message, Toast.LENGTH_SHORT).show()
        }
    }

    fun showSnackbar(view: View, message: String, action: String? = null, actionListener: View.OnClickListener? = null) {
        val snackbar = Snackbar.make(view, message, Snackbar.LENGTH_LONG)
        if (action != null && actionListener != null) {
            snackbar.setAction(action, actionListener)
        }
        snackbar.show()
    }
}
