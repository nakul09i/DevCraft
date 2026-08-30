package com.devcraft.parser.offline

import com.devcraft.domain.model.ParsedItem
import com.devcraft.domain.model.ParsedMessage
import java.text.SimpleDateFormat
import java.util.*
import java.util.regex.Pattern

/**
 * Offline, rule-based multilingual order parser (English / Hindi / Hinglish /
 * Devanagari). No network, no model, fully deterministic.
 *
 * Everything matches on *tokens*, never on raw substrings. The previous
 * substring approach silently misread real orders: "10 bori" scored quantity 1
 * because the text contains "1", and "5 chairs ... Rs 2500" scored 2 because it
 * contains "2".
 */
object DeterministicParser {

    private val NUMBER_WORDS: Map<String, Int> = mapOf(
        "ek" to 1, "एक" to 1,
        "do" to 2, "दो" to 2,
        "teen" to 3, "tin" to 3, "तीन" to 3,
        "chaar" to 4, "char" to 4, "चार" to 4,
        "paanch" to 5, "panch" to 5, "पांच" to 5, "पाँच" to 5,
        "chhah" to 6, "chah" to 6, "che" to 6, "छह" to 6,
        "saat" to 7, "सात" to 7,
        "aath" to 8, "आठ" to 8,
        "nau" to 9, "नौ" to 9,
        "das" to 10, "दस" to 10,
        "barah" to 12, "बारह" to 12,
        "bees" to 20, "बीस" to 20,
        "pachas" to 50, "पचास" to 50,
        "sau" to 100, "सौ" to 100,
        "hazaar" to 1000, "hazar" to 1000, "हजार" to 1000, "हज़ार" to 1000,
    )

    /** Fractional Hindi quantities. Rounded up: half a sack is still a sack to pack. */
    private val FRACTION_WORDS: Map<String, Int> = mapOf(
        "dedh" to 2, "डेढ़" to 2, "डेढ" to 2,   // 1.5
        "dhai" to 3, "ढाई" to 3,                 // 2.5
        "adha" to 1, "aadha" to 1, "आधा" to 1,   // 0.5
    )

    private val HONORIFICS = setOf(
        "bhaiya", "bhai", "ji", "shri", "sahab", "saheb", "madam",
        "भाई", "भैया", "जी", "श्री", "साहब",
    )

    private val TODAY = setOf("aaj", "aj", "today", "आज")
    private val TOMORROW = setOf("kal", "kl", "tomorrow", "कल")
    private val DAY_AFTER = setOf("parso", "parson", "parsoon", "परसों", "परसो")
    private val NEXT_WEEK_PHRASES = listOf("next week", "agle hafte", "agle hafta", "अगले हफ्ते")
    private val PRIOR_ORDER_TOKENS = setOf(
        "purana", "puraana", "wahi", "vahi", "repeat", "pichla", "pichhla",
        "पुराना", "वही", "पिछला",
    )
    private val PRIOR_ORDER_PHRASES = listOf("same as last", "same as before", "like last time")

    private val CURRENCY = setOf("rs", "rs.", "rupees", "rupee", "rupaye", "rupaiya", "inr", "₹", "रु", "रुपये")
    private val MEASURE_PREFIX = setOf("chest", "size", "saiz", "no", "number", "नंबर")

    /**
     * Verb stems after which "do"/"lo" are imperatives ("bhej do" = please send),
     * not the numeral two. CLAUDE.md flags exactly this Hinglish collision.
     */
    private val IMPERATIVE_STEMS = setOf(
        "bhej", "bhejo", "kar", "karo", "de", "le", "daal", "dal", "likh",
        "भेज", "कर", "दे", "ले",
    )
    private val AMBIGUOUS_NUMERALS = setOf("do", "दो", "le", "lo")

    /** Tokens that never belong in an item description. */
    private val DESCRIPTION_NOISE = setOf(
        "ko", "ka", "ki", "ke", "se", "me", "mein", "par", "tak", "tk", "hi", "bhi",
        "को", "का", "की", "के", "से", "में", "तक", "ही", "भी",
        "chahiye", "chaiye", "chahiya", "चाहिए", "bhejo", "bhej", "bhejna", "bhejdena",
        "dena", "do", "karo", "kar", "send", "please", "pls", "need", "want", "order",
        "भेजो", "भेज", "देना", "करो", "और", "and", "for", "the", "a", "an",
        "shaam", "subah", "raat", "dopahar", "शाम", "सुबह", "रात",
    )

