package com.devcraft.parser.offline

import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale
import java.util.regex.Pattern

/**
 * Field-level extraction helpers, split out of DeterministicParser so each rule
 * is testable and readable on its own.
 *
 * Strategy, in strict order of trust:
 *  1. explicit labels ("Location: Bhopal") - highest confidence
 *  2. unambiguous patterns (dates, currency, phone, PIN, payment terms)
 *  3. positional and vocabulary heuristics - lowest confidence, and only for
 *     fields still unresolved
 *
 * Nothing here touches the network.
 */
internal object FieldExtractors {

    // ------------------------------------------------------------------ labels

    /** Canonical field -> label aliases a merchant actually types. */
    private val LABELS: Map<String, List<String>> = mapOf(
        "customer" to listOf("name", "customer", "customer name", "cust", "party", "naam", "नाम", "ग्राहक", "for", "to"),
        "phone" to listOf("phone", "mobile", "contact", "mob", "no", "number", "फोन", "मोबाइल"),
        "item" to listOf("item", "items", "product", "goods", "maal", "saman", "samaan", "सामान", "माल"),
        "quantity" to listOf("qty", "quantity", "nos", "count", "मात्रा"),
        "location" to listOf("location", "address", "addr", "place", "city", "deliver to", "delivery at", "pata", "पता", "जगह"),
        "date" to listOf("delivery", "delivery date", "date", "due", "due date", "deliver by", "by", "tarikh", "तारीख", "दिनांक"),
        "amount" to listOf("amount", "amt", "total", "price", "rate", "value", "rs", "cost", "राशि", "कीमत"),
        "payment" to listOf("payment", "payment method", "pay", "mode", "payment mode", "भुगतान"),
    )

    private val LABEL_LINE = Pattern.compile("^\\s*([\\p{L} ]{2,20})\\s*:\\s*(.+)$")
    private val INLINE_LABEL = Pattern.compile(
        "\\b(Location|Address|Delivery|Date|Amount|Customer|Name|Qty|Quantity|Item|Payment)\\s*:\\s*([^\\n\\r,;]+)",
        Pattern.CASE_INSENSITIVE
    )

    /**
     * Parses "Label: value" lines and inline label tokens.
     */
    fun labelledFields(rawText: String): Map<String, String> {
        val found = mutableMapOf<String, String>()

        // 1. Line-by-line matching
        for (line in rawText.split(Regex("[\\n\\r]+"))) {
            val m = LABEL_LINE.matcher(line.trim())
            if (m.matches()) {
                val label = m.group(1)?.trim()?.lowercase(Locale.ROOT) ?: continue
                val value = m.group(2)?.trim() ?: continue
                if (value.isNotEmpty()) {
                    val field = LABELS.entries.firstOrNull { (_, aliases) -> label in aliases }?.key
                    if (field != null && !found.containsKey(field)) {
                        found[field] = value
                    }
                }
            }
        }

        // 2. Inline label matching
        val matcher = INLINE_LABEL.matcher(rawText)
        while (matcher.find()) {
            val label = matcher.group(1)?.trim()?.lowercase(Locale.ROOT) ?: continue
            val value = matcher.group(2)?.trim() ?: continue
            val field = LABELS.entries.firstOrNull { (_, aliases) -> label in aliases }?.key
            if (field != null && !found.containsKey(field)) {
                found[field] = value
            }
        }

        return found
    }

    // ------------------------------------------------------------------ payment

    private val PAYMENT_RULES: List<Pair<Regex, String>> = listOf(
        Regex("(?i)\\bcod\\b|cash on delivery|cash-on-delivery") to "COD",
        Regex("(?i)\\bupi\\b|gpay|google pay|phonepe|phone pe|paytm|bhim") to "UPI",
        Regex("(?i)\\badvance\\b|\\bprepaid\\b|paid in advance|peshgi") to "ADVANCE",
        Regex("(?i)\\bcard\\b|debit card|credit card") to "CARD",
        Regex("(?i)\\bnet ?banking\\b|neft|imps") to "BANK_TRANSFER",
        Regex("(?i)\\budhaar\\b|\\budhar\\b|\\bcredit\\b|baad me|उधार") to "CREDIT",
        Regex("(?i)\\bcash\\b|nagad|नकद") to "CASH",
    )

    fun paymentMethod(lowerText: String): String? {
        for ((pattern, canonical) in PAYMENT_RULES) {
            if (pattern.containsMatchIn(lowerText)) return canonical
        }
        return null
    }

    // ------------------------------------------------------------------ dates

    private val MONTHS: Map<String, Int> = mapOf(
        "january" to 1, "jan" to 1, "जनवरी" to 1,
        "february" to 2, "feb" to 2, "फ़रवरी" to 2, "फरवरी" to 2,
        "march" to 3, "mar" to 3, "मार्च" to 3,
        "april" to 4, "apr" to 4, "अप्रैल" to 4,
        "may" to 5, "मई" to 5,
        "june" to 6, "jun" to 6, "जून" to 6,
        "july" to 7, "jul" to 7, "जुलाई" to 7,
        "august" to 8, "aug" to 8, "अगस्त" to 8,
        "september" to 9, "sep" to 9, "sept" to 9, "सितंबर" to 9,
        "october" to 10, "oct" to 10, "अक्टूबर" to 10,
        "november" to 11, "nov" to 11, "नवंबर" to 11,
        "december" to 12, "dec" to 12, "दिसंबर" to 12,
    )

    private const val MONTH_ALT = "january|jan|february|feb|march|mar|april|apr|may|june|jun|july|jul|august|aug|september|sep|sept|october|oct|november|nov|december|dec|जनवरी|फ़रवरी|फरवरी|मार्च|अप्रैल|मई|जून|जुलाई|अगस्त|सितंबर|अक्टूबर|नवंबर|दिसंबर"

