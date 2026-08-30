package com.devcraft.parser.offline

import com.devcraft.domain.model.ParsedItem
import com.devcraft.domain.model.ParsedMessage
import java.text.SimpleDateFormat
import java.util.*
import java.util.regex.Pattern

/**
 * Offline, rule-based multilingual order parser. No network, no model, fully
 * deterministic. The same message always parses the same way.
 *
 * Extraction runs in strict order of trust:
 *  1. explicit labels - "Location: Bhopal", "Amount: Rs 500"
 *  2. unambiguous patterns - dates, currency, phone, PIN, payment terms
 *  3. positional and vocabulary heuristics, only for fields still unresolved
 *
 * Field order in the message is irrelevant. Nothing is invented: an unresolved
 * field stays null and is listed in [ParsedMessage.missing_fields].
 */
object DeterministicParser {

    // ------------------------------------------------------------- vocabulary

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
    private val AMBIGUOUS_NUMERALS = setOf("do", "दो", "le", "lo")

    /** Words that end an item phrase. */
    private val ITEM_STOP = setOf(
        "location", "address", "addr", "delivery", "deliver", "date", "due",
        "amount", "amt", "total", "price", "rate", "payment", "pay", "mode",
        "phone", "mobile", "contact", "qty", "quantity", "name", "customer",
        "chahiye", "chaiye", "chahiya", "चाहिए", "bhejo", "bhej", "bhejna",
        "dena", "karo", "send", "please", "pls", "need", "want", "for",
        "भेजो", "भेज", "देना", "करो", "और", "and", "tak", "tk", "ko", "को",
        "cod", "cash", "upi", "advance", "prepaid", "paid", "udhaar", "udhar",
    )

