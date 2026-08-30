package com.devcraft.parser.offline

import com.devcraft.domain.model.ParsedItem
import com.devcraft.domain.model.ParsedMessage
import java.text.SimpleDateFormat
import java.util.*
import java.util.regex.Pattern

/**
 * Offline, rule-based multilingual order parser. No network, no cloud AI model,
 * 100% deterministic and private.
 *
 * Execution flow:
 *  1. Classification & Non-order protection (PDFs, OTPs, Bank alerts NEVER become orders)
 *  2. Explicit labels ("Location: Bhopal", "Amount: Rs 500")
 *  3. Unambiguous pattern extraction (dates, currency, phone, PIN, payment method)
 *  4. Heuristic name and item resolution (guarded against metadata and units)
 *  5. Objective per-order scoring (Classification, Field, Date, Clarification, Overall)
 */
object DeterministicParser {

    // ------------------------------------------------------------- vocabulary

    private val NUMBER_WORDS: Map<String, Int> = mapOf(
        "ek" to 1, "एक" to 1, "१" to 1,
        "do" to 2, "दो" to 2, "२" to 2,
        "teen" to 3, "tin" to 3, "तीन" to 3, "३" to 3,
        "chaar" to 4, "char" to 4, "चार" to 4, "४" to 4,
        "paanch" to 5, "panch" to 5, "पांच" to 5, "पाँच" to 5, "५" to 5,
        "chhah" to 6, "chah" to 6, "che" to 6, "छह" to 6, "६" to 6,
        "saat" to 7, "सात" to 7, "७" to 7,
        "aath" to 8, "आठ" to 8, "८" to 8,
        "nau" to 9, "नौ" to 9, "९" to 9,
        "das" to 10, "दस" to 10,
        "barah" to 12, "बारह" to 12,
        "bees" to 20, "बीस" to 20,
        "pachas" to 50, "पचास" to 50,
        "sau" to 100, "सौ" to 100,
        "hazaar" to 1000, "hazar" to 1000, "हजार" to 1000, "हज़ार" to 1000,
    )

    private val FRACTION_WORDS: Map<String, Int> = mapOf(
        "dedh" to 2, "डेढ़" to 2, "डेढ" to 2,
        "dhai" to 3, "ढाई" to 3,
        "adha" to 1, "aadha" to 1, "आधा" to 1,
    )

    private val HONORIFICS = setOf(
        "bhaiya", "bhai", "ji", "shri", "sahab", "saheb", "madam",
        "भाई", "भैया", "जी", "श्री", "साहब",
    )

    private val TODAY = setOf("aaj", "aj", "today", "आज")
    private val TOMORROW = setOf("kal", "kl", "tomorrow", "कल")
    private val DAY_AFTER = setOf("parso", "parson", "parsoon", "परसों", "परसो")
    private val NEXT_WEEK_PHRASES = listOf("next week", "agle hafte", "agle hafta", "अगले हफ्ते")

    private val WEEKDAYS: Map<String, Int> = mapOf(
        "monday" to Calendar.MONDAY, "somvar" to Calendar.MONDAY, "सोमवार" to Calendar.MONDAY,
        "tuesday" to Calendar.TUESDAY, "mangalvar" to Calendar.TUESDAY, "मंगलवार" to Calendar.TUESDAY,
        "wednesday" to Calendar.WEDNESDAY, "budhvar" to Calendar.WEDNESDAY, "बुधवार" to Calendar.WEDNESDAY,
        "thursday" to Calendar.THURSDAY, "guruvar" to Calendar.THURSDAY, "गुरुवार" to Calendar.THURSDAY,
        "friday" to Calendar.FRIDAY, "shukravar" to Calendar.FRIDAY, "शुक्रवार" to Calendar.FRIDAY,
        "saturday" to Calendar.SATURDAY, "shanivar" to Calendar.SATURDAY, "शनिवार" to Calendar.SATURDAY,
        "sunday" to Calendar.SUNDAY, "ravivar" to Calendar.SUNDAY, "रविवार" to Calendar.SUNDAY,
    )

    private val PRIOR_ORDER_TOKENS = setOf(
        "purana", "puraana", "wahi", "vahi", "repeat", "pichla", "pichhla",
        "पुराना", "वही", "पिछला",
    )
    private val PRIOR_ORDER_PHRASES = listOf("same as last", "same as before", "like last time")

