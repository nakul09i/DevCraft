package com.devcraft.parser.offline

import java.util.Locale

/**
 * Categorization for incoming conversational and notification messages.
 * Classification runs before extraction so non-orders (documents, OTPs, bank alerts,
 * delivery confirmations) can NEVER be converted into orders.
 */
enum class MessageCategory {
    ORDER,
    BANK_FINANCIAL,
    OTP_AUTHENTICATION,
    DELIVERY_TRACKING,
    PERSONAL_MESSAGE,
    PROMOTIONAL,
    SYSTEM_NOTIFICATION,
    DOCUMENT_FILE,
    UNKNOWN;

    val label: String
        get() = when (this) {
            ORDER -> "Order"
            OTP_AUTHENTICATION -> "OTP"
            BANK_FINANCIAL -> "Bank"
            DELIVERY_TRACKING -> "Delivery"
            PERSONAL_MESSAGE -> "Personal"
            PROMOTIONAL -> "Promotion"
            SYSTEM_NOTIFICATION -> "System"
            DOCUMENT_FILE -> "Document"
            UNKNOWN -> "Unknown"
        }

    /** Only ORDER messages are eligible for order extraction. */
    val isOrder: Boolean get() = this == ORDER

    companion object {
        val OTP get() = OTP_AUTHENTICATION
        val BANK get() = BANK_FINANCIAL
        val DELIVERY get() = DELIVERY_TRACKING
        val PERSONAL get() = PERSONAL_MESSAGE
        val SYSTEM get() = SYSTEM_NOTIFICATION
        val PAYMENT get() = BANK_FINANCIAL
    }
}

data class ClassificationResult(
    val category: MessageCategory,
    val confidence: Float,
    val reason: String? = null
)

/**
 * Fully local, deterministic, rule-based classifier.
 *
 * Evaluation order:
 *  1. Document / File metadata (PDF, doc, (8 pages), attachments) -> DOCUMENT_FILE
 *  2. Authentication / OTP -> OTP_AUTHENTICATION
 *  3. Bank / Financial alerts -> BANK_FINANCIAL
 *  4. Delivery tracking status -> DELIVERY_TRACKING
 *  5. Promotional / Marketing -> PROMOTIONAL
 *  6. System / Network notifications -> SYSTEM_NOTIFICATION
 *  7. Personal messages -> PERSONAL_MESSAGE
 *  8. Hard Order Intent Gate -> ORDER
 *  9. Fallback -> UNKNOWN
 */
object MessageClassifier {

    // ------------------------------------------------------------- Document & File
    private val DOCUMENT_EXTENSIONS = listOf(
        ".pdf", ".doc", ".docx", ".xls", ".xlsx", ".ppt", ".pptx",
        ".csv", ".zip", ".apk", ".jpg", ".jpeg", ".png", ".mp4",
        ".txt", ".tar", ".gz", ".rar", ".7z"
    )

    private val DOCUMENT_PATTERNS = listOf(
        Regex("(?i)\\.pdf\\b|\\.docx?\\b|\\.xlsx?\\b|\\.pptx?\\b|\\.apk\\b|\\.zip\\b"),
        Regex("(?i)\\(\\s*\\d+\\s*pages?\\s*\\)"),
        Regex("(?i)\\b\\d+\\s*pages?\\b"),
        Regex("(?i)\\bpage\\s*\\d+\\b"),
        Regex("(?i)\\b(attachment|attached file|downloaded|uploaded|shared a file|screenshot|test_report|architecture_test)\\b")
    )

    // ------------------------------------------------------------- OTP & Auth
    private val OTP_MARKERS = listOf(
        "otp", "one time password", "one-time password", "verification code",
        "verify code", "security code", "login code", "auth code", "2fa",
        "do not share", "never share", "confidential code", "your otp is",
        "use code", "code to login", "otp is"
    )

    // ------------------------------------------------------------- Banking & Finance
    private val BANK_MARKERS = listOf(
        "debited", "credited", "a/c", "acct", "account ending", "avl bal",
        "available balance", "closing balance", "bank", "neft", "imps", "rtgs",
        "min amt due", "statement", "emi", "loan", "interest", "atm",
        "txn", "transaction id", "ref no", "upi ref", "credited with",
        "debited by", "account has been credited", "account has been debited",
        "payment received", "paid successfully", "refund", "cashback credited", "wallet credited"
    )

    // ------------------------------------------------------------- Delivery Tracking
    private val DELIVERY_STRONG = listOf(
        "has been shipped", "has been dispatched", "has been delivered",
        "out for delivery", "in transit", "is arriving", "will be delivered",
        "tracking id", "tracking number", "awb", "consignment",
        "parcel has been", "package has been", "shipment has",
        "delivered successfully", "item delivered"
    )

    private val DELIVERY_MARKERS = listOf(
        "shipped", "dispatched", "delivered", "tracking", "courier", "shipment",
        "arriving"
    )

    // ------------------------------------------------------------- Promotional
    private val PROMO_MARKERS = listOf(
        "sale", "offer", "discount", "coupon", "off on", "limited time",
        "buy now", "shop now", "click here", "unsubscribe", "t&c apply",
        "hurry", "flat 50", "cashback offer", "download the app", "win ",
        "% off", "get 50% off"
    )

    // ------------------------------------------------------------- System
    private val SYSTEM_MARKERS = listOf(
        "your plan", "recharge", "data balance", "validity", "network",
        "sim card", "roaming", "activated successfully", "server update",
        "system notification", "low battery", "storage full"
    )

