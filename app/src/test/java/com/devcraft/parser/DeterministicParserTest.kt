package com.devcraft.parser

import com.devcraft.parser.offline.DeterministicParser
import org.junit.Assert.*
import org.junit.Test

class DeterministicParserTest {

    @Test
    fun testHinglishKurtaOrder() {
        val input = "bhaiya 2 kurta chahiye navy blue chest 40 parso tak"
        val result = DeterministicParser.parse(input)

        assertEquals(1, result.items.size)
        assertEquals(2, result.items[0].quantity)
        assertEquals("navy blue", result.items[0].attributes["color"])
        assertEquals("40", result.items[0].attributes["size"])
        assertNotNull(result.due_date)
        assertFalse(result.references_prior_order)
    }

    @Test
    fun testPriorOrderReference() {
        val input = "Ramesh bhai wahi purana order kal tak bhej do"
        val result = DeterministicParser.parse(input)

        assertEquals("Ramesh", result.customer)
        assertTrue(result.references_prior_order)
        assertNotNull(result.due_date)
    }

    @Test
    fun testDevanagariHindiNumber() {
        val input = "रमेश भाई को ५ बोरी सीमेंट चाहिए"
        val result = DeterministicParser.parse(input)

        assertEquals("रमेश", result.customer)
        assertEquals(5, result.items[0].quantity)
    }

    // --- regressions: the old parser matched raw substrings, not tokens ---

    @Test
    fun testMultiDigitQuantityNotTruncated() {
        // "10" used to score 1 because the text contains the substring "1"
        val result = DeterministicParser.parse("Ramesh bhaiya ko kal 10 bori cement bhejo")
        assertEquals(10, result.items[0].quantity)
    }

    @Test
    fun testAmountDigitsAreNotReadAsQuantity() {
        // "Rs 2500" used to score quantity 2
        val result = DeterministicParser.parse("Mohan ji 5 chairs send tomorrow Rs 2500")
        assertEquals(5, result.items[0].quantity)
        assertEquals(2500.0, result.amount ?: 0.0, 0.01)
    }

    @Test
    fun testMeasurementIsNotReadAsQuantity() {
        val result = DeterministicParser.parse("bhaiya 2 kurta chest 40 chahiye")
        assertEquals(2, result.items[0].quantity)
        assertEquals("40", result.items[0].attributes["size"])
    }

    @Test
    fun testDevanagariDateKeywords() {
        assertNotNull(DeterministicParser.parse("सुरेश भाई को ३ पैकेट आज चाहिए").due_date)
        assertNotNull(DeterministicParser.parse("सुरेश भाई को ३ पैकेट कल चाहिए").due_date)
        assertNotNull(DeterministicParser.parse("सुरेश भाई को ३ पैकेट परसों चाहिए").due_date)
    }

    @Test
    fun testDateKeywordRespectsWordBoundary() {
        // "kalash" contains "kal" but is not a date
        assertNull(DeterministicParser.parse("Ramesh bhai 2 kalash bhejna").due_date)
    }

    @Test
    fun testSuffixedAmount() {
        val result = DeterministicParser.parse("Ramesh bhai 4 shirts 3500 rupees")
        assertEquals(3500.0, result.amount ?: 0.0, 0.01)
        assertEquals(4, result.items[0].quantity)
    }

    @Test
    fun testCurrencyPrefixRequiresWordBoundary() {
        // "cars 500" must not match the "rs" currency token inside "cars"
        assertNull(DeterministicParser.parse("Ramesh bhai 2 cars 500").amount)
    }

    @Test
    fun testMissingQuantityTriggersClarification() {
        val result = DeterministicParser.parse("kuch samaan bhej do")
        assertNull(result.customer)
        assertTrue(result.confidence < 0.7f)
        assertTrue(result.needs_clarification)
    }

    @Test
    fun testItemDescriptionExcludesNoiseTokens() {
        val result = DeterministicParser.parse("Ramesh bhaiya ko kal shaam 10 bori cement bhejo Rs 3500")
        val desc = result.items[0].description
        assertEquals("bori cement", desc)
    }

    @Test
    fun testHindiCompoundQuantity() {
        assertEquals(200, DeterministicParser.parse("Ramesh bhai do sau bori cement").items[0].quantity)
    }

    @Test
    fun testEmptyMessageIsFlagged() {
        val result = DeterministicParser.parse("   ")
        assertEquals(0.0f, result.confidence, 0.001f)
        assertTrue(result.needs_clarification)
    }
}