    private val CURRENCY = setOf("rs", "rs.", "rupees", "rupee", "rupaye", "rupaiya", "inr", "₹", "रु", "रुपये")
    private val MEASURE_PREFIX = setOf("chest", "size", "saiz", "no", "number", "नंबर")

    private val IMPERATIVE_STEMS = setOf(
        "bhej", "bhejo", "kar", "karo", "de", "le", "daal", "dal", "likh",
        "भेज", "कर", "दे", "ले",
    )

    /** Words that end an item phrase. */
    private val ITEM_STOP = setOf(
        "location", "address", "addr", "delivery", "deliver", "date", "due",
        "amount", "amt", "total", "price", "rate", "payment", "pay", "mode",
        "phone", "mobile", "contact", "qty", "quantity", "name", "customer",
        "chahiye", "chaiye", "chahiya", "चाहिए", "bhejo", "bhej", "bhejna",
        "dena", "karo", "send", "please", "pls", "need", "want", "for", "to",
        "भेजो", "भेज", "देना", "करो", "और", "and", "tak", "tk", "ko", "को",
        "cod", "cash", "upi", "advance", "prepaid", "paid", "udhaar", "udhar",
        "on", "in", "at", "by", "deliver to", "near", "opposite", "shop", "flat"
    )

    /** Words that must NEVER be extracted as a customer name. */
    private val NOT_A_NAME = setOf(
        "kuch", "koi", "thoda", "zara", "jaldi", "urgent", "asap", "please", "pls",
        "samaan", "saman", "maal", "order", "item", "items", "goods", "product",
        "delivery", "location", "address", "amount", "total", "payment", "date",
        "cod", "cash", "upi", "advance", "credit", "udhaar", "parcel", "parcels",
        "packet", "bori", "piece", "pieces", "pcs", "box", "bag", "kg", "dozen",
        "pages", "page", "report", "test", "tech", "stack", "folders", "architecture",
        "document", "file", "attachment", "pdf", "apk", "doc", "zip", "parcal", "parcals",
        "min", "mins", "sec", "hrs", "version", "build", "need", "for", "to", "kurta", "cement",
        "chairs", "shirts", "notebook", "notebooks", "food",
        "कुछ", "सामान", "माल", "ऑर्डर", "पैकेट", "बोरी",
    )

    private val COLORS = listOf(
        "navy blue", "sky blue", "light blue", "dark blue",
        "blue", "red", "black", "white", "green", "yellow", "grey", "gray", "brown", "orange", "pink",
        "नीला", "लाल", "काला", "सफेद", "हरा", "पीला",
    )

    private val ADDRESS_TOKENS = setOf(
        "address", "pata", "पता", "location",
        "shop", "plot", "flat", "house", "makan", "दुकान", "मकान",
        "nagar", "colony", "sector", "road", "rd", "gali", "marg", "chowk",
        "bazar", "bazaar", "market", "mandi", "vihar", "puram", "ganj", "tola",
        "नगर", "कॉलोनी", "सेक्टर", "रोड", "गली", "मार्ग", "चौक", "बाजार", "मार्केट",
        "near", "opposite", "opp", "behind", "beside", "paas", "पास", "सामने",
        "landmark", "pin", "pincode",
    )

    // ------------------------------------------------------------- patterns

    private const val CURRENCY_ALT =
        "\\brs\\.?|\\brupees?|\\brupaye|\\brupaiya|\\binr|\\u20B9|\\u0930\\u0941"
    private const val NUM = "[0-9\\u0966-\\u096F]+(?:\\.[0-9]+)?"

    private val AMOUNT_PATTERN: Pattern = Pattern.compile(
        "(?:$CURRENCY_ALT)[ \\t]*($NUM)|($NUM)[ \\t]*(?:$CURRENCY_ALT)",
        Pattern.CASE_INSENSITIVE,
    )
    private val SIZE_PATTERN: Pattern = Pattern.compile("(?:chest|size|saiz)\\s*([0-9\\u0966-\\u096F]+)")
    private val PINCODE_PATTERN: Pattern = Pattern.compile("\\b([1-9][0-9]{5})\\b")
    private val PHONE_PATTERN: Pattern = Pattern.compile("(?:\\+?91[\\s-]?)?\\b([6-9][0-9]{9})\\b")

    private val TOKEN_SPLIT = Regex("[^\\p{L}\\p{M}\\p{N}.]+")
    private val CLAUSE_SPLIT = Regex("[,;|\\n\\r]+|\\s+-\\s+")