    /** Never a customer name. */
    private val NOT_A_NAME = setOf(
        "kuch", "koi", "thoda", "zara", "jaldi", "urgent", "asap", "please", "pls",
        "samaan", "saman", "maal", "order", "item", "items", "goods", "product",
        "delivery", "location", "address", "amount", "total", "payment", "date",
        "cod", "cash", "upi", "advance", "credit", "udhaar", "parcel", "parcels",
        "packet", "bori", "piece", "pieces", "pcs", "box", "bag", "kg", "dozen",
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

    // [ \t]* not \s*: the currency word must be on the SAME line as the number.
    // With \s* a trailing "2026\nrs 500" matched "2026 rs" and read the year as
    // the amount.
    private val AMOUNT_PATTERN: Pattern = Pattern.compile(
        "(?:$CURRENCY_ALT)[ \\t]*($NUM)|($NUM)[ \\t]*(?:$CURRENCY_ALT)",
        Pattern.CASE_INSENSITIVE,
    )
    private val SIZE_PATTERN: Pattern = Pattern.compile("(?:chest|size|saiz)\\s*([0-9\\u0966-\\u096F]+)")
    private val PINCODE_PATTERN: Pattern = Pattern.compile("\\b([1-9][0-9]{5})\\b")
    private val PHONE_PATTERN: Pattern = Pattern.compile("(?:\\+?91[\\s-]?)?\\b([6-9][0-9]{9})\\b")

    // \p{M} is essential: Devanagari matras and anusvara are combining marks, so
    // without it "परसों" shreds to "परस" and "बोरी" to "बोर".
    private val TOKEN_SPLIT = Regex("[^\\p{L}\\p{M}\\p{N}.]+")
    private val CLAUSE_SPLIT = Regex("[,;|\\n\\r]+|\\s+-\\s+")

    // ------------------------------------------------------------- entry point

    fun parse(text: String): ParsedMessage = parse(text, System.currentTimeMillis())

    /** [nowMillis] is the date anchor, injected so this is unit-testable. */
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

        val category = MessageClassifier.classify(trimmed)
        val lower = trimmed.lowercase(Locale.ROOT)
        val tokens = tokenize(lower)
        val labels = FieldExtractors.labelledFields(trimmed)
        val notes = mutableListOf<String>()

        // ---- phone first: a 10-digit mobile must never be read as money ---
        val phone = labels["phone"]?.let { extractPhone(it) } ?: extractPhone(trimmed)

        // ---- amount: label first, then currency pattern -------------------
        val labelledAmountDigits = labels["amount"]?.let { extractAmountDigits(it.lowercase(Locale.ROOT)) }
        val amountDigits = (labelledAmountDigits ?: extractAmountDigits(lower))
            ?.takeIf { toAsciiDigits(it) != phone }

        // ---- payment ------------------------------------------------------
        val payment = FieldExtractors.paymentMethod(lower)

        // ---- date: label first, then absolute, then relative --------------
        val labelledDate = labels["date"]?.let { FieldExtractors.absoluteDate(it, nowMillis) }
        val absolute = labelledDate ?: FieldExtractors.absoluteDate(trimmed, nowMillis)
        absolute?.note?.let { notes += it }
        val dueDate = absolute?.iso ?: resolveRelativeDate(tokens, lower, nowMillis)

        // ---- pincode ------------------------------------------------------
        val pincode = extractPincode(trimmed, amountDigits, phone)

        // Every digit run already claimed by another field, so quantity can
        // never steal a PIN code, a phone number, a date part or the amount.
        val consumed = buildList {
            amountDigits?.let { toAsciiDigits(it)?.let(::add) }
            phone?.let { add(it.filter { c -> c.isDigit() }) }
            pincode?.let { add(it) }
            absolute?.iso?.let { iso -> iso.split("-").forEach { add(it.trimStart('0').ifEmpty { "0" }); add(it) } }
            // Raw date digits as written, e.g. "30", "08", "2026"
            dateDigitRuns(labels["date"] ?: trimmed).forEach { add(it) }
        }

        // ---- quantity -----------------------------------------------------
        val quantity = labels["quantity"]?.let { qtyFromText(it) }
            ?: extractQuantity(tokens, consumed)

        // ---- amount fallback ---------------------------------------------
        // Only when there is real evidence of money: a payment method or an
        // amount label. Otherwise "2 cars 500" would invent a price.
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

        // ---- location -----------------------------------------------------
        val location = labels["location"]?.trim()
            ?: extractAddressClause(trimmed, pincode)
            ?: FieldExtractors.cityMention(tokens)
            ?: fuzzyCity(tokens)?.also {
                notes += "Location \"$it\" was matched from a probable misspelling."
            }

        // ---- attributes ---------------------------------------------------
        val attributes = mutableMapOf<String, String>()
        COLORS.firstOrNull { lower.contains(it) }?.let { attributes["color"] = it }
        SIZE_PATTERN.matcher(lower).let { m ->
            if (m.find()) toAsciiDigits(m.group(1) ?: "")?.let { attributes["size"] = it }
        }

        // ---- item: label, else the noun phrase after the quantity ---------
        val item = labels["item"]?.trim()?.takeIf { it.isNotBlank() }
            ?: itemAfterQuantity(trimmed, quantity)

        val customer = labels["customer"]?.trim()?.takeIf { it.isNotBlank() }
            ?: honorificName(trimmed)
            ?: bareName(trimmed, hasOrderEvidence = quantity != null || amount != null)

        val referencesPrior = tokens.any { it in PRIOR_ORDER_TOKENS } ||
            PRIOR_ORDER_PHRASES.any { lower.contains(it) }

        // ---- confidence from actual extraction ----------------------------
        val missing = mutableListOf<String>()
        var confidence = 0.0f
        if (customer != null) confidence += 0.18f else missing += "Customer name"
        if (quantity != null) confidence += 0.22f else missing += "Quantity"
        if (!item.isNullOrBlank()) confidence += 0.22f else missing += "Item"
        if (dueDate != null) confidence += 0.12f else missing += "Delivery date"
        if (amount != null) confidence += 0.12f else missing += "Amount"
        if (location != null) confidence += 0.08f else missing += "Location"
        if (payment != null) confidence += 0.06f else missing += "Payment method"

        // Never claim certainty, and never let a non-order look confident.
        confidence = confidence.coerceAtMost(0.97f)
        if (!category.isOrder) {
            confidence = minOf(confidence, 0.4f)
            notes += "Classified as ${category.label}, not an order."
        }
        if (absolute?.ambiguous == true) confidence -= 0.10f
        confidence = confidence.coerceIn(0f, 0.97f)

        val needsReview = confidence < 0.7f || absolute?.ambiguous == true || !category.isOrder

        return ParsedMessage(
            customer = customer,
            items = listOf(
                ParsedItem(
                    description = item?.takeIf { it.isNotBlank() } ?: "Unspecified item",
                    quantity = quantity ?: 1,
                    attributes = attributes,
                )
            ),
            due_date = dueDate,
            amount = amount,
            references_prior_order = referencesPrior,
            confidence = confidence,
            needs_clarification = needsReview,
            delivery_address = location,
            pincode = pincode,
            phone = phone,
            payment_method = payment,
            classification = category,
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

    /**
     * Month names, spelled out. The word after a number must be one of THESE to
     * count as a date - an earlier version accepted any 3-9 letter word, so
     * "10 bori" and "2 kurta" were read as dates and their digits were struck
     * from quantity extraction.
     */
    private const val MONTH_ALT =
        "jan(?:uary)?|feb(?:ruary)?|mar(?:ch)?|apr(?:il)?|may|jun(?:e)?|jul(?:y)?" +
            "|aug(?:ust)?|sep(?:t)?(?:ember)?|oct(?:ober)?|nov(?:ember)?|dec(?:ember)?"

    private val TEXT_DATE_DIGITS = Regex(
        "\\b(\\d{1,2})\\s*(?:st|nd|rd|th)?[\\s.,-]+(?:$MONTH_ALT)\\b[\\s.,-]*(\\d{4}|\\d{2})?",
        RegexOption.IGNORE_CASE,
    )
    private val MONTH_FIRST_DIGITS = Regex(
        "\\b(?:$MONTH_ALT)\\b[\\s.,-]+(\\d{1,2})(?:st|nd|rd|th)?[\\s.,-]*(\\d{4}|\\d{2})?",
        RegexOption.IGNORE_CASE,
    )
    private val NUMERIC_DATE_DIGITS = Regex("\\b(\\d{1,4})[/.-](\\d{1,2})[/.-](\\d{2,4})\\b")

    /** Digit runs appearing inside anything date-shaped, so quantity skips them. */
    private fun dateDigitRuns(text: String): List<String> {
        val out = mutableListOf<String>()
        for (regex in listOf(NUMERIC_DATE_DIGITS, TEXT_DATE_DIGITS, MONTH_FIRST_DIGITS)) {
            regex.findAll(text).forEach { m ->
                m.groupValues.drop(1).filter { it.isNotBlank() }.forEach { out += it }
            }
        }
        return out
    }

    private fun extractPhone(text: String): String? {
        val m = PHONE_PATTERN.matcher(text)
        if (m.find()) return m.group(1)
        return null
    }

    private fun extractPincode(text: String, amountDigits: String?, phone: String?): String? {
        val m = PINCODE_PATTERN.matcher(text)
        while (m.find()) {
            val candidate = m.group(1) ?: continue
            if (candidate == amountDigits) continue
            if (phone != null && phone.contains(candidate)) continue
            return candidate
        }
        return null
    }

    private fun qtyFromText(value: String): Int? {
        val t = tokenize(value.lowercase(Locale.ROOT))
        return extractQuantity(t, emptyList())
    }

    /**
     * Two passes. A number directly followed by a goods word ("2 food parcels",
     * "10 bori") is a far stronger quantity signal than a bare number, so it is
     * preferred. Without this, "500 COD / ... / 2 food parcels" read 500 as the
     * quantity purely because it appeared first.
     */
    private fun extractQuantity(tokens: List<String>, consumedDigits: List<String>): Int? =
        scanQuantity(tokens, consumedDigits, requireFollowingNoun = true)
            ?: scanQuantity(tokens, consumedDigits, requireFollowingNoun = false)

    private fun scanQuantity(
        tokens: List<String>,
        consumedDigits: List<String>,
        requireFollowingNoun: Boolean,
    ): Int? {
        tokens.forEachIndexed { i, token ->
            val prev = tokens.getOrNull(i - 1) ?: ""
            val next = tokens.getOrNull(i + 1) ?: ""
            val nextIsNoun = next.isNotEmpty() &&
                toAsciiDigits(next) == null &&
                next !in ITEM_STOP &&
                next !in CURRENCY &&
                next !in ADDRESS_TOKENS &&
                next !in HONORIFICS &&
                next.none { it.isDigit() }

            val digits = toAsciiDigits(token)
            if (digits != null) {
                val isClaimed = digits in consumedDigits || prev in CURRENCY || next in CURRENCY
                val isMeasure = prev in MEASURE_PREFIX
                if (!isClaimed && !isMeasure && (!requireFollowingNoun || nextIsNoun)) {
                    digits.toIntOrNull()?.let { if (it > 0) return it }
                }
                return@forEachIndexed
            }

            FRACTION_WORDS[token]?.let { if (!requireFollowingNoun || nextIsNoun) return it }
            NUMBER_WORDS[token]?.let { qty ->
                if (token in AMBIGUOUS_NUMERALS && prev in IMPERATIVE_STEMS) return@forEachIndexed
                val multiplier = NUMBER_WORDS[next]
                if (multiplier != null && multiplier >= 100) return qty * multiplier
                if (qty >= 100) return@forEachIndexed
                if (!requireFollowingNoun || nextIsNoun) return qty
            }
        }
        return null
    }

    /**
     * Largest unclaimed number, but only when the message shows money evidence
     * (a payment method or an Amount label). Without that guard, "2 cars 500"
     * would invent a price.
     */
    private fun fallbackAmount(
        tokens: List<String>,
        consumed: List<String>,
        quantity: Int?,
        hasMoneyEvidence: Boolean,
    ): Double? {
        if (!hasMoneyEvidence) return null
        return tokens.mapNotNull { toAsciiDigits(it) }
            .filter { it !in consumed && it.none { c -> c == '.' } }
            .mapNotNull { it.toIntOrNull() }
            .filter { it >= 10 && it != quantity }
            .maxOrNull()
            ?.toDouble()
    }

    /**
     * The noun phrase immediately after the quantity, stopping at a label word,
     * another number, or a clause break. This replaces the old "everything left
     * over is the item" rule, which produced strings like
     * "2x nakul food parcels location bhopal delivery august amount cod".
     */
    private fun itemAfterQuantity(rawText: String, quantity: Int?): String? {
        for (line in rawText.split(CLAUSE_SPLIT)) {
            val words = line.trim().split(Regex("\\s+")).filter { it.isNotBlank() }
            if (words.isEmpty()) continue

            val anchor = words.indexOfFirst { w ->
                val lw = w.lowercase(Locale.ROOT).trim('.', ':')
                val d = toAsciiDigits(lw)?.toIntOrNull()
                (quantity != null && d == quantity) || NUMBER_WORDS[lw] == quantity
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
     * A standalone word that is not any other known thing. Only used when the
     * message already looks like an order, so a vague text does not acquire a
     * fabricated customer.
     */
    private fun bareName(originalText: String, hasOrderEvidence: Boolean): String? {
        if (!hasOrderEvidence) return null

        for (line in originalText.split(Regex("[\\n\\r]+"))) {
            val words = line.trim().split(Regex("\\s+")).filter { it.isNotBlank() }
            // A line that is just a name, 1-3 words, no digits, no keywords.
            if (words.isEmpty() || words.size > 3) continue
            if (words.any { w -> w.any { it.isDigit() } }) continue
            if (words.any { !isNameLike(it) }) continue
            return capitalize(words.first())
        }

        // Single-line messy message: first name-like token.
        val words = originalText.split(Regex("[\\s,]+")).filter { it.isNotBlank() }
        return words.firstOrNull { isNameLike(it) }?.let { capitalize(it) }
    }

    private fun isNameLike(word: String): Boolean {
        val lc = word.lowercase(Locale.ROOT).trim('.', ':', ',')
        if (lc.length < 3) return false
        if (lc.any { it.isDigit() }) return false
        if (lc in NOT_A_NAME || lc in ITEM_STOP || lc in HONORIFICS) return false
        if (lc in CURRENCY || lc in ADDRESS_TOKENS || lc in MEASURE_PREFIX) return false
        if (lc in NUMBER_WORDS || lc in FRACTION_WORDS || lc in PRIOR_ORDER_TOKENS) return false
        if (lc in TODAY || lc in TOMORROW || lc in DAY_AFTER || lc in WEEKDAYS) return false
        if (lc in FieldExtractors.KNOWN_CITIES) return false
        if (COLORS.any { it == lc }) return false
        return word.any { it.isLetter() }
    }

    private fun capitalize(word: String) = word.replaceFirstChar { it.uppercase() }

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

    /** Strictly the next occurrence, never today. */
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

    /** Edit-distance-1 city match, for obvious typos like "Bhopl". */
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
