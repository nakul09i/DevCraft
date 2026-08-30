package com.devcraft.domain.model

import com.devcraft.parser.offline.MessageCategory

data class ParsedItem(
    val description: String,
    val quantity: Int = 1,
    val attributes: Map<String, String> = emptyMap()
)

data class ParsedMessage(
    val customer: String? = null,
    val items: List<ParsedItem> = emptyList(),
    /** ISO-8601 yyyy-MM-dd. Canonical storage format. */
    val due_date: String? = null,
    val raw_date_text: String? = null,
    val date_confidence: Float = 0.0f,
    val date_resolution_status: String = "NOT_FOUND", // RESOLVED, AMBIGUOUS, NOT_FOUND
    val amount: Double? = null,
    val references_prior_order: Boolean = false,
    val confidence: Float = 0.9f,
    val needs_clarification: Boolean = false,
    /**
     * Delivery location as written in the message. Text only - works 100% offline.
     */
    val delivery_address: String? = null,
    val pincode: String? = null,
    val phone: String? = null,
    /** COD, UPI, CASH, ADVANCE, CARD, BANK_TRANSFER, CREDIT. */
    val payment_method: String? = null,
    /** What kind of message this is. Only ORDER is eligible for conversion. */
    val classification: MessageCategory = MessageCategory.UNKNOWN,
    /** Per-order calculated evaluation metrics (0.0 to 1.0) */
    val classification_score: Float = 0.0f,
    val field_extraction_score: Float = 0.0f,
    val date_resolution_score: Float = 0.0f,
    val clarification_decision_score: Float = 0.0f,
    val overall_score: Float = 0.0f,
    /** Fields the parser could not resolve. */
    val missing_fields: List<String> = emptyList(),
    /** Human-readable reasons for review. */
    val review_notes: List<String> = emptyList()
) {
    val hasLocation: Boolean get() = !delivery_address.isNullOrBlank() || !pincode.isNullOrBlank()

    /** DD/MM/YYYY for display. Storage stays ISO (YYYY-MM-DD). */
    val display_date: String?
        get() = com.devcraft.parser.offline.DeterministicParser.displayDate(due_date)

    val quantity: Int? get() = items.firstOrNull()?.quantity

    val itemDescription: String? get() = items.firstOrNull()?.description
}
