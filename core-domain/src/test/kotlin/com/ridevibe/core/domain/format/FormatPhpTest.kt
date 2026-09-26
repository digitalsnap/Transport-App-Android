package com.ridevibe.core.domain.format

import org.junit.Test
import kotlin.test.assertEquals

class FormatPhpTest {

    @Test
    fun `groups thousands and shows centavos by default`() {
        assertEquals("₱1,234.00", formatPhp(1234.0))
        assertEquals("₱1,234.50", formatPhp(1234.5))
        assertEquals("₱0.00", formatPhp(0.0))
    }

    @Test
    fun `can hide centavos for whole-peso displays`() {
        assertEquals("₱1,234", formatPhp(1234.0, showCentavos = false))
        assertEquals("₱1,235", formatPhp(1234.5, showCentavos = false))
    }

    @Test
    fun `negative amounts keep the minus in front of the peso sign`() {
        assertEquals("-₱485.00", formatPhp(-485.0))
    }

    @Test
    fun `uses the real peso sign`() {
        assertEquals('₱', formatPhp(1.0).first())
        assertEquals("₱", PESO_SIGN)
    }
}
