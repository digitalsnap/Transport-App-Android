package com.ridevibe.core.domain.model

import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class PartyRulesTest {

    @Test
    fun `a lone adult is bookable`() {
        assertNull(PartyRules.validate(adults = 1, children = 0, infants = 0))
    }

    @Test
    fun `no adult is refused even with children`() {
        assertEquals(PartyError.NO_ADULT, PartyRules.validate(adults = 0, children = 2, infants = 0))
    }

    @Test
    fun `adults plus children may fill the hold cap but not exceed it`() {
        assertNull(PartyRules.validate(adults = 6, children = 6, infants = 0))
        assertEquals(PartyError.TOO_MANY_SEATS, PartyRules.validate(adults = 7, children = 6, infants = 0))
    }

    @Test
    fun `infants do not take seats`() {
        assertNull(PartyRules.validate(adults = 12, children = 0, infants = 5))
    }

    @Test
    fun `more than five infants is refused`() {
        assertEquals(PartyError.TOO_MANY_INFANTS, PartyRules.validate(adults = 12, children = 0, infants = 6))
    }

    @Test
    fun `each infant needs an adult lap`() {
        assertEquals(PartyError.INFANTS_EXCEED_ADULTS, PartyRules.validate(adults = 1, children = 0, infants = 2))
        assertNull(PartyRules.validate(adults = 2, children = 0, infants = 2))
    }

    @Test
    fun `every error carries rider-facing copy`() {
        PartyError.values().forEach { assertTrue(it.message.isNotBlank(), "${it.name} has no message") }
    }
}