    // ------------------------------------------------------------- entry point

    fun parse(text: String): ParsedMessage = parse(text, System.currentTimeMillis())

    /** [nowMillis] is the reference timestamp for relative date resolution. */
    fun parse(text: String, nowMillis: Long): ParsedMessage {
        val trimmed = text.trim()
        if (trimmed.isEmpty()) {
            return ParsedMessage(
                confidence = 0.0f,
                needs_clarification = true,
                classification = MessageCategory.UNKNOWN,
                missing_fields = ALL_FIELDS,
            )
        }

        // STEP 1: Message Classification & Confidence Gate
        val classResult = MessageClassifier.classifyWithDetails(trimmed)
        val category = classResult.category
        val classificationScore = classResult.confidence

        // If message is NOT an order (e.g. Document, Bank, OTP, Delivery, System, Promo),
        // completely isolate it so document metadata and file names never become order fields.
        if (!category.isOrder) {
            return ParsedMessage(
                customer = null,
                items = emptyList(),
                due_date = null,
                raw_date_text = null,
                date_confidence = 0.0f,
                date_resolution_status = "NOT_FOUND",
                amount = null,
                references_prior_order = false,
                confidence = minOf(classificationScore * 0.35f, 0.40f),
                needs_clarification = true,
                delivery_address = null,
                pincode = null,
                phone = null,
                payment_method = null,
                classification = category,
                classification_score = classificationScore,
                field_extraction_score = 0.0f,
                date_resolution_score = 1.0f,
                clarification_decision_score = 1.0f,
                overall_score = 0.40f,
                missing_fields = ALL_FIELDS,
                review_notes = listOf("Classified as ${category.label}, not an order. Order creation disabled.")
            )
        }

        val lower = trimmed.lowercase(Locale.ROOT)
        val tokens = tokenize(lower)
        val labels = FieldExtractors.labelledFields(trimmed)
        val notes = mutableListOf<String>()

        // STEP 2: Phone number extraction
        val phone = labels["phone"]?.let { extractPhone(it) } ?: extractPhone(trimmed)

        // STEP 4: Payment method
        val payment = FieldExtractors.paymentMethod(lower)

        // STEP 3: Amount extraction (Label first, then currency pattern, then payment-adjacent number)
        val paymentAmountPattern = Pattern.compile(
            "\\b($NUM)\\s*(?:cod|cash|upi|advance|prepaid|paid)\\b|\\b(?:cod|cash|upi|advance|prepaid|paid)\\s*($NUM)\\b",
            Pattern.CASE_INSENSITIVE
        )
        val payMatcher = paymentAmountPattern.matcher(lower)
        val paymentAmountDigits = if (payMatcher.find()) (payMatcher.group(1) ?: payMatcher.group(2)) else null

        val labelledAmountDigits = labels["amount"]?.let { extractAmountDigits(it.lowercase(Locale.ROOT)) }
        val amountDigits = (labelledAmountDigits ?: extractAmountDigits(lower) ?: paymentAmountDigits)
            ?.takeIf { toAsciiDigits(it) != phone }


        // STEP 5: Date resolution (Label first, then absolute, then relative)
        val labelledDate = labels["date"]?.let { FieldExtractors.absoluteDate(it, nowMillis) }
        val absolute = labelledDate ?: FieldExtractors.absoluteDate(trimmed, nowMillis)
        absolute?.note?.let { notes += it }

        var rawDateText = absolute?.rawText ?: labels["date"]
        val dueDate = absolute?.iso ?: run {
            val rel = resolveRelativeDate(tokens, lower, nowMillis)
            if (rel != null) {
                rawDateText = findRelativeDateToken(tokens, lower)
            }
            rel
        }

        val dateResolutionStatus = when {
            dueDate != null && absolute?.ambiguous != true -> "RESOLVED"
            dueDate != null && absolute?.ambiguous == true -> "AMBIGUOUS"
            else -> "NOT_FOUND"
        }
        val dateConfidence = when (dateResolutionStatus) {
            "RESOLVED" -> 1.0f
            "AMBIGUOUS" -> 0.75f
            else -> 0.0f
        }

        // STEP 6: PIN Code extraction
        val pincode = extractPincode(trimmed, amountDigits, phone)

        // Attributes (Color, Size)
        val attributes = mutableMapOf<String, String>()
        COLORS.firstOrNull { lower.contains(it) }?.let { attributes["color"] = it }
        var sizeDigits: String? = null
        SIZE_PATTERN.matcher(lower).let { m ->
            if (m.find()) {
                sizeDigits = toAsciiDigits(m.group(1) ?: "")
                sizeDigits?.let { attributes["size"] = it }
            }
        }

        // Digits claimed by other fields so quantity never steals PIN, phone, dates, amount, or size
        val consumed = buildList {
            amountDigits?.let { toAsciiDigits(it)?.let(::add) }
            phone?.let { add(it.filter { c -> c.isDigit() }) }
            pincode?.let { add(it) }
            sizeDigits?.let { add(it) }
            // Only consume digits that actually belonged to a matched explicit date
            val explicitDateText = absolute?.rawText ?: labels["date"]
            if (explicitDateText != null) {
                dateDigitRuns(explicitDateText).forEach { add(it) }
            }
        }

        // STEP 7: Quantity extraction
        val quantity = labels["quantity"]?.let { qtyFromText(it) }
            ?: extractQuantity(tokens, consumed)

        // STEP 8: Amount fallback
        val amountFromPattern = amountDigits?.let { toAsciiDigits(it)?.toDoubleOrNull() }
        val amount = amountFromPattern ?: fallbackAmount(
            tokens = tokens,
            consumed = consumed,
            quantity = quantity,
            hasMoneyEvidence = payment != null || labels.containsKey("amount"),
        )
        if (amountFromPattern == null && amount != null) {
            notes += "Amount ₹${amount.toInt()} was inferred without a currency symbol."
        }

        // STEP 9: Location extraction
        val location = labels["location"]?.trim()
            ?: extractAddressClause(trimmed, pincode)
            ?: FieldExtractors.cityMention(tokens)
            ?: fuzzyCity(tokens)?.also {
                notes += "Location \"$it\" was matched from a probable misspelling."
            }

        // STEP 10: Item extraction
        val rawItem = labels["item"]?.trim()?.takeIf { it.isNotBlank() }
            ?: itemAfterQuantity(trimmed, quantity)
            ?: fallbackItemFromTokens(tokens, consumed)

        val item = rawItem?.takeIf { it.isNotBlank() }

        // STEP 11: Customer name extraction (Strictly guarded against files and units)
        val customer = labels["customer"]?.trim()?.takeIf { it.isNotBlank() }
            ?: patternCustomerName(trimmed)
            ?: honorificName(trimmed)
            ?: bareName(trimmed, hasOrderEvidence = quantity != null || amount != null || item != null)

        val referencesPrior = tokens.any { it in PRIOR_ORDER_TOKENS } ||
            PRIOR_ORDER_PHRASES.any { lower.contains(it) }

        // STEP 12: Calculate Field Extraction Scores & Missing Fields
        val missing = mutableListOf<String>()
        var fieldScore = 0.0f
        var totalFieldWeight = 0.0f

        totalFieldWeight += 0.20f
        if (customer != null) fieldScore += 0.20f else missing += "Customer name"

        totalFieldWeight += 0.25f
        if (item != null) fieldScore += 0.25f else missing += "Item"

        totalFieldWeight += 0.20f
        if (quantity != null) fieldScore += 0.20f else missing += "Quantity"

        totalFieldWeight += 0.15f
        if (dueDate != null) fieldScore += 0.15f else missing += "Delivery date"

        totalFieldWeight += 0.10f
        if (amount != null) fieldScore += 0.10f else missing += "Amount"

        totalFieldWeight += 0.05f
        if (location != null) fieldScore += 0.05f else missing += "Location"

        totalFieldWeight += 0.05f
        if (payment != null) fieldScore += 0.05f else missing += "Payment method"

        val fieldExtractionScore = (fieldScore / totalFieldWeight).coerceIn(0.0f, 1.0f)
        val dateResolutionScore = if (dueDate != null) dateConfidence else if (labels.containsKey("date")) 0.0f else 1.0f

        // Overall Confidence calculation
        var confidence = fieldExtractionScore.coerceAtMost(0.97f)
        if (absolute?.ambiguous == true) confidence -= 0.10f
        confidence = confidence.coerceIn(0f, 0.97f)

        val needsReview = confidence < 0.70f || absolute?.ambiguous == true || (item == null && quantity == null)
        val clarificationDecisionScore = 1.0f

        val overallScore = (0.60f * fieldExtractionScore + 0.20f * dateResolutionScore + 0.20f * clarificationDecisionScore).coerceIn(0.0f, 1.0f)

        val itemsList = if (item != null || quantity != null) {
            listOf(
                ParsedItem(
                    description = item ?: "Unspecified item",
                    quantity = quantity ?: 1,
                    attributes = attributes,
                )
            )
        } else {
            emptyList()
        }

        return ParsedMessage(
            customer = customer,
            items = itemsList,
            due_date = dueDate,
            raw_date_text = rawDateText,
            date_confidence = dateConfidence,
            date_resolution_status = dateResolutionStatus,
            amount = amount,
            references_prior_order = referencesPrior,
            confidence = confidence,
            needs_clarification = needsReview,
            delivery_address = location,
            pincode = pincode,
            phone = phone,
            payment_method = payment,
            classification = category,
            classification_score = classificationScore,
            field_extraction_score = fieldExtractionScore,
            date_resolution_score = dateResolutionScore,
            clarification_decision_score = clarificationDecisionScore,
            overall_score = overallScore,
            missing_fields = missing,
            review_notes = notes,
        )
    }

