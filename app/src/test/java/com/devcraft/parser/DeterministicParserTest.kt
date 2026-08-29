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
}
