package com.devcraft.parser.offline

import com.devcraft.domain.model.ParsedItem
import com.devcraft.domain.model.ParsedMessage
import java.text.SimpleDateFormat
import java.util.*
import java.util.regex.Pattern

object DeterministicParser {

    private val HINDI_NUMBERS = mapOf(
        "ek" to 1, "এক" to 1, "एक" to 1, "1" to 1, "१" to 1,
        "do" to 2, "दो" to 2, "2" to 2, "२" to 2,
        "teen" to 3, "तीन" to 3, "3" to 3, "३" to 3,
        "chaar" to 4, "चार" to 4, "4" to 4, "४" to 4,
        "paanch" to 5, "panch" to 5, "पांच" to 5, "5" to 5, "५" to 5,
        "chhah" to 6, "छह" to 6, "6" to 6, "६" to 6,
        "saat" to 7, "सात" to 7, "7" to 7, "७" to 7,
        "aath" to 8, "आठ" to 8, "8" to 8, "८" to 8,
        "nau" to 9, "नौ" to 9, "9" to 9, "९" to 9,
        "das" to 10, "दस" to 10, "10" to 10
    )

    fun parse(text: String): ParsedMessage {
        val trimmed = text.trim()
        if (trimmed.isEmpty()) {
            return ParsedMessage(confidence = 0.0f, needs_clarification = true)
        }

        val lowerText = trimmed.lowercase(Locale.ROOT)

        val referencesPrior = lowerText.contains("purana") ||
                lowerText.contains("wahi") ||
                lowerText.contains("repeat") ||
                lowerText.contains("same as last") ||
                lowerText.contains("pichla")

        val dueDate = resolveDueDate(lowerText)
        val customerName = extractCustomer(trimmed)
        val quantity = extractQuantity(lowerText)
        val amount = extractAmount(lowerText)

        val attributes = mutableMapOf<String, String>()
        val colors = listOf("navy blue", "blue", "red", "black", "white", "green", "yellow")
        for (c in colors) {
            if (lowerText.contains(c)) {
                attributes["color"] = c
                break
            }
        }
        val sizePattern = Pattern.compile("(chest|size)\\s*(\\d+)")
        val sizeMatcher = sizePattern.matcher(lowerText)
        if (sizeMatcher.find()) {
            attributes["size"] = sizeMatcher.group(2) ?: ""
        }

        val itemDesc = extractItemDescription(lowerText)
        val parsedItem = ParsedItem(
            description = if (itemDesc.isNotBlank()) itemDesc else "Order Item",
            quantity = quantity,
            attributes = attributes
        )

        val confidence = if (parsedItem.quantity > 0) 0.95f else 0.5f

        return ParsedMessage(
            customer = customerName,
            items = listOf(parsedItem),
            due_date = dueDate,
            amount = amount,
            references_prior_order = referencesPrior,
            confidence = confidence,
            needs_clarification = confidence < 0.7f
        )
    }

    private fun resolveDueDate(text: String): String? {
        val cal = Calendar.getInstance()
        val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())

        return when {
            text.contains("aaj") || text.contains("today") -> sdf.format(cal.time)
            text.contains("kal") || text.contains("tomorrow") -> {
                cal.add(Calendar.DAY_OF_YEAR, 1)
                sdf.format(cal.time)
            }
            text.contains("parso") || text.contains("parson") || text.contains("day after tomorrow") -> {
                cal.add(Calendar.DAY_OF_YEAR, 2)
                sdf.format(cal.time)
            }
            text.contains("next week") || text.contains("agle hafte") -> {
                cal.add(Calendar.DAY_OF_YEAR, 7)
                sdf.format(cal.time)
            }
            else -> null
        }
    }

    private fun extractCustomer(text: String): String? {
        val words = text.split("\\s+".toRegex())
        val honorifics = listOf("bhaiya", "bhai", "ji", "shri", "bhaiya,", "bhai,", "भाई", "भैया", "जी")
        
        for (i in words.indices) {
            val w = words[i].lowercase(Locale.ROOT)
            if (w in honorifics || words[i] in honorifics) {
                if (i > 0 && words[i-1].lowercase(Locale.ROOT) !in honorifics && words[i-1] !in honorifics) {
                    return words[i-1].replaceFirstChar { it.uppercase() }
                } else if (i < words.size - 1 && words[i+1].lowercase(Locale.ROOT) !in honorifics && words[i+1] !in honorifics) {
                    val candidate = words[i+1]
                    if (!candidate.first().isDigit()) {
                        return candidate.replaceFirstChar { it.uppercase() }
                    }
                }
            }
        }
        return "Customer"
    }

    private fun extractQuantity(text: String): Int {
        for ((word, q) in HINDI_NUMBERS) {
            if (text.contains(word)) {
                return q
            }
        }
        val matcher = Pattern.compile("\\b(\\d+)\\b").matcher(text)
        if (matcher.find()) {
            return matcher.group(1)?.toIntOrNull() ?: 1
        }
        return 1
    }

    private fun extractAmount(text: String): Double? {
        val pattern = Pattern.compile("(rs|rupees|inr|\\u20B9)\\s*(\\d+(\\.\\d+)?)")
        val matcher = pattern.matcher(text)
        if (matcher.find()) {
            return matcher.group(2)?.toDoubleOrNull()
        }
        return null
    }

    private fun extractItemDescription(text: String): String {
        val stopWords = listOf("bhaiya", "bhai", "chahiye", "tak", "parso", "kal", "send", "please", "karo", "wahi", "purana", "order")
        var cleaned = text
        for (sw in stopWords) {
            cleaned = cleaned.replace("\\b$sw\\b".toRegex(), "")
        }
        return cleaned.replace("\\s+".toRegex(), " ").trim()
    }
}
