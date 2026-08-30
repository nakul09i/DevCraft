package com.devcraft.ai

import com.devcraft.data.local.entities.OrderEntity
import com.devcraft.domain.model.ParsedItem
import com.devcraft.domain.model.ParsedMessage
import com.devcraft.parser.offline.DeterministicParser
import com.devcraft.parser.offline.MessageCategory
import com.devcraft.parser.offline.MessageClassifier
import com.google.gson.JsonParser
import java.util.Locale

class ClaudeAiService(
    private val client: ClaudeApiClient = ClaudeApiClient()
) {
    val status: AiStatus get() = AiStatus.ONLINE
    val requestsTodayCount: Int get() = client.requestsTodayCount

    /**
     * Enhances low-confidence message parsing when online.
     * PRIVACY RULE: Non-order messages (BANK, OTP, PERSONAL, etc.) are strictly kept local.
     */
    suspend fun interpretAmbiguousMessage(
        rawText: String,
        localParsed: ParsedMessage = DeterministicParser.parse(rawText)
    ): ParsedMessage {
        // 1. High confidence or non-order message -> return local result immediately without cloud call
        if (localParsed.confidence >= 0.85f || !localParsed.classification.isOrder) {
            return localParsed
        }

        // 2. Privacy Gate: Double check classification
        val category = MessageClassifier.classify(rawText)
        if (!category.isOrder) {
            return localParsed
        }

        // 3. Online AI enhancement for ambiguous orders
        val prompt = """
            Extract structured order details from this message: "$rawText".
            Return ONLY valid JSON matching this schema:
            {
              "customer": "Customer Name or null",
              "items": [{"description": "item", "quantity": 1}],
              "amount": 500.0,
              "due_date": "YYYY-MM-DD or null",
              "delivery_address": "address or null",
              "payment_method": "COD/UPI/CARD or null",
              "needs_clarification": false
            }
            Do NOT invent missing fields.
        """.trimIndent()

        val response = client.queryClaude(prompt)
        if (!response.isSuccess || response.data == null) {
            return localParsed
        }

        return try {
            val json = JsonParser.parseString(response.data).asJsonObject
            val customer = json.get("customer")?.takeIf { !it.isJsonNull }?.asString ?: localParsed.customer
            val amount = json.get("amount")?.takeIf { !it.isJsonNull }?.asDouble ?: localParsed.amount
            val dueDate = json.get("due_date")?.takeIf { !it.isJsonNull }?.asString ?: localParsed.due_date
            val address = json.get("delivery_address")?.takeIf { !it.isJsonNull }?.asString ?: localParsed.delivery_address
            val payment = json.get("payment_method")?.takeIf { !it.isJsonNull }?.asString ?: localParsed.payment_method

            localParsed.copy(
                customer = customer,
                amount = amount,
                due_date = dueDate,
                delivery_address = address,
                payment_method = payment,
                confidence = maxOf(localParsed.confidence, 0.90f),
                review_notes = localParsed.review_notes + "Enhanced by Claude AI"
            )
        } catch (e: Exception) {
            localParsed
        }
    }

    /**
     * Generates a natural Hindi / Hinglish clarification question for missing fields.
     */
    suspend fun generateClarificationQuestion(missingFields: List<String>, contextText: String): String {
        if (missingFields.isEmpty()) return "All details are complete."
        val fieldsStr = missingFields.joinToString(", ")
        
        val prompt = "Create a polite, short 1-line Hindi/Hinglish customer clarification question asking for missing fields ($fieldsStr) for order context: '$contextText'."
        val response = client.queryClaude(prompt)
        
        return if (response.isSuccess && !response.data.isNullOrBlank()) {
            response.data
        } else {
            "Please clarify the following missing details: $fieldsStr."
        }
    }

    /**
     * Business chat tool execution over real local orders.
     */
    suspend fun askDevCraftAi(query: String, orders: List<OrderEntity>): String {
        val lower = query.lowercase(Locale.ROOT)
        
        // Controlled local tool execution
        val totalRevenue = orders.sumOf { it.totalAmount ?: 0.0 }
        val pendingCount = orders.count { it.status == "NEW" || it.status == "CONFIRMED" }
        val overdueCount = orders.count { it.dueDate != null && it.status != "COMPLETED" }

        val contextInfo = "Total Orders: ${orders.size}, Pending: $pendingCount, Overdue: $overdueCount, Revenue: ₹$totalRevenue."

        val prompt = "User asked: '$query'. Current real business context: '$contextInfo'. Answer concisely in English or Hinglish using ONLY these real facts."
        val response = client.queryClaude(prompt)

        return if (response.isSuccess && !response.data.isNullOrBlank()) {
            response.data
        } else {
            "DevCraft Summary: $contextInfo"
        }
    }

    /**
     * Customer reply drafting (Confirmation, Delay, Payment Reminder).
     */
    suspend fun draftCustomerReply(order: OrderEntity, intent: String): String {
        val prompt = "Draft a professional 2-line WhatsApp reply to customer ${order.customerName ?: "Customer"} for order ${order.orderNumber} with intent: $intent."
        val response = client.queryClaude(prompt)
        return response.data ?: "Hello ${order.customerName ?: "Customer"}, regarding your order ${order.orderNumber}: $intent."
    }

    /**
     * Daily business insights report.
     */
    suspend fun generateBusinessReport(orders: List<OrderEntity>): String {
        val count = orders.size
        val completed = orders.count { it.status == "COMPLETED" }
        val revenue = orders.sumOf { it.totalAmount ?: 0.0 }
        
        val prompt = "Write a 3-paragraph daily business report for $count orders ($completed completed, total revenue ₹$revenue)."
        val response = client.queryClaude(prompt)
        return response.data ?: "DAILY SUMMARY: $count orders processed, $completed completed, Total Revenue: ₹$revenue."
    }
}