    // ------------------------------------------------------------- Order Vocabulary
    private val ORDER_INTENT_WORDS = setOf(
        "order", "ordered", "buy", "purchase", "need", "send", "deliver", "delivery",
        "chahiye", "chaiye", "bhejo", "bhej", "bhejna", "dena", "parcel", "parcels", "packet", "bori",
        "kg", "piece", "pcs", "pieces", "dozen", "box", "carton", "bag", "cod",
        "cash on delivery", "advance",
        "चाहिए", "भेजो", "भेज", "देना", "ऑर्डर", "पैकेट", "बोरी", "किलो", "कुर्ता", "सीमेंट",
        "parcal", "parcals", "saman", "samaan", "सामान"
    )

    private val PERSONAL_MARKERS = listOf(
        "call me", "how are you", "kaise ho", "kya kar", "good morning",
        "good night", "happy birthday", "miss you", "love you", "kahan ho",
        "hiii", "hello bro", "hey bro", "kya haal"
    )

    // Words that follow a number but must NEVER be treated as commercial merchandise
    private val NON_ITEM_UNITS = setOf(
        "pages", "page", "min", "mins", "minutes", "minute", "sec", "secs", "seconds",
        "hour", "hours", "hrs", "day", "days", "month", "months", "year", "years",
        "am", "pm", "kb", "mb", "gb", "fps", "dpi", "steps", "points", "times",
        "percent", "%", "seats", "version", "build", "update", "stars", "likes",
        "views", "words", "lines", "runs", "tickets", "items", "number", "numbers"
    )

    fun classify(rawText: String): MessageCategory = classifyWithDetails(rawText).category

    fun classifyWithDetails(rawText: String): ClassificationResult {
        val trimmed = rawText.trim()
        if (trimmed.isBlank()) {
            return ClassificationResult(MessageCategory.UNKNOWN, 0.0f, "Empty message")
        }

        val text = trimmed.lowercase(Locale.ROOT)

        // 1. Document / File metadata check (Strict prevention of PDF/file false positives)
        if (DOCUMENT_EXTENSIONS.any { text.contains(it) } || DOCUMENT_PATTERNS.any { it.containsMatchIn(text) }) {
            return ClassificationResult(MessageCategory.DOCUMENT_FILE, 0.99f, "Document or file attachment detected")
        }

        // 2. Sensitive check: OTP / Authentication code
        if (OTP_MARKERS.any { text.contains(it) }) {
            return ClassificationResult(MessageCategory.OTP_AUTHENTICATION, 0.98f, "OTP or verification code pattern")
        }
        if (trimmed.length <= 8 && trimmed.all { it.isDigit() }) {
            return ClassificationResult(MessageCategory.OTP_AUTHENTICATION, 0.95f, "Standalone verification code")
        }

        // 3. Bank & Financial notifications
        if (BANK_MARKERS.any { text.contains(it) }) {
            return ClassificationResult(MessageCategory.BANK_FINANCIAL, 0.96f, "Bank / financial transaction alert")
        }

        // 4. Delivery status updates (past/in-flight shipments, NOT new orders)
        if (DELIVERY_STRONG.any { text.contains(it) }) {
            return ClassificationResult(MessageCategory.DELIVERY_TRACKING, 0.95f, "Shipment tracking status update")
        }
        if (DELIVERY_MARKERS.any { text.contains(it) } && !hasStrongOrderEvidence(text)) {
            return ClassificationResult(MessageCategory.DELIVERY_TRACKING, 0.85f, "Delivery notice")
        }

        // 5. Promotional
        if (PROMO_MARKERS.count { text.contains(it) } >= 2 || (text.contains("get 50% off") || text.contains("flat 50") || text.contains("big sale"))) {
            return ClassificationResult(MessageCategory.PROMOTIONAL, 0.92f, "Marketing or promotional text")
        }

        // 6. System notification
        if (SYSTEM_MARKERS.any { text.contains(it) } && !hasStrongOrderEvidence(text)) {
            return ClassificationResult(MessageCategory.SYSTEM_NOTIFICATION, 0.90f, "System or telecom notification")
        }

        // 7. Order intent verification
        if (hasStrongOrderEvidence(text)) {
            return ClassificationResult(MessageCategory.ORDER, 0.97f, "High order intent signals detected")
        }

        // 8. Personal chat markers
        if (PERSONAL_MARKERS.any { text.contains(it) }) {
            return ClassificationResult(MessageCategory.PERSONAL_MESSAGE, 0.90f, "Personal conversational message")
        }

        // 9. Quantity + Merchandise noun (excluding time/pages/units)
        if (hasValidMerchandiseQuantity(text)) {
            return ClassificationResult(MessageCategory.ORDER, 0.88f, "Quantity and merchandise pattern detected")
        }

        // 10. Fallback: Short conversational vs Unknown
        return if (text.split(Regex("\\s+")).size <= 4 && !text.any { it.isDigit() }) {
            ClassificationResult(MessageCategory.PERSONAL_MESSAGE, 0.65f, "Short conversational text")
        } else {
            ClassificationResult(MessageCategory.UNKNOWN, 0.50f, "Unclassified / ambiguous content")
        }
    }

    private fun hasStrongOrderEvidence(text: String): Boolean {
        val matches = ORDER_INTENT_WORDS.count { text.contains(it) }
        val hasQtyNoun = hasValidMerchandiseQuantity(text)
        return (matches >= 1) || hasQtyNoun
    }

    private fun hasValidMerchandiseQuantity(text: String): Boolean {
        val regex = Regex("(?:[0-9\\u0966-\\u096F]+|do|teen|tin|chaar|paanch|chhah|saat|aath|nau|das)\\s+([\\p{L}]{2,})", RegexOption.IGNORE_CASE)
        val match = regex.find(text) ?: return false
        val noun = match.groupValues[1].lowercase(Locale.ROOT)
        return noun !in NON_ITEM_UNITS
    }
}