    fun displayDate(iso: String?): String? = FieldExtractors.displayDate(iso)

    private val ALL_FIELDS = listOf(
        "Customer name", "Quantity", "Item", "Delivery date",
        "Amount", "Location", "Payment method",
    )

    // ------------------------------------------------------------- helpers

    private fun tokenize(lowerText: String): List<String> =
        lowerText.split(TOKEN_SPLIT).map { it.trim('.') }.filter { it.isNotEmpty() }

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

    private fun extractAmountDigits(text: String): String? {
        val m = AMOUNT_PATTERN.matcher(text)
        if (!m.find()) return null
        return m.group(1) ?: m.group(2)
    }

    private fun dateDigitRuns(text: String): List<String> {
        val runs = mutableListOf<String>()
        val m = Pattern.compile("\\b(\\d{1,4})\\b").matcher(text)
        while (m.find()) {
            val d = m.group(1) ?: continue
            val n = d.toIntOrNull() ?: continue
            if (n in 1..31 || n in 1900..2100) runs += d
        }
        return runs
    }

    private fun extractPhone(text: String): String? {
        val m = PHONE_PATTERN.matcher(text)
        return if (m.find()) m.group(1) else null
    }

    private fun extractPincode(text: String, amountDigits: String?, phone: String?): String? {
        val m = PINCODE_PATTERN.matcher(text)
        while (m.find()) {
            val pin = m.group(1) ?: continue
            if (pin == amountDigits || (phone != null && phone.contains(pin))) continue
            return pin
        }
        return null
    }

