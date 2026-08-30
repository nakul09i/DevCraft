package com.devcraft.export

import com.devcraft.data.local.dao.CustomerBalance
import com.devcraft.data.local.dao.OrderWithItems
import com.devcraft.data.local.entities.OrderEntity
import com.devcraft.data.local.entities.OrderItemEntity
import org.junit.Assert.*
import org.junit.Test

class CsvExportTest {

    @Test
    fun testOrdersCsvGeneration_properlyFormatsRows() {
        val order = OrderEntity(
            orderId = "order-001",
            customerId = "cust-001",
            customerName = "Nakul Chourey",
            status = "CONFIRMED",
            totalAmount = 1500.0,
            dueDate = "2026-08-30",
            rawMessage = "Nakul 2 food parcels Bhopal 1500 COD",
            createdAt = 1725000000000L
        )
        val items = listOf(
            OrderItemEntity(itemId = "item-1", orderId = "order-001", description = "food parcels", quantity = 2),
            OrderItemEntity(itemId = "item-2", orderId = "order-001", description = "sweet box", quantity = 1)
        )
        val orderWithItems = OrderWithItems(order = order, items = items)

        val csv = CsvExportManager.generateOrdersCsv(listOf(orderWithItems))

        assertTrue(csv.contains("Order ID,Customer Name,Status,Total Amount (INR)"))
        assertTrue(csv.contains("order-001"))
        assertTrue(csv.contains("Nakul Chourey"))
        assertTrue(csv.contains("1500.00"))
        assertTrue(csv.contains("2x food parcels; 1x sweet box"))
        assertTrue(csv.contains(",3,")) // total quantity
    }

    @Test
    fun testCustomerBalancesCsvGeneration() {
        val balances = listOf(
            CustomerBalance(customerName = "Ramesh Kumar", outstanding = 4500.50, openOrders = 3),
            CustomerBalance(customerName = "Suresh Bhai", outstanding = 1200.00, openOrders = 1)
        )

        val csv = CsvExportManager.generateCustomerBalancesCsv(balances)

        assertTrue(csv.contains("Customer Name,Outstanding Amount (INR),Open Orders Count"))
        assertTrue(csv.contains("Ramesh Kumar,4500.50,3"))
        assertTrue(csv.contains("Suresh Bhai,1200.00,1"))
    }

    @Test
    fun testCsvEscaping_handlesCommasAndQuotes() {
        assertEquals("Plain text", CsvExportManager.escapeCsv("Plain text"))
        assertEquals("\"Text, with comma\"", CsvExportManager.escapeCsv("Text, with comma"))
        assertEquals("\"Text with \"\"quotes\"\"\"", CsvExportManager.escapeCsv("Text with \"quotes\""))
        assertEquals("\"Line1\nLine2\"", CsvExportManager.escapeCsv("Line1\nLine2"))
    }
}
