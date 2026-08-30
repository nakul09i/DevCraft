package com.devcraft.domain.model

data class ParsedItem(
    val description: String,
    val quantity: Int = 1,
    val attributes: Map<String, String> = emptyMap()
)

data class ParsedMessage(
    val customer: String? = null,
    val items: List<ParsedItem> = emptyList(),
    val due_date: String? = null, // YYYY-MM-DD
    val amount: Double? = null,
    val references_prior_order: Boolean = false,
    val confidence: Float = 0.9f,
    val needs_clarification: Boolean = false,
    /**
     * Delivery address as written in the message. Text only - no geocoding, so
     * this needs no API key and works offline. Coordinates stay null until a
     * mapping provider is configured.
     */
    val delivery_address: String? = null,
    /** Indian PIN code if one was stated. */
    val pincode: String? = null,
    /** Phone number if explicitly present in the message. */
    val phone: String? = null
) {
    /** True when anything location-ish was found. */
    val hasLocation: Boolean get() = !delivery_address.isNullOrBlank() || !pincode.isNullOrBlank()
}