    private fun qtyFromText(text: String): Int? {
        toAsciiDigits(text.trim())?.toIntOrNull()?.let { return it }
        val t = text.lowercase(Locale.ROOT).trim()
        return NUMBER_WORDS[t] ?: FRACTION_WORDS[t]
    }

    private fun extractQuantity(tokens: List<String>, consumed: List<String>): Int? {
        // 1. Check compound Hindi numbers: "do sau" (2 * 100), "teen sau" (3 * 100), "paanch sau" (500)
        for (i in 0 until tokens.size - 1) {
            val w1 = tokens[i]
            val w2 = tokens[i + 1]
            val v1 = NUMBER_WORDS[w1]
            if (v1 != null && (w2 == "sau" || w2 == "सौ" || w2 == "hazar" || w2 == "hazaar" || w2 == "हजार")) {
                val mult = if (w2 == "sau" || w2 == "सौ") 100 else 1000
                return v1 * mult
            }
        }

        // 2. Check tokens
        for (token in tokens) {
            val ascii = toAsciiDigits(token)
            if (ascii != null && ascii !in consumed) {
                val n = ascii.toIntOrNull()
                // A quantity is normally 1-5000. Reject years (1900..2100).
                if (n != null && n in 1..5000 && n !in 1900..2100) {
                    return n
                }
            }
            val wordVal = NUMBER_WORDS[token]
            if (wordVal != null && token !in consumed && wordVal in 1..5000) return wordVal
        }
        return null
    }

