package com.devcraft.parser.offline

import java.util.Locale

/**
 * What kind of message this is. Classification runs before extraction so an OTP
 * or a bank debit can never be turned into an order.
 */
enum class MessageCategory {
    ORDER,
    OTP,
    BANK,
    PAYMENT,
    DELIVERY,
    PERSONAL,
    PROMOTIONAL,
    SYSTEM,
    UNKNOWN;

    val label: String
        get() = when (this) {
            ORDER -> "Order"
            OTP -> "Verification code"
            BANK -> "Bank alert"
            PAYMENT -> "Payment"
            DELIVERY -> "Delivery update"
            PERSONAL -> "Personal"
            PROMOTIONAL -> "Promotion"
            SYSTEM -> "System"
            UNKNOWN -> "Unknown"
        }

    /** Only ORDER messages are eligible for order extraction. */
    val isOrder: Boolean get() = this == ORDER
}

/**
 * Fully local, rule-based classifier. No network, no model.
 *
 * Ordering matters: the sensitive categories (OTP, bank) are tested first and
 * win outright, because misfiling one of those as an order is the expensive
 * mistake. Order detection is deliberately last and requires positive evidence.
 */
object MessageClassifier {

    private val OTP_MARKERS = listOf(
        "otp", "one time password", "one-time password", "verification code",
        "verify code", "security code", "login code", "auth code", "2fa",
        "do not share", "never share", "confidential code",
    )

    private val BANK_MARKERS = listOf(
        "debited", "credited", "a/c", "acct", "account ending", "avl bal",
        "available balance", "closing balance", "bank", "neft", "imps", "rtgs",
        "min amt due", "statement", "emi", "loan", "interest", "atm",
        "txn", "transaction id", "ref no", "upi ref",
    )

    private val PAYMENT_MARKERS = listOf(
        "payment received", "payment of", "paid successfully", "received rs",
        "money received", "refund", "cashback credited", "wallet",
    )

    /**
     * Unambiguous status updates about an existing shipment. These are past-tense
     * or in-flight reports and are never someone placing an order, so they win
     * outright even though they share vocabulary with orders ("parcel",
     * "delivery").
     */
    private val DELIVERY_STRONG = listOf(
        "has been shipped", "has been dispatched", "has been delivered",
        "out for delivery", "in transit", "is arriving", "will be delivered",
        "tracking id", "tracking number", "awb", "consignment",
        "parcel has been", "package has been", "shipment has",
    )

    private val DELIVERY_MARKERS = listOf(
        "shipped", "dispatched", "delivered", "tracking", "courier", "shipment",
        "arriving",
    )

    private val PROMO_MARKERS = listOf(
        "sale", "offer", "discount", "coupon", "off on", "limited time",
        "buy now", "shop now", "click here", "unsubscribe", "t&c apply",
        "hurry", "flat 50", "cashback offer", "download the app", "win ",
    )

    private val SYSTEM_MARKERS = listOf(
        "your plan", "recharge", "data balance", "validity", "network",
        "sim card", "roaming", "activated successfully", "server",
    )

    /** Order vocabulary: goods, quantities, payment terms, delivery intent. */
    private val ORDER_MARKERS = listOf(
        "order", "chahiye", "chaiye", "bhejo", "bhej", "deliver", "delivery",
        "parcel", "parcels", "packet", "bori", "kg", "piece", "pcs", "pieces",
        "dozen", "box", "carton", "bag", "cod", "cash on delivery", "advance",
        "quantity", "qty", "amount", "total", "rate", "each",
        "चाहिए", "भेजो", "ऑर्डर", "पैकेट", "बोरी", "किलो",
    )

    private val PERSONAL_MARKERS = listOf(
        "call me", "how are you", "kaise ho", "kya kar", "good morning",
        "good night", "happy birthday", "miss you", "love you", "kahan ho",
    )

    fun classify(rawText: String): MessageCategory {
        val text = rawText.lowercase(Locale.ROOT)
        if (text.isBlank()) return MessageCategory.UNKNOWN

        // Sensitive first: never let these reach order extraction.
        if (OTP_MARKERS.any { text.contains(it) }) return MessageCategory.OTP
        // A bare short numeric body is a code, not an order.
        val trimmed = rawText.trim()
        if (trimmed.length <= 8 && trimmed.all { it.isDigit() }) return MessageCategory.OTP

        if (BANK_MARKERS.count { text.contains(it) } >= 1 &&
            !hasStrongOrderEvidence(text)
        ) return MessageCategory.BANK

        if (PAYMENT_MARKERS.any { text.contains(it) }) return MessageCategory.PAYMENT

        // Strong delivery phrasing wins outright; weak markers defer to order evidence.
        if (DELIVERY_STRONG.any { text.contains(it) }) return MessageCategory.DELIVERY
        if (DELIVERY_MARKERS.any { text.contains(it) } && !hasStrongOrderEvidence(text)) {
            return MessageCategory.DELIVERY
        }

        if (PROMO_MARKERS.count { text.contains(it) } >= 2) return MessageCategory.PROMOTIONAL
        if (SYSTEM_MARKERS.any { text.contains(it) } && !hasStrongOrderEvidence(text)) {
            return MessageCategory.SYSTEM
        }

        // Order needs positive evidence, not merely the absence of other signals.
        if (hasStrongOrderEvidence(text)) return MessageCategory.ORDER

        if (PERSONAL_MARKERS.any { text.contains(it) }) return MessageCategory.PERSONAL

        // A quantity plus a noun is enough on its own ("2 burgers").
        if (QUANTITY_NOUN.containsMatchIn(text)) return MessageCategory.ORDER

        return if (text.split(Regex("\\s+")).size <= 6) {
            MessageCategory.PERSONAL
        } else {
            MessageCategory.UNKNOWN
        }
    }

    private val QUANTITY_NOUN = Regex("\\b\\d{1,4}\\s+[\\p{L}]{3,}")

    private fun hasStrongOrderEvidence(text: String): Boolean =
        ORDER_MARKERS.count { text.contains(it) } >= 2 ||
            (ORDER_MARKERS.any { text.contains(it) } && QUANTITY_NOUN.containsMatchIn(text))
}