    private val DATE_DMY_SLASH = Pattern.compile(
        "\\b(0?[1-9]|[12][0-9]|3[01])[-/.](0?[1-9]|1[0-2])[-/.](20\\d{2}|\\d{2})\\b"
    )
    private val DATE_ISO = Pattern.compile(
        "\\b(20\\d{2})[-/.](0?[1-9]|1[0-2])[-/.](0?[1-9]|[12][0-9]|3[01])\\b"
    )
    private val DATE_NAMED_MONTH = Pattern.compile(
        "\\b(0?[1-9]|[12][0-9]|3[01])(?:st|nd|rd|th)?\\s+($MONTH_ALT)\\s*(20\\d{2}|\\d{2})?\\b",
        Pattern.CASE_INSENSITIVE
    )
    private val DATE_MONTH_FIRST = Pattern.compile(
        "\\b($MONTH_ALT)\\s+(0?[1-9]|[12][0-9]|3[01])(?:st|nd|rd|th)?(?:,)?\\s*(20\\d{2}|\\d{2})?\\b",
        Pattern.CASE_INSENSITIVE
    )

    data class DateResult(
        val iso: String,
        val rawText: String? = null,
        val ambiguous: Boolean = false,
        val note: String? = null,
    )

    fun absoluteDate(text: String, nowMillis: Long): DateResult? {
        val currentYear = Calendar.getInstance().apply { timeInMillis = nowMillis }.get(Calendar.YEAR)

        // 1. ISO YYYY-MM-DD
        val mIso = DATE_ISO.matcher(text)
        if (mIso.find()) {
            val y = mIso.group(1)!!.toInt()
            val m = mIso.group(2)!!.toInt()
            val d = mIso.group(3)!!.toInt()
            val raw = mIso.group(0)
            return DateResult(formatIso(y, m, d), rawText = raw)
        }

        // 2. Day Month Year (e.g. 30 August 2026, 30th Aug, 30 Aug)
        val mNamed = DATE_NAMED_MONTH.matcher(text)
        while (mNamed.find()) {
            val monthName = mNamed.group(2)!!.lowercase(Locale.ROOT)
            val monthNum = MONTHS[monthName] ?: continue
            val d = mNamed.group(1)!!.toInt()
            val yRaw = mNamed.group(3)
            val y = parseYear(yRaw, currentYear)
            val raw = mNamed.group(0)
            return DateResult(formatIso(y, monthNum, d), rawText = raw)
        }

        // 3. Month Day Year (e.g. August 30, 2026)
        val mMonthFirst = DATE_MONTH_FIRST.matcher(text)
        while (mMonthFirst.find()) {
            val monthName = mMonthFirst.group(1)!!.lowercase(Locale.ROOT)
            val monthNum = MONTHS[monthName] ?: continue
            val d = mMonthFirst.group(2)!!.toInt()
            val yRaw = mMonthFirst.group(3)
            val y = parseYear(yRaw, currentYear)
            val raw = mMonthFirst.group(0)
            return DateResult(formatIso(y, monthNum, d), rawText = raw)
        }

        // 4. DD/MM/YYYY or DD-MM-YYYY or DD.MM.YYYY
        val mSlash = DATE_DMY_SLASH.matcher(text)
        while (mSlash.find()) {
            val a = mSlash.group(1)!!.toInt()
            val b = mSlash.group(2)!!.toInt()
            val yRaw = mSlash.group(3)
            val y = parseYear(yRaw, currentYear)
            val raw = mSlash.group(0)

            if (a > 12 && b <= 12) {
                return DateResult(formatIso(y, b, a), rawText = raw)
            }
            if (b > 12 && a <= 12) {
                return DateResult(formatIso(y, a, b), rawText = raw)
            }
            // Indian commerce default: DD/MM/YYYY
            return DateResult(
                iso = formatIso(y, b, a),
                rawText = raw,
                ambiguous = true,
                note = "Date \"$raw\" was read as $a/${b}/$y (day-month-year); could also be month-first."
            )
        }

        return null
    }

    private fun parseYear(raw: String?, currentYear: Int): Int {
        if (raw == null || raw.isBlank()) return currentYear
        val n = raw.trim().toIntOrNull() ?: return currentYear
        return if (n < 100) 2000 + n else n
    }

    private fun formatIso(y: Int, m: Int, d: Int): String =
        String.format(Locale.US, "%04d-%02d-%02d", y, m, d)

    fun displayDate(iso: String?): String? {
        if (iso == null || !iso.matches(Regex("\\d{4}-\\d{2}-\\d{2}"))) return null
        val parts = iso.split("-")
        return "${parts[2]}/${parts[1]}/${parts[0]}"
    }

    // ------------------------------------------------------------------ cities

    val KNOWN_CITIES = setOf(
        "bhopal", "indore", "jabalpur", "gwalior", "ujjain", "sagar",
        "dewas", "satna", "ratlam", "rewa", "katni", "singrauli",
        "burhanpur", "khandwa", "sehore", "vidisha", "hoshangabad",
        "delhi", "mumbai", "pune", "nagpur", "jaipur", "ahmedabad",
        "bengaluru", "bangalore", "hyderabad", "chennai", "kolkata",
        "lucknow", "kanpur", "patna", "varanasi", "agra", "meerut",
        "भोपल", "भोपाल", "इंदौर", "ग्वालियर", "उज्जैन", "जबलपुर",
    )

    fun cityMention(tokens: List<String>): String? {
        for (t in tokens) {
            val hit = KNOWN_CITIES.firstOrNull { it.equals(t, ignoreCase = true) }
            if (hit != null) return hit.replaceFirstChar { it.uppercase() }
        }
        return null
    }
}
