package com.devcraft.order

import com.devcraft.data.local.entities.OrderEntity
import org.junit.Assert.*
import org.junit.Test

class OmnichannelOrderTest {

    @Test
    fun `test omnichannel order unification`() {
        val webOrder = OrderEntity(
            orderNumber = "#WEB-1042",
            source = "WEBSITE",
            customerName = "Nakul",
            phone = "9876543210",
            totalAmount = 500.0,
            dueDate = "2026-08-31",
            status = "NEW",
            paymentMethod = "COD"
        )

        val smsOrder = OrderEntity(
            orderNumber = "#SMS-8812",
            source = "SMS",
            customerName = "Vikram",
            phone = "9826012345",
            totalAmount = 850.0,
            dueDate = "2026-09-01",
            status = "CONFIRMED",
            paymentMethod = "UPI"
        )

        val manualOrder = OrderEntity(
            orderNumber = "#MAN-5001",
            source = "MANUAL",
            customerName = "Aman",
            totalAmount = 1200.0,
            status = "PROCESSING"
        )

        val orders = listOf(webOrder, smsOrder, manualOrder)

        assertEquals(3, orders.size)
        assertEquals("WEBSITE", webOrder.source)
        assertEquals("SMS", smsOrder.source)
        assertEquals("MANUAL", manualOrder.source)
        assertEquals(2550.0, orders.sumOf { it.totalAmount ?: 0.0 }, 0.01)
    }
}
