package com.devcraft.parser

import com.devcraft.parser.offline.DeterministicParser
import com.devcraft.parser.offline.MessageCategory
import com.devcraft.parser.offline.MessageClassifier
import org.junit.Assert.*
import org.junit.Test
import java.text.SimpleDateFormat
import java.util.Locale

class MasterRegressionTest {

    // Fixed reference anchor: 2026-08-25 10:00 AM (Tuesday)
    private val anchor: Long =
        SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.US).parse("2026-08-25 10:00")!!.time

    private fun parse(text: String) = DeterministicParser.parse(text, anchor)

    // =========================================================================
    // REQUIRED PROMPT TEST SUITE (TESTS 1 - 10)
    // =========================================================================

    @Test
    fun test1_needFoodParcelsBhopalTomorrow_isOrder() {
        val result = parse("Need 2 food parcels in Bhopal tomorrow")
        assertEquals(MessageCategory.ORDER, result.classification)
        assertEquals(2, result.quantity)
        assertTrue(result.itemDescription?.contains("food parcel") == true)
        assertEquals("2026-08-26", result.due_date) // anchor + 1 day
        assertEquals("Bhopal", result.delivery_address)
    }

    @Test
    fun test2_multilineFormattedOrder_extractsAllFields() {
        val raw = """
            Nakul
            2 food parcels
            Location: Bhopal
            Delivery: 30 August 2026
            Amount: Rs 500
            COD
        """.trimIndent()
        val result = parse(raw)

        assertEquals(MessageCategory.ORDER, result.classification)
        assertEquals("Nakul", result.customer)
        assertEquals(2, result.quantity)
        assertEquals("food parcels", result.itemDescription)
        assertEquals("Bhopal", result.delivery_address)
        assertEquals("2026-08-30", result.due_date)
        assertEquals("30/08/2026", result.display_date)
        assertEquals(500.0, result.amount ?: 0.0, 0.01)
        assertEquals("COD", result.payment_method)
        assertFalse(result.needs_clarification)
    }

    @Test
    fun test3_bankAccountCredited_isBankNotOrder() {
        val result = parse("Your account has been credited with Rs 500")
        assertEquals(MessageCategory.BANK_FINANCIAL, result.classification)
        assertFalse(result.classification.isOrder)
        assertTrue(result.items.isEmpty())
        assertNull(result.customer)
        assertNull(result.due_date)
        assertTrue(result.needs_clarification)
    }

    @Test
    fun test4_otpNotification_isOtpNotOrder() {
        val result = parse("Your OTP is 482913")
        assertEquals(MessageCategory.OTP_AUTHENTICATION, result.classification)
        assertFalse(result.classification.isOrder)
        assertTrue(result.items.isEmpty())
        assertNull(result.customer)
        assertNull(result.quantity)
    }

    @Test
    fun test5_pdfDocumentWithPages_isDocumentNotOrder() {
        val raw = "DevCraft_Tech_Stack_Folders_Architecture_Test_Report_v3.pdf (8 pages)"
        val result = parse(raw)

        assertEquals(MessageCategory.DOCUMENT_FILE, result.classification)
        assertFalse(result.classification.isOrder)
        assertTrue(result.items.isEmpty())
        assertNull(result.customer)
        assertNull(result.quantity)
        assertNull(result.amount)
        assertNull(result.due_date)
    }

    @Test
    fun test6_deliveryConfirmation_isDeliveryTrackingNotOrder() {
        val result = parse("Your parcel has been delivered")
        assertEquals(MessageCategory.DELIVERY_TRACKING, result.classification)
        assertFalse(result.classification.isOrder)
        assertTrue(result.items.isEmpty())
    }

    @Test
    fun test7_personalGreeting_isPersonalNotOrder() {
        val result = parse("Hiii")
        assertEquals(MessageCategory.PERSONAL_MESSAGE, result.classification)
        assertFalse(result.classification.isOrder)
        assertTrue(result.items.isEmpty())
    }

    @Test
    fun test8_promotionalSale_isPromotionalNotOrder() {
        val result = parse("Get 50% off today")
        assertEquals(MessageCategory.PROMOTIONAL, result.classification)
        assertFalse(result.classification.isOrder)
        assertTrue(result.items.isEmpty())
    }

    @Test
    fun test9_orderWithForPattern_extractsCustomerAndDate() {
        val result = parse("Order 5 notebooks for Rahul, deliver to Bhopal on 31/08/2026")
        assertEquals(MessageCategory.ORDER, result.classification)
        assertEquals("Rahul", result.customer)
        assertEquals(5, result.quantity)
        assertTrue(result.itemDescription?.contains("notebook") == true)
        assertEquals("2026-08-31", result.due_date)
        assertEquals("31/08/2026", result.display_date)
        assertEquals("Bhopal", result.delivery_address)
    }

    @Test
    fun test10_bareAmountAndDateWithoutItem_needsClarificationNotAutoOrder() {
        val result = parse("amount 500, date 30/08/2026")
        assertTrue("ambiguous/incomplete message must need clarification", result.needs_clarification)
    }

    // =========================================================================
    // EXACT PROMPT SCENARIO
    // =========================================================================

    @Test
    fun testExactRequiredScenario_Nakul1000COD() {
        val raw = """
            Nakul
            2 food parcels
            Location: Bhopal
            Delivery: 30 August 2026
            Amount: Rs 1000
            COD
        """.trimIndent()
        val result = parse(raw)

        assertEquals(MessageCategory.ORDER, result.classification)
        assertEquals("Nakul", result.customer)
        assertEquals(2, result.quantity)
        assertEquals("food parcels", result.itemDescription)
        assertEquals("Bhopal", result.delivery_address)
        assertEquals("2026-08-30", result.due_date)
        assertEquals("30/08/2026", result.display_date)
        assertEquals(1000.0, result.amount ?: 0.0, 0.01)
        assertEquals("COD", result.payment_method)
        assertTrue("overall score should be high", result.overall_score >= 0.90f)
        assertFalse(result.needs_clarification)
    }

    // =========================================================================
    // ADVANCED REGRESSION (TESTS 11 - 20)
    // =========================================================================

    @Test
    fun test11_explicitDateExtraction_handlesMultipleFormats() {
        assertEquals("2026-08-30", parse("2 food parcels delivery 30 August 2026").due_date)
        assertEquals("2026-08-30", parse("2 food parcels delivery 30 Aug 2026").due_date)
        assertEquals("2026-08-30", parse("2 food parcels delivery 30/08/2026").due_date)
        assertEquals("2026-08-30", parse("2 food parcels delivery 30-08-2026").due_date)
        assertEquals("2026-08-30", parse("2 food parcels delivery 30.08.2026").due_date)
        assertEquals("2026-08-30", parse("2 food parcels delivery August 30, 2026").due_date)
        assertEquals("2026-08-30", parse("2 food parcels delivery 30th August 2026").due_date)
    }

    @Test
    fun test12_dateFormatConversion_canonicalIsoVsDisplay() {
        val result = parse("Nakul 2 food parcels 30 August 2026")
        assertEquals("2026-08-30", result.due_date)
        assertEquals("30/08/2026", result.display_date)
    }

    @Test
    fun test13_relativeDateResolution_usesAnchorTimestamp() {
        assertEquals("2026-08-25", parse("Nakul 2 parcels today").due_date)
        assertEquals("2026-08-26", parse("Nakul 2 parcels tomorrow").due_date)
        assertEquals("2026-08-26", parse("Nakul 2 parcels kal").due_date)
        assertEquals("2026-08-27", parse("Nakul 2 parcels parso").due_date)
        // 2026-08-25 is Tuesday, next Monday is 2026-08-31
        assertEquals("2026-08-31", parse("Nakul 2 parcels next Monday").due_date)
    }

    @Test
    fun test14_missingDate_isNotInvented() {
        val result = parse("Nakul 2 food parcels Bhopal")
        assertNull(result.due_date)
        assertNull(result.display_date)
        assertTrue(result.missing_fields.contains("Delivery date"))
    }

    @Test
    fun test15_customerNameExtraction_doesNotUsePdfOrFilenames() {
        val res1 = parse("Mohan ji ko 2 bori cement bhejo")
        assertEquals("Mohan", res1.customer)

        val res2 = parse("DevCraft_Test.pdf (5 pages)")
        assertNull(res2.customer)
    }

    @Test
    fun test16_quantityExtraction_neverExtractsYearsOrPinCodesOrPages() {
        val res = parse("Nakul 2 food parcels PIN 462001 year 2026")
        assertEquals(2, res.quantity)
        assertNotEquals(462001, res.quantity)
        assertNotEquals(2026, res.quantity)
    }

    @Test
    fun test17_amountExtraction_withCurrencySymbols() {
        assertEquals(500.0, parse("2 food parcels ₹500").amount ?: 0.0, 0.01)
        assertEquals(1250.0, parse("2 food parcels Rs. 1250").amount ?: 0.0, 0.01)
        assertEquals(750.0, parse("2 food parcels 750 rupees").amount ?: 0.0, 0.01)
    }

    @Test
    fun test18_addressExtraction_labelledAndKnownCities() {
        val labelled = parse("2 food parcels Location: MP Nagar Zone 1 Bhopal")
        assertEquals("MP Nagar Zone 1 Bhopal", labelled.delivery_address)

        val unlabelled = parse("Nakul 2 parcels Bhopal")
        assertEquals("Bhopal", unlabelled.delivery_address)
    }

    @Test
    fun test19_paymentMethodExtraction() {
        assertEquals("COD", parse("2 food parcels cash on delivery").payment_method)
        assertEquals("UPI", parse("2 food parcels payment via gpay").payment_method)
        assertEquals("ADVANCE", parse("2 food parcels paid in advance").payment_method)
        assertEquals("CASH", parse("2 food parcels cash").payment_method)
    }

    @Test
    fun test20_perOrderScoringMetrics() {
        val order = parse("Nakul 2 food parcels Location: Bhopal Delivery: 30 August 2026 Amount: Rs 1000 COD")
        assertEquals(0.97f, order.classification_score, 0.01f)
        assertTrue(order.field_extraction_score >= 0.90f)
        assertEquals(1.0f, order.date_resolution_score, 0.01f)
        assertEquals(1.0f, order.clarification_decision_score, 0.01f)
        assertTrue(order.overall_score >= 0.90f)
    }
}
