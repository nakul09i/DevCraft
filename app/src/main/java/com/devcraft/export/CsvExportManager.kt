package com.devcraft.export

import android.content.Context
import android.content.Intent
import androidx.core.content.FileProvider
import com.devcraft.data.local.dao.CustomerBalance
import com.devcraft.data.local.dao.OrderWithItems
import java.io.File
import java.text.SimpleDateFormat
import java.util.*

object CsvExportManager {

    /**
     * Generates a standard RFC 4180 compliant CSV string for all orders and items.
     */
    fun generateOrdersCsv(ordersWithItems: List<OrderWithItems>): String {
        val sb = StringBuilder()
        // CSV Header
        sb.append("Order ID,Customer Name,Status,Total Amount (INR),Due Date,Items Breakdown,Total Quantity,Delivery Address,Created At,Raw Message\n")

        val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.US)

        for (item in ordersWithItems) {
            val order = item.order
            val itemsSummary = item.items.joinToString("; ") { "${it.quantity}x ${it.description}" }
            val totalQty = item.items.sumOf { it.quantity }
            val createdStr = dateFormat.format(Date(order.createdAt))

            sb.append(escapeCsv(order.orderId)).append(",")
            sb.append(escapeCsv(order.customerName ?: "")).append(",")
            sb.append(escapeCsv(order.status)).append(",")

            sb.append(String.format(Locale.US, "%.2f", order.totalAmount)).append(",")
            sb.append(escapeCsv(order.dueDate ?: "")).append(",")
            sb.append(escapeCsv(itemsSummary)).append(",")
            sb.append(totalQty).append(",")
            sb.append(escapeCsv(order.formattedAddress ?: "")).append(",")
            sb.append(escapeCsv(createdStr)).append(",")
            sb.append(escapeCsv(order.rawMessage ?: "")).append("\n")
        }

        return sb.toString()
    }

    /**
     * Generates a CSV string for Customer Balances / Khata report.
     */
    fun generateCustomerBalancesCsv(balances: List<CustomerBalance>): String {
        val sb = StringBuilder()
        sb.append("Customer Name,Outstanding Amount (INR),Open Orders Count\n")

        for (b in balances) {
            sb.append(escapeCsv(b.customerName)).append(",")
            sb.append(String.format(Locale.US, "%.2f", b.outstanding)).append(",")
            sb.append(b.openOrders).append("\n")
        }

        return sb.toString()
    }

    /**
     * Saves CSV to the app's cache directory and triggers Android share intent.
     */
    fun exportAndShareOrders(context: Context, orders: List<OrderWithItems>): Boolean {
        val csvContent = generateOrdersCsv(orders)
        val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
        val fileName = "DevCraft_Orders_$timestamp.csv"
        return shareCsvFile(context, fileName, csvContent, "Share Orders Spreadsheet")
    }

    /**
     * Saves Customer Balances CSV to the app's cache directory and triggers share intent.
     */
    fun exportAndShareBalances(context: Context, balances: List<CustomerBalance>): Boolean {
        val csvContent = generateCustomerBalancesCsv(balances)
        val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
        val fileName = "DevCraft_Khata_Balances_$timestamp.csv"
        return shareCsvFile(context, fileName, csvContent, "Share Customer Khata Report")
    }

    private fun shareCsvFile(
        context: Context,
        fileName: String,
        content: String,
        chooserTitle: String
    ): Boolean {
        return runCatching {
            val exportDir = File(context.cacheDir, "exports").apply { mkdirs() }
            val file = File(exportDir, fileName)
            file.writeText(content, Charsets.UTF_8)

            val uri = FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                file
            )

            val intent = Intent(Intent.ACTION_SEND).apply {
                type = "text/csv"
                putExtra(Intent.EXTRA_STREAM, uri)
                putExtra(Intent.EXTRA_SUBJECT, fileName)
                putExtra(Intent.EXTRA_TEXT, "Exported spreadsheet from DevCraft.")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }

            val chooser = Intent.createChooser(intent, chooserTitle).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(chooser)
            true
        }.getOrElse {
            it.printStackTrace()
            false
        }
    }

    /**
     * Escapes CSV values conforming to RFC 4180.
     */
    fun escapeCsv(value: String): String {
        if (!value.contains(",") && !value.contains("\"") && !value.contains("\n") && !value.contains("\r")) {
            return value
        }
        return "\"" + value.replace("\"", "\"\"") + "\""
    }
}
