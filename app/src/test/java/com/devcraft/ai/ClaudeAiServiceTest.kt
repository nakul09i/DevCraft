package com.devcraft.ai

import com.devcraft.data.local.entities.OrderEntity
import com.devcraft.domain.model.ParsedMessage
import com.devcraft.parser.offline.MessageCategory
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test

class ClaudeAiServiceTest {

    @Test
    fun `test privacy gate keeps non-order messages strictly local`() = runBlocking {
        val service = ClaudeAiService()

        val otpText = "Your OTP for login is 987654. Do not share with anyone."
        val otpLocal = ParsedMessage(
            classification = MessageCategory.OTP_AUTHENTICATION,
            confidence = 0.99f
        )

        val result = service.interpretAmbiguousMessage(otpText, otpLocal)

        // Privacy rule: Non-order OTP is returned directly without calling AI
        assertEquals(MessageCategory.OTP_AUTHENTICATION, result.classification)
        assertEquals(0.99f, result.confidence, 0.001f)
    }

    @Test
    fun `test controlled tool execution for business chat`() = runBlocking {
        val service = ClaudeAiService()

        val orders = listOf(
            OrderEntity(orderNumber = "#1", status = "COMPLETED", totalAmount = 500.0),
            OrderEntity(orderNumber = "#2", status = "NEW", totalAmount = 300.0)
        )

        val answer = service.askDevCraftAi("What is today's revenue?", orders)
        assertNotNull(answer)
        assertTrue(answer.isNotEmpty())
    }
}
