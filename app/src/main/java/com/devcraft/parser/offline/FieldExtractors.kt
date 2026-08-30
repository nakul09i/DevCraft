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
        "customer" to listOf("name", "customer", "customer name", "cust", "party", "naam", "नाम", "ग्राहक"),
        "phone" to listOf("phone", "mobile", "contact", "mob", "no", "number", "फोन", "मोबाइल"),
        "item" to listOf("item", "items", "product", "goods", "maal", "saman", "samaan", "सामान", "माल"),
        "quantity" to listOf("qty", "quantity", "nos", "count", "मात्रा"),
        "location" to listOf("location", "address", "addr", "place", "city", "deliver to", "delivery at", "pata", "पता", "जगह"),
        "date" to listOf("delivery", "delivery date", "date", "due", "due date", "deliver by", "by", "tarikh", "तारीख", "दिनांक"),
        "amount" to listOf("amount", "amt", "total", "price", "rate", "value", "rs", "cost", "राशि", "कीमत"),
        "payment" to listOf("payment", "payment method", "pay", "mode", "payment mode", "भुगतान"),
    )

    private val LABEL_LINE = Pattern.compile("^\\s*([\\p{L} ]{2,20})\\s*[:\\-–]\\s*(.+)$")

    /**
     * Parses "Label: value" lines. Returns canonical field -> raw value.
     * Label-driven parsing is what makes field order irrelevant.
     */
    fun labelledFields(rawText: String): Map<String, String> {
        val found = mutableMapOf<String, String>()
        for (line in rawText.split(Regex("[\\n\\r]+"))) {
            val m = LABEL_LINE.matcher(line.trim())
            if (!m.matches()) continue
            val label = m.group(1)?.trim()?.lowercase(Locale.ROOT) ?: continue
            val value = m.group(2)?.trim() ?: continue
            if (value.isEmpty()) continue

            val field = LABELS.entries.firstOrNull { (_, aliases) -> label in aliases }?.key
            if (field != null && !found.containsKey(field)) {
                found[field] = value
            }
        }
        return found
    }

    // ------------------------------------------------------------------ payment

    private val PAYMENT_RULES: List<Pair<Regex, String>> = listOf(
        Regex("\\bcod\\b|cash on delivery|cash-on-delivery") to "COD",
        Regex("\\bupi\\b|gpay|google pay|phonepe|phone pe|paytm|bhim") to "UPI",
        Regex("\\badvance\\b|\\bprepaid\\b|paid in advance|peshgi") to "ADVANCE",
        Regex("\\bcard\\b|debit card|credit card") to "CARD",
        Regex("\\bnet ?banking\\b|neft|imps") to "BANK_TRANSFER",
        Regex("\\budhaar\\b|\\budhar\\b|\\bcredit\\b|baad me|उधार") to "CREDIT",
        Regex("\\bcash\\b|nagad|नकद") to "CASH",
    )

    fun paymentMethod(lowerText: String): String? =
        PAYMENT_RULES.firstOrNull { (regex, _) -> regex.containsMatchIn(lowerText) }?.second

    // ------------------------------------------------------------------ dates

    private val MONTHS: Map<String, Int> = buildMap {
        val full = listOf(
            "january", "february", "march", "april", "may", "june",
            "july", "august", "september", "october", "november", "december",
        )
        full.forEachIndexed { i, name ->
            put(name, i + 1)
            put(name.take(3), i + 1)
        }
        put("sept", 9)
    }

    /**
     * Month names are validated inside the pattern, not afterwards. An earlier
     * version accepted any 3-9 letter word and post-checked it, which let
     * "2 parcels 30" match first and swallow the digits of the real date.
     */
    private const val MONTH_ALT =
        "jan(?:uary)?|feb(?:ruary)?|mar(?:ch)?|apr(?:il)?|may|jun(?:e)?|jul(?:y)?" +
            "|aug(?:ust)?|sep(?:t)?(?:ember)?|oct(?:ober)?|nov(?:ember)?|dec(?:ember)?"

    /** "30 August 2026", "30 aug 2026" */
    private val TEXT_DATE = Pattern.compile(
        "\\b(\\d{1,2})\\s*(?:st|nd|rd|th)?[\\s.,-]+($MONTH_ALT)\\b[\\s.,-]*(\\d{4}|\\d{2})?",
        Pattern.CASE_INSENSITIVE,
    )
    /** "aug 30 2026" */
    private val MONTH_FIRST_DATE = Pattern.compile(
        "\\b($MONTH_ALT)\\b[\\s.,-]+(\\d{1,2})(?:st|nd|rd|th)?[\\s.,-]*(\\d{4}|\\d{2})?",
        Pattern.CASE_INSENSITIVE,
    )
    /** 30/08/2026, 30-08-2026, 2026-08-30 */
    private val NUMERIC_DATE = Pattern.compile("\\b(\\d{1,4})[/.-](\\d{1,2})[/.-](\\d{2,4})\\b")

    data class DateResult(val iso: String, val ambiguous: Boolean, val note: String? = null)

    /**
     * Absolute date in any supported written form. Returns null when none is
     * present - never a guess.
     */
    fun absoluteDate(rawText: String, nowMillis: Long): DateResult? {
        NUMERIC_DATE.matcher(rawText).let { m ->
            if (m.find()) {
                val a = m.group(1)!!.toInt()
                val b = m.group(2)!!.toInt()
                val c = m.group(3)!!
                // yyyy-MM-dd
                if (m.group(1)!!.length == 4) {
                    return isoOrNull(a, b, c.toInt())?.let { DateResult(it, false) }
                }
                val year = normaliseYear(c.toInt())
                // Both <= 12: genuinely ambiguous. Assume day-first (Indian
                // convention) but flag it rather than pretend certainty.
                val ambiguous = a <= 12 && b <= 12 && a != b
                return isoOrNull(year, b, a)?.let {
                    DateResult(
                        iso = it,
                        ambiguous = ambiguous,
                        note = if (ambiguous)
                            "Date $a/$b could be day-month or month-day. Read as day-first."
                        else null,
                    )
                }
            }
        }

        TEXT_DATE.matcher(rawText).let { m ->
            while (m.find()) {
                val day = m.group(1)!!.toInt()
                val month = MONTHS[m.group(2)!!.lowercase(Locale.ROOT)] ?: continue
                val year = m.group(3)?.toIntOrNull()?.let { normaliseYear(it) }
                    ?: yearOf(nowMillis)
                isoOrNull(year, month, day)?.let { return DateResult(it, false) }
            }
        }

        MONTH_FIRST_DATE.matcher(rawText).let { m ->
            while (m.find()) {
                val month = MONTHS[m.group(1)!!.lowercase(Locale.ROOT)] ?: continue
                val day = m.group(2)!!.toInt()
                val year = m.group(3)?.toIntOrNull()?.let { normaliseYear(it) }
                    ?: yearOf(nowMillis)
                isoOrNull(year, month, day)?.let { return DateResult(it, false) }
            }
        }

        return null
    }

    private fun normaliseYear(y: Int) = if (y < 100) 2000 + y else y

    private fun yearOf(millis: Long) = Calendar.getInstance()
        .apply { timeInMillis = millis }
        .get(Calendar.YEAR)

    private fun isoOrNull(year: Int, month: Int, day: Int): String? {
        if (month !in 1..12 || day !in 1..31) return null
        val cal = Calendar.getInstance().apply {
            isLenient = false
            clear()
            set(year, month - 1, day)
        }
        return try {
            SimpleDateFormat("yyyy-MM-dd", Locale.US).format(cal.time)
        } catch (e: Exception) {
            null
        }
    }

    /** UI format. Internal storage stays ISO. */
    fun displayDate(iso: String?): String? {
        if (iso.isNullOrBlank()) return null
        return try {
            val parsed = SimpleDateFormat("yyyy-MM-dd", Locale.US).apply { isLenient = false }
                .parse(iso) ?: return null
            SimpleDateFormat("dd/MM/yyyy", Locale.US).format(parsed)
        } catch (e: Exception) {
            null
        }
    }

    // ------------------------------------------------------------------ location

    /**
     * A deliberately small list of cities, used only when no Location label and
     * no address vocabulary is present.
     *
     * ponytail: a hardcoded list will miss most towns in India. It exists so
     * "nakul bhopal 2 parcels" resolves at all; a labelled "Location:" always
     * wins over it. Upgrade path is an offline place gazetteer, or Mappls
     * geocoding once a key exists.
     */
    val KNOWN_CITIES = setOf(
        "indore", "bhopal", "jabalpur", "gwalior", "ujjain", "dewas", "sagar",
        "mumbai", "pune", "nagpur", "nashik", "thane",
        "delhi", "noida", "gurgaon", "gurugram", "faridabad", "ghaziabad",
        "jaipur", "jodhpur", "udaipur", "kota", "ajmer",
        "lucknow", "kanpur", "agra", "varanasi", "prayagraj", "meerut",
        "ahmedabad", "surat", "vadodara", "rajkot",
        "bengaluru", "bangalore", "mysuru", "hubli",
        "hyderabad", "chennai", "coimbatore", "madurai",
        "kolkata", "howrah", "patna", "ranchi", "raipur", "bhubaneswar",
        "chandigarh", "ludhiana", "amritsar", "dehradun", "guwahati", "kochi",
    )

    fun cityMention(tokens: List<String>): String? =
        tokens.firstOrNull { it in KNOWN_CITIES }?.replaceFirstChar { it.uppercase() }
}