    private val COLORS = listOf(
        "navy blue", "sky blue", "light blue", "dark blue",
        "blue", "red", "black", "white", "green", "yellow", "grey", "gray", "brown", "orange", "pink",
        "नीला", "लाल", "काला", "सफेद", "हरा", "पीला",
    )

    // \b on the alpha spellings so "cars 500" cannot match "rs 500". The symbol
    // forms (₹, रु) are non-word characters and must not carry \b.
    private const val CURRENCY_ALT =
        "\\brs\\.?|\\brupees?|\\brupaye|\\brupaiya|\\binr|\\u20B9|\\u0930\\u0941"
    private const val NUM = "[0-9\\u0966-\\u096F]+(?:\\.[0-9]+)?"

    private val AMOUNT_PATTERN: Pattern = Pattern.compile(
        // prefixed: Rs 3500 / ₹450 / inr 1600     suffixed: 3500 rupees
        "(?:$CURRENCY_ALT)\\s*($NUM)|($NUM)\\s*(?:$CURRENCY_ALT)",
        Pattern.CASE_INSENSITIVE,
    )

    private val SIZE_PATTERN: Pattern = Pattern.compile("(?:chest|size|saiz)\\s*([0-9\\u0966-\\u096F]+)")
    // \p{M} is essential: Devanagari matras and the anusvara are combining marks,
    // not letters. Without it "परसों" shreds to "परस" and "बोरी" to "बोर".
    private val TOKEN_SPLIT = Regex("[^\\p{L}\\p{M}\\p{N}.]+")

    fun parse(text: String): ParsedMessage {
        val trimmed = text.trim()
        if (trimmed.isEmpty()) {
            return ParsedMessage(confidence = 0.0f, needs_clarification = true)
        }

        val lower = trimmed.lowercase(Locale.ROOT)
        val tokens = tokenize(lower)

        val amountDigits = extractAmountDigits(lower)
        val amount = amountDigits?.let { toAsciiDigits(it)?.toDoubleOrNull() }

        val quantity = extractQuantity(tokens, amountDigits)
        val customer = extractCustomer(trimmed)
        val dueDate = resolveDueDate(tokens, lower)
        val referencesPrior = tokens.any { it in PRIOR_ORDER_TOKENS } ||
            PRIOR_ORDER_PHRASES.any { lower.contains(it) }

        val attributes = mutableMapOf<String, String>()
        COLORS.firstOrNull { lower.contains(it) }?.let { attributes["color"] = it }
        SIZE_PATTERN.matcher(lower).let { m ->
            if (m.find()) toAsciiDigits(m.group(1) ?: "")?.let { attributes["size"] = it }
        }

        val description = extractItemDescription(tokens, customer, attributes.values)
        val item = ParsedItem(
            description = description.ifBlank { "Order Item" },
            quantity = quantity ?: 1,
            attributes = attributes,
        )

        // Confidence reflects what was actually resolved, so the ambiguity guard
        // can genuinely fire instead of being hardcoded optimistic.
        // Penalties chosen so no combination lands exactly on the 0.7 threshold.
        // Missing quantity alone (0.65) is enough to demand clarification; a
        // missing customer alone (0.80) is not, since the sender name can fill in.
        var confidence = 0.95f
        if (quantity == null) confidence -= 0.30f
        if (customer == null) confidence -= 0.15f
        if (description.isBlank()) confidence -= 0.15f

        return ParsedMessage(
            customer = customer,
            items = listOf(item),
            due_date = dueDate,
            amount = amount,
            references_prior_order = referencesPrior,
            confidence = confidence,
            needs_clarification = confidence < 0.7f,
        )
    }

    private fun tokenize(lowerText: String): List<String> =
        lowerText.split(TOKEN_SPLIT)
            .map { it.trim('.') }
            .filter { it.isNotEmpty() }

    /** Devanagari numerals (०-९) to ASCII. Returns null if the token is not all digits. */
    private fun toAsciiDigits(token: String): String? {
        if (token.isEmpty()) return null
        val sb = StringBuilder(token.length)
        for (c in token) {
            when {
                c in '0'..'9' -> sb.append(c)
                c in '०'..'९' -> sb.append('0' + (c - '०'))
                c == '.' -> sb.append(c)
                else -> return null
            }
        }
        return sb.toString()
    }

