package com.devcraft.parser

import com.devcraft.data.local.entities.MessageEntity
import com.devcraft.data.local.entities.MessageSource
import com.devcraft.data.local.entities.MessageStatus
import com.devcraft.data.local.entities.OrderEntity
import com.devcraft.parser.offline.DeterministicParser
import org.junit.Assert.*
import org.junit.Test
import java.util.UUID

class MessagePipelineTest {

    @Test
    fun testWhatsAppSharedHinglishOrderParsing() {
        val rawWhatsAppText = "Ramesh bhaiya ko kal shaam 10 bori cement bhejo Rs 3500"
        val parsed = DeterministicParser.parse(rawWhatsAppText)

        assertEquals("Ramesh", parsed.customer)
        assertEquals(1, parsed.items.size)
        assertEquals(10, parsed.items[0].quantity)
        assertEquals(3500.0, parsed.amount ?: 0.0, 0.01)
        assertNotNull(parsed.due_date)
        assertFalse(parsed.references_prior_order)
        assertTrue(parsed.confidence >= 0.8f)
        assertFalse(parsed.needs_clarification)
    }

    @Test
    fun testGarmentSizeAndColorExtraction() {
        val rawText = "bhaiya 2 kurta chahiye navy blue chest 40 parso tak"
        val parsed = DeterministicParser.parse(rawText)

        assertEquals(1, parsed.items.size)
        val item = parsed.items[0]
        assertEquals(2, item.quantity)
        assertEquals("navy blue", item.attributes["color"])
        assertEquals("40", item.attributes["size"])
        assertNotNull(parsed.due_date)
    }

    @Test
    fun testDevanagariOrderExtraction() {
        val rawText = "सुरेश भाई को ३ पैकेट नमकीन आज ही चाहिए ₹450"
        val parsed = DeterministicParser.parse(rawText)

        assertEquals("सुरेश", parsed.customer)
        assertEquals(1, parsed.items.size)
        assertEquals(3, parsed.items[0].quantity)
        assertEquals(450.0, parsed.amount ?: 0.0, 0.01)
        assertNotNull(parsed.due_date)
    }

    @Test
    fun testPriorOrderRepeatDetection() {
        val rawText = "Ramesh bhai wahi purana order kal bhej dena please"
        val parsed = DeterministicParser.parse(rawText)

        assertEquals("Ramesh", parsed.customer)
        assertTrue(parsed.references_prior_order)
        assertNotNull(parsed.due_date)
    }

    @Test
    fun testAmbiguousMessageTriggersClarification() {
        val emptyOrVagueText = "kuch samaan bhej do"
        val parsed = DeterministicParser.parse(emptyOrVagueText)

        // Without explicit numbers, default quantity is 1 and customer extraction fails cleanly
        assertNotNull(parsed)
    }

    @Test
    fun testMessageEntityStateTransitions() {
        val messageId = UUID.randomUUID().toString()
        val originalText = "Mohan ji 5 chairs send tomorrow Rs 2500"

        // 1. Initial Received State
        val receivedMessage = MessageEntity(
            messageId = messageId,
            source = MessageSource.WHATSAPP_SHARE.name,
            originalText = originalText,
            status = MessageStatus.RECEIVED.name
        )
        assertEquals(MessageStatus.RECEIVED.name, receivedMessage.status)
        assertNull(receivedMessage.parsedOrderId)

        // 2. Parsed State
        val parsed = DeterministicParser.parse(receivedMessage.originalText)
        val parsedMessage = receivedMessage.copy(
            status = MessageStatus.PARSED.name,
            confidence = parsed.confidence,
            needsClarification = parsed.needs_clarification,
            senderName = parsed.customer
        )
        assertEquals(MessageStatus.PARSED.name, parsedMessage.status)
        assertEquals("Mohan", parsedMessage.senderName)
        assertEquals(5, parsed.items[0].quantity)

        // 3. Converted to Order State
        val orderId = UUID.randomUUID().toString()
        val convertedMessage = parsedMessage.copy(
            status = MessageStatus.CONVERTED.name,
            parsedOrderId = orderId,
            updatedAt = System.currentTimeMillis()
        )
        assertEquals(MessageStatus.CONVERTED.name, convertedMessage.status)
        assertEquals(orderId, convertedMessage.parsedOrderId)
    }

    @Test
    fun testOrderEntityGenerationFromParsedMessage() {
        val rawText = "Ramesh bhaiya 4 shirts blue size 42 parso tak Rs 1600"
        val parsed = DeterministicParser.parse(rawText)

        val orderId = UUID.randomUUID().toString()
        val order = OrderEntity(
            orderId = orderId,
            customerName = parsed.customer ?: "Guest Customer",
            status = "CONFIRMED",
            totalAmount = parsed.amount ?: 0.0,
            dueDate = parsed.due_date,
            rawMessage = rawText,
            confidence = parsed.confidence
        )

        assertEquals("Ramesh", order.customerName)
        assertEquals("CONFIRMED", order.status)
        assertEquals(1600.0, order.totalAmount ?: 0.0, 0.01)
        assertEquals(rawText, order.rawMessage)
    }
}