    private fun fallbackAmount(
        tokens: List<String>,
        consumed: List<String>,
        quantity: Int?,
        hasMoneyEvidence: Boolean
    ): Double? {
        if (!hasMoneyEvidence) return null
        for (t in tokens) {
            val digits = toAsciiDigits(t) ?: continue
            if (digits in consumed) continue
            val v = digits.toDoubleOrNull() ?: continue
            if (quantity != null && v == quantity.toDouble()) continue
            if (v >= 10.0) return v
        }
        return null
    }

    private fun itemAfterQuantity(originalText: String, quantity: Int?): String? {
        if (quantity == null) return null
        for (line in originalText.split(Regex("[\\n\\r]+"))) {
            val words = line.trim().split(Regex("\\s+")).filter { it.isNotBlank() }
            val anchor = words.indexOfFirst { w ->
                val lw = w.lowercase(Locale.ROOT).trim('.', ':', ',')
                val d = toAsciiDigits(lw)?.toIntOrNull()
                d == quantity || NUMBER_WORDS[lw] == quantity
            }
            if (anchor == -1) continue

            val phrase = mutableListOf<String>()
            for (j in (anchor + 1) until words.size) {
                val w = words[j]
                val lw = w.lowercase(Locale.ROOT).trim('.', ':', ',')
                if (lw.isEmpty()) continue
                if (toAsciiDigits(lw) != null) break
                if (lw in ITEM_STOP || lw in CURRENCY || lw in HONORIFICS) break
                if (lw in ADDRESS_TOKENS) break
                phrase += w.trim('.', ':', ',')
                if (phrase.size >= 4) break
            }
            if (phrase.isNotEmpty()) return phrase.joinToString(" ").lowercase(Locale.ROOT)
        }
        return null
    }

    private fun fallbackItemFromTokens(tokens: List<String>, consumed: List<String>): String? {
        val candidates = tokens.filter { t ->
            t !in consumed && t !in ITEM_STOP && t !in CURRENCY && t !in HONORIFICS &&
                t !in ADDRESS_TOKENS && t !in TODAY && t !in TOMORROW &&
                t !in DAY_AFTER && t !in WEEKDAYS && t.length >= 3 && toAsciiDigits(t) == null
        }
        return candidates.take(3).joinToString(" ").takeIf { it.isNotBlank() }
    }

    /** Contextual customer names: "for Rahul", "to Rahul", "Rahul ko". */
    private fun patternCustomerName(originalText: String): String? {
        val forToRegex = Regex("(?i)\\b(?:for|to)\\s+([\\p{L}]{3,15})\\b")
        forToRegex.find(originalText)?.let { m ->
            val name = m.groupValues[1]
            if (isNameLike(name)) return capitalize(name)
        }
        val koRegex = Regex("(?i)\\b([\\p{L}]{3,15})\\s+ko\\b")
        koRegex.find(originalText)?.let { m ->
            val name = m.groupValues[1]
            if (isNameLike(name)) return capitalize(name)
        }
        return null
    }

    /** Name adjacent to an honorific: "Ramesh bhaiya", "सुरेश भाई", "Mohan ji". */
    private fun honorificName(originalText: String): String? {
        val words = originalText.split(Regex("[\\s,\\n\\r]+"))
            .map { it.trim(',', '.', '!', '?', ':', ';') }
            .filter { it.isNotEmpty() }

        fun isName(candidate: String?): Boolean {
            if (candidate.isNullOrBlank()) return false
            val lc = candidate.lowercase(Locale.ROOT)
            if (lc in HONORIFICS || lc in NOT_A_NAME || lc in ITEM_STOP) return false
            if (candidate.first().isDigit()) return false
            return candidate.any { it.isLetter() }
        }

        words.forEachIndexed { i, word ->
            if (word.lowercase(Locale.ROOT) !in HONORIFICS) return@forEachIndexed
            words.getOrNull(i - 1)?.let { if (isName(it)) return capitalize(it) }
            words.getOrNull(i + 1)?.let { if (isName(it)) return capitalize(it) }
        }
        return null
    }

    /**
     * Clean name extraction at the start of a message or standalone name line.
     */
    private fun bareName(originalText: String, hasOrderEvidence: Boolean): String? {
        if (!hasOrderEvidence) return null

        for (line in originalText.split(Regex("[\\n\\r]+"))) {
            val words = line.trim().split(Regex("\\s+")).filter { it.isNotBlank() }
            if (words.isEmpty() || words.size > 3) continue
            if (words.any { w -> w.any { it.isDigit() } || w.contains("(") || w.contains(")") }) continue
            if (words.any { !isNameLike(it) }) continue
            return capitalize(words.first())
        }

        val words = originalText.split(Regex("[\\s,]+")).filter { it.isNotBlank() }
        return words.firstOrNull { isNameLike(it) }?.let { capitalize(it) }
    }

