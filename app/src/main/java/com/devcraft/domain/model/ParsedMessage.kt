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
    val needs_clarification: Boolean = false
)
