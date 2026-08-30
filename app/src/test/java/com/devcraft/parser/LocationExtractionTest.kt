package com.devcraft.parser

import com.devcraft.parser.offline.DeterministicParser
import org.junit.Assert.*
import org.junit.Test

/**
 * Address, PIN code and phone extraction. All offline text parsing - no
 * geocoding, so none of this needs an API key.
 */
class LocationExtractionTest {

    @Test
    fun extractsCommaSeparatedIndianAddress() {
        val result = DeterministicParser.parse(
            "Ramesh bhai 10 bori cement, Shop 4 MG Road Indore 452001, kal tak"
        )
        val address = result.delivery_address
        assertNotNull("address should be found", address)
        assertTrue("should contain the shop clause: $address", address!!.contains("Shop 4"))
        assertEquals("452001", result.pincode)
    }

    @Test
    fun pincodeIsNotMistakenForQuantity() {
        // The old numeric scan would have grabbed 452001 as the quantity.
        val result = DeterministicParser.parse(
            "Ramesh bhai 10 bori cement, MG Road 452001"
        )
        assertEquals(10, result.items[0].quantity)
        assertEquals("452001", result.pincode)
    }

    @Test
    fun phoneIsNotMistakenForQuantityOrAmount() {
        val result = DeterministicParser.parse(
            "Ramesh bhai 5 kurta, contact 9876543210, Rs 2500"
        )
        assertEquals("9876543210", result.phone)
        assertEquals(5, result.items[0].quantity)
        assertEquals(2500.0, result.amount ?: 0.0, 0.01)
    }

    @Test
    fun acceptsPhoneWithCountryCode() {
        val result = DeterministicParser.parse("Mohan ji 2 chairs, +91 9812345678")
        assertEquals("9812345678", result.phone)
    }

    @Test
    fun recognisesHindiAddressVocabulary() {
        val result = DeterministicParser.parse(
            "सुरेश भाई को ३ पैकेट, गांधी नगर, इंदौर 452003"
        )
        assertNotNull(result.delivery_address)
        assertEquals("452003", result.pincode)
    }

    @Test
    fun recognisesNearLandmarkClause() {
        val result = DeterministicParser.parse(
            "Rahul bhai 6 shirts, near Bombay Hospital, Indore"
        )
        assertNotNull(result.delivery_address)
        assertTrue(result.delivery_address!!.contains("near", ignoreCase = true))
    }

    @Test
    fun noAddressMeansNullNotAGuess() {
        val result = DeterministicParser.parse("Ramesh bhaiya ko kal 10 bori cement bhejo")
        assertNull("must not invent an address", result.delivery_address)
        assertNull(result.pincode)
        assertNull(result.phone)
        assertFalse(result.hasLocation)
    }

    @Test
    fun addressWordsDoNotLeakIntoItemDescription() {
        val result = DeterministicParser.parse(
            "Ramesh bhai 10 bori cement, Shop 4 MG Road Indore 452001"
        )
        val desc = result.items[0].description
        assertFalse("description leaked the address: $desc", desc.contains("road", ignoreCase = true))
        assertFalse("description leaked the address: $desc", desc.contains("shop", ignoreCase = true))
        assertTrue("description should still name the goods: $desc", desc.contains("cement"))
    }

    @Test
    fun hasLocationReflectsWhatWasFound() {
        assertTrue(DeterministicParser.parse("2 bori, Sector 7 Bhopal 462001").hasLocation)
        assertFalse(DeterministicParser.parse("2 bori cement chahiye").hasLocation)
    }

    @Test
    fun sixDigitAmountIsNotTreatedAsPincode() {
        // "Rs 452001" is money, not a PIN code
        val result = DeterministicParser.parse("Ramesh bhai 3 bori Rs 452001")
        assertEquals(452001.0, result.amount ?: 0.0, 0.01)
        assertNull(result.pincode)
    }
}