    private fun isNameLike(word: String): Boolean {
        val lc = word.lowercase(Locale.ROOT).trim('.', ':', ',', '(', ')', '_')
        if (lc.length < 3) return false
        if (lc.any { it.isDigit() }) return false
        if (word.contains("(") || word.contains(")") || word.contains("_") || word.contains(".")) return false
        if (lc in NOT_A_NAME || lc in ITEM_STOP || lc in HONORIFICS) return false
        if (lc in CURRENCY || lc in ADDRESS_TOKENS || lc in MEASURE_PREFIX) return false
        if (lc in NUMBER_WORDS || lc in FRACTION_WORDS || lc in PRIOR_ORDER_TOKENS) return false
        if (lc in TODAY || lc in TOMORROW || lc in DAY_AFTER || lc in WEEKDAYS) return false
        if (lc in FieldExtractors.KNOWN_CITIES) return false
        if (COLORS.any { it == lc }) return false
        return word.any { it.isLetter() }
    }

    private fun capitalize(word: String) = word.replaceFirstChar { it.uppercase() }

    private fun findRelativeDateToken(tokens: List<String>, lowerText: String): String? {
        if (NEXT_WEEK_PHRASES.any { lowerText.contains(it) }) return "next week"
        return tokens.firstOrNull { it in TODAY || it in TOMORROW || it in DAY_AFTER || it in WEEKDAYS }
    }

    private fun resolveRelativeDate(tokens: List<String>, lowerText: String, nowMillis: Long): String? {
        WEEKDAYS.entries.firstOrNull { (name, _) -> tokens.contains(name) }?.let { (_, dow) ->
            return nextWeekday(dow, nowMillis)
        }
        val daysAhead = when {
            NEXT_WEEK_PHRASES.any { lowerText.contains(it) } -> 7
            tokens.any { it in DAY_AFTER } -> 2
            tokens.any { it in TOMORROW } -> 1
            tokens.any { it in TODAY } -> 0
            else -> return null
        }
        val cal = Calendar.getInstance().apply {
            timeInMillis = nowMillis
            add(Calendar.DAY_OF_YEAR, daysAhead)
        }
        return SimpleDateFormat("yyyy-MM-dd", Locale.US).format(cal.time)
    }

    private fun nextWeekday(targetDow: Int, nowMillis: Long): String {
        val cal = Calendar.getInstance().apply { timeInMillis = nowMillis }
        do {
            cal.add(Calendar.DAY_OF_YEAR, 1)
        } while (cal.get(Calendar.DAY_OF_WEEK) != targetDow)
        return SimpleDateFormat("yyyy-MM-dd", Locale.US).format(cal.time)
    }

    private fun extractAddressClause(text: String, pincode: String?): String? {
        val clauses = text.split(CLAUSE_SPLIT).map { it.trim() }.filter { it.isNotEmpty() }
        val hits = clauses.filter { clause ->
            val t = tokenize(clause.lowercase(Locale.ROOT))
            t.any { it in ADDRESS_TOKENS } || (pincode != null && clause.contains(pincode))
        }
        if (hits.isEmpty()) return null
        return hits.joinToString(", ").replace(Regex("\\s+"), " ").trim().takeIf { it.isNotBlank() }
    }

    private fun fuzzyCity(tokens: List<String>): String? {
        for (t in tokens) {
            if (t.length < 5) continue
            val hit = FieldExtractors.KNOWN_CITIES.firstOrNull { editDistanceAtMostOne(t, it) }
            if (hit != null) return hit.replaceFirstChar { it.uppercase() }
        }
        return null
    }

    private fun editDistanceAtMostOne(a: String, b: String): Boolean {
        if (a == b) return true
        if (kotlin.math.abs(a.length - b.length) > 1) return false
        var i = 0
        var j = 0
        var edits = 0
        while (i < a.length && j < b.length) {
            if (a[i] == b[j]) { i++; j++; continue }
            if (++edits > 1) return false
            when {
                a.length > b.length -> i++
                a.length < b.length -> j++
                else -> { i++; j++ }
            }
        }
        if (i < a.length || j < b.length) edits++
        return edits <= 1
    }
}