    private fun extractAmountDigits(lowerText: String): String? {
        val m = AMOUNT_PATTERN.matcher(lowerText)
        if (!m.find()) return null
        return m.group(1) ?: m.group(2)
    }

    /**
     * First numeric or number-word token that is not the order amount and not a
     * measurement (chest 40, size 42). Returns null when no quantity was stated,
     * which lowers confidence rather than silently defaulting to 1.
     */
    private fun extractQuantity(tokens: List<String>, amountDigits: String?): Int? {
        val amountAscii = amountDigits?.let { toAsciiDigits(it) }
        tokens.forEachIndexed { i, token ->
            val prev = tokens.getOrNull(i - 1) ?: ""
            val next = tokens.getOrNull(i + 1) ?: ""

            val digits = toAsciiDigits(token)
            if (digits != null) {
                val isAmount = digits == amountAscii || prev in CURRENCY || next in CURRENCY
                val isMeasure = prev in MEASURE_PREFIX
                if (!isAmount && !isMeasure) {
                    digits.toIntOrNull()?.let { if (it > 0) return it }
                }
                return@forEachIndexed
            }

            FRACTION_WORDS[token]?.let { return it }
            NUMBER_WORDS[token]?.let { qty ->
                // "bhej do" is "please send", not "send 2"
                if (token in AMBIGUOUS_NUMERALS && prev in IMPERATIVE_STEMS) return@forEachIndexed
                // "do sau" = 200, "das hazaar" = 10000
                val multiplier = NUMBER_WORDS[next]
                if (multiplier != null && multiplier >= 100) return qty * multiplier
                if (qty >= 100) return@forEachIndexed
                return qty
            }
        }
        return null
    }

    /**
     * Name adjacent to an honorific ("Ramesh bhaiya", "Mohan ji", "सुरेश भाई").
     * Returns null when no name can be inferred - the caller decides whether to
     * fall back to a contact name or ask the merchant.
     */
    private fun extractCustomer(originalText: String): String? {
        val words = originalText.split(Regex("\\s+"))
            .map { it.trim(',', '.', '!', '?', ':', ';') }
            .filter { it.isNotEmpty() }

        fun isName(candidate: String?): Boolean {
            if (candidate.isNullOrBlank()) return false
            if (candidate.lowercase(Locale.ROOT) in HONORIFICS) return false
            if (candidate.first().isDigit()) return false
            if (candidate.lowercase(Locale.ROOT) in DESCRIPTION_NOISE) return false
            return candidate.any { it.isLetter() }
        }

        words.forEachIndexed { i, word ->
            if (word.lowercase(Locale.ROOT) !in HONORIFICS) return@forEachIndexed
            words.getOrNull(i - 1)?.let { if (isName(it)) return capitalize(it) }
            words.getOrNull(i + 1)?.let { if (isName(it)) return capitalize(it) }
        }
        return null
    }

    private fun capitalize(word: String) = word.replaceFirstChar { it.uppercase() }

    private fun resolveDueDate(tokens: List<String>, lowerText: String): String? {
        // Most specific first: "parso" is a stronger signal than a bare "kal".
        val daysAhead = when {
            NEXT_WEEK_PHRASES.any { lowerText.contains(it) } -> 7
            tokens.any { it in DAY_AFTER } -> 2
            tokens.any { it in TOMORROW } -> 1
            tokens.any { it in TODAY } -> 0
            else -> return null
        }
        val cal = Calendar.getInstance().apply { add(Calendar.DAY_OF_YEAR, daysAhead) }
        return SimpleDateFormat("yyyy-MM-dd", Locale.US).format(cal.time)
    }

    private fun extractItemDescription(
        tokens: List<String>,
        customer: String?,
        attributeValues: Collection<String>,
    ): String {
        val customerToken = customer?.lowercase(Locale.ROOT)
        val attributeTokens = attributeValues.flatMap { it.split(" ") }.toSet()

        return tokens.filterNot { token ->
            token in DESCRIPTION_NOISE ||
                token in HONORIFICS ||
                token in CURRENCY ||
                token in TODAY || token in TOMORROW || token in DAY_AFTER ||
                token in PRIOR_ORDER_TOKENS ||
                token in MEASURE_PREFIX ||
                token in NUMBER_WORDS || token in FRACTION_WORDS ||
                token in attributeTokens ||
                token == customerToken ||
                toAsciiDigits(token) != null
        }.joinToString(" ").trim()
    }
}
