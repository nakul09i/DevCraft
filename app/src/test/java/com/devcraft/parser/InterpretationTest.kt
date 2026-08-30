package com.devcraft.parser

import com.devcraft.parser.offline.DeterministicParser
import com.devcraft.parser.offline.MessageCategory
import com.devcraft.parser.offline.MessageClassifier
import org.junit.Assert.*
import org.junit.Test
import java.text.SimpleDateFormat
import java.util.Locale

/**
 * The exact bug that was reported from the device: the Interpret button produced
 *
 *   Customer Name: Guest Customer
 *   Item: "2x nakul food parcels location bhopal delivery august amount cod"
 *
 * because the old parser had no label support, required an honorific to find a
 * name, and treated every leftover token as the item. These tests pin the fix.
 *
 * A fixed date anchor is injected so results are deterministic.
 */
class InterpretationTest {

    /** 2026-08-25, so "30 August 2026" is in the future. */
    private val anchor: Long =
        SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.US).parse("2026-08-25 10:00")!!.time

    private fun parse(text: String) = DeterministicParser.parse(text, anchor)

    // ---------------------------------------------------------- example 1

    @Test
    fun labelledMultiLineOrderIsFullyStructured() {
        val result = parse(
            """
            Nakul
            2 food parcels
            Location: Bhopal
            Delivery: 30 August 2026
            Amount: Rs 500
            COD
            """.trimIndent()
        )

        assertEquals("Nakul", result.customer)
        assertEquals(2, result.quantity)
        assertEquals("food parcels", result.itemDescription)
        assertEquals("Bhopal", result.delivery_address)
        assertEquals("2026-08-30", result.due_date)
        assertEquals("30/08/2026", result.display_date)
        assertEquals(500.0, result.amount ?: 0.0, 0.01)
        assertEquals("COD", result.payment_method)
        assertEquals(MessageCategory.ORDER, result.classification)
    }

    @Test
    fun customerNameIsNeverMergedIntoTheItem() {
        val result = parse(
            "Nakul\n2 food parcels\nLocation: Bhopal\nAmount: Rs 500\nCOD"
        )
        val item = result.itemDescription.orEmpty()
        // The reported bug, asserted directly.
        assertFalse("item leaked the customer: $item", item.contains("nakul", ignoreCase = true))
        assertFalse("item leaked the location: $item", item.contains("bhopal", ignoreCase = true))
        assertFalse("item leaked a label: $item", item.contains("location", ignoreCase = true))
        assertFalse("item leaked payment: $item", item.contains("cod", ignoreCase = true))
        assertNotEquals("Guest Customer", result.customer)
    }

    // ---------------------------------------------------------- example 2

    @Test
    fun fieldOrderDoesNotMatter() {
        val ordered = parse(
            "Nakul\n2 food parcels\nLocation: Bhopal\nDelivery: 30/08/2026\nAmount: Rs 500\nCOD"
        )
        val shuffled = parse(
            "500 COD\nBhopal\n30/08/2026\n2 food parcels\nNakul"
        )

        assertEquals(ordered.quantity, shuffled.quantity)
        assertEquals(ordered.due_date, shuffled.due_date)
        assertEquals(ordered.amount, shuffled.amount)
        assertEquals(ordered.payment_method, shuffled.payment_method)
        assertEquals("Nakul", shuffled.customer)
        assertEquals("food parcels", shuffled.itemDescription)
        assertTrue(shuffled.delivery_address.orEmpty().contains("Bhopal", ignoreCase = true))
    }

    // ---------------------------------------------------------- example 3

    @Test
    fun messySingleLineOrderIsStructured() {
        val result = parse("nakul bhopal 2 food parcel 500 cash on delivery 30 aug 2026")

        assertEquals("Nakul", result.customer)
        assertEquals(2, result.quantity)
        assertEquals("food parcel", result.itemDescription)
        assertEquals("Bhopal", result.delivery_address)
        assertEquals(500.0, result.amount ?: 0.0, 0.01)
        assertEquals("COD", result.payment_method)
        assertEquals("2026-08-30", result.due_date)
    }

    // ---------------------------------------------------------- example 4

    @Test
    fun obviousCityMisspellingIsRecovered() {
        val result = parse("Nakul\n2 fud parcals\nBhopl\n30 aug 2026\nrs 500 cod")

        assertEquals("Nakul", result.customer)
        assertEquals(2, result.quantity)
        assertEquals("Bhopal", result.delivery_address)
        assertEquals(500.0, result.amount ?: 0.0, 0.01)
        // The item text itself is NOT autocorrected - we do not guess goods.
        assertEquals("fud parcals", result.itemDescription)
        assertTrue(
            "a fuzzy match should be surfaced",
            result.review_notes.any { it.contains("misspelling", ignoreCase = true) },
        )
    }

    // ---------------------------------------------------------- dates

    @Test
    fun allSupportedDateFormatsResolveToTheSameDay() {
        val forms = listOf(
            "Nakul 2 parcels 30 August 2026 rs 500 cod",
            "Nakul 2 parcels 30 Aug 2026 rs 500 cod",
            "Nakul 2 parcels 30/08/2026 rs 500 cod",
            "Nakul 2 parcels 30-08-2026 rs 500 cod",
            "Nakul 2 parcels 2026-08-30 rs 500 cod",
        )
        for (f in forms) {
            assertEquals("failed for: $f", "2026-08-30", parse(f).due_date)
        }
    }

    @Test
    fun ambiguousNumericDateIsFlaggedNotGuessedSilently() {
        val result = parse("Nakul 2 parcels 08/09/2026 rs 500 cod")
        assertTrue("must be flagged for review", result.needs_clarification)
        assertTrue(
            "must explain the ambiguity",
            result.review_notes.any { it.contains("day-month", ignoreCase = true) },
        )
    }

    @Test
    fun relativeDatesUseTheDeviceDate() {
        assertEquals("2026-08-25", parse("Nakul 2 parcels today rs 500 cod").due_date)
        assertEquals("2026-08-26", parse("Nakul 2 parcels kal rs 500 cod").due_date)
        assertEquals("2026-08-27", parse("Nakul 2 parcels parso rs 500 cod").due_date)
    }

    @Test
    fun nextWeekdayIsStrictlyTheNextOccurrence() {
        // 2026-08-25 is a Tuesday; next Friday is the 28th
        assertEquals("2026-08-28", parse("Nakul 2 parcels Friday rs 500 cod").due_date)
    }

    // ---------------------------------------------------------- missing fields

    @Test
    fun missingFieldsAreListedNotInvented() {
        val result = parse("Nakul\n2 food parcels")

        assertNull("no date was stated", result.due_date)
        assertNull("no amount was stated", result.amount)
        assertNull("no payment method was stated", result.payment_method)
        assertTrue(result.missing_fields.contains("Delivery date"))
        assertTrue(result.missing_fields.contains("Amount"))
        assertTrue(result.missing_fields.contains("Payment method"))
        assertTrue("incomplete order must be reviewed", result.needs_clarification)
    }

    @Test
    fun amountIsNotInventedWithoutMoneyEvidence() {
        // "2 cars 500" has no currency symbol and no payment term
        assertNull(parse("Ramesh bhai 2 cars 500").amount)
    }

    @Test
    fun confidenceReflectsHowMuchWasActuallyFound() {
        val full = parse("Nakul\n2 food parcels\nLocation: Bhopal\nDelivery: 30 Aug 2026\nAmount: Rs 500\nCOD")
        val sparse = parse("Nakul\n2 food parcels")

        assertTrue("full order should be confident: ${full.confidence}", full.confidence >= 0.9f)
        assertTrue("sparse order should not be: ${sparse.confidence}", sparse.confidence < 0.7f)
        assertTrue("never claim certainty", full.confidence <= 0.97f)
        assertTrue(full.confidence > sparse.confidence)
    }

    // ---------------------------------------------------------- classification

    @Test
    fun sensitiveMessagesAreNeverClassifiedAsOrders() {
        assertEquals(MessageCategory.OTP, MessageClassifier.classify("OTP 492811"))
        assertEquals(
            MessageCategory.OTP,
            MessageClassifier.classify("492811 is your verification code. Do not share."),
        )
        assertEquals(
            MessageCategory.BANK,
            MessageClassifier.classify("Rs 500 debited from your a/c XX1234. Avl bal 2300."),
        )
    }

    @Test
    fun deliveryAndPersonalAndPromoAreClassified() {
        assertEquals(
            MessageCategory.DELIVERY,
            MessageClassifier.classify("Your parcel has been shipped and is out for delivery"),
        )
        assertEquals(MessageCategory.PERSONAL, MessageClassifier.classify("Hi bro call me"))
        assertEquals(
            MessageCategory.PROMOTIONAL,
            MessageClassifier.classify("Big sale! Flat 50 off on everything. Shop now, T&C apply"),
        )
    }

    @Test
    fun realOrdersAreClassifiedAsOrders() {
        assertEquals(MessageCategory.ORDER, MessageClassifier.classify("2 burgers Bhopal 500 COD"))
        assertEquals(
            MessageCategory.ORDER,
            MessageClassifier.classify("Ramesh bhaiya ko kal 10 bori cement bhejo Rs 3500"),
        )
    }

    @Test
    fun nonOrderMessagesCannotLookConfident() {
        val otp = parse("Your OTP is 492811. Do not share it with anyone.")
        assertEquals(MessageCategory.OTP, otp.classification)
        assertTrue("an OTP must never look confident", otp.confidence <= 0.4f)
        assertTrue(otp.needs_clarification)
        assertTrue(otp.review_notes.any { it.contains("not an order", ignoreCase = true) })
    }

    // ---------------------------------------------------------- multilingual

    @Test
    fun hinglishAndDevanagariStillWork() {
        val hinglish = parse("Ramesh bhaiya ko kal shaam 10 bori cement bhejo Rs 3500")
        assertEquals("Ramesh", hinglish.customer)
        assertEquals(10, hinglish.quantity)
        assertEquals(3500.0, hinglish.amount ?: 0.0, 0.01)

        val devanagari = parse("सुरेश भाई को ३ पैकेट नमकीन आज चाहिए ₹450")
        assertEquals("सुरेश", devanagari.customer)
        assertEquals(3, devanagari.quantity)
        assertEquals(450.0, devanagari.amount ?: 0.0, 0.01)
    }

    @Test
    fun phoneNumberIsExtractedWhenPresentAndNotConfusedWithMoney() {
        val result = parse("Nakul 2 parcels contact 9876543210 rs 500 cod Bhopal")
        assertEquals("9876543210", result.phone)
        assertEquals(500.0, result.amount ?: 0.0, 0.01)
        assertEquals(2, result.quantity)
    }
}
