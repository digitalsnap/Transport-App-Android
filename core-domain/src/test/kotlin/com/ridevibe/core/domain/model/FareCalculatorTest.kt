package com.ridevibe.core.domain.model

import org.junit.Test
import kotlin.test.assertEquals

class FareCalculatorTest {

    @Test
    fun `regular passengers pay the full fare`() {
        val quote = FareCalculator.quote(485.0, listOf(PassengerType.REGULAR, PassengerType.REGULAR))

        assertEquals(970.0, quote.subtotalPhp)
        assertEquals(0.0, quote.discountPhp)
        assertEquals(970.0, quote.totalPhp)
        assertEquals(1, quote.legs.size)
        assertEquals(2, quote.legs[0].seatCount)
    }

    @Test
    fun `discounted types take twenty percent off their own seat only`() {
        val quote = FareCalculator.quote(
            485.0,
            listOf(PassengerType.REGULAR, PassengerType.STUDENT, PassengerType.SENIOR_CITIZEN, PassengerType.PWD),
        )

        assertEquals(1940.0, quote.subtotalPhp)
        assertEquals(291.0, quote.discountPhp) // 3 × 97.00
        assertEquals(1649.0, quote.totalPhp)
    }

    @Test
    fun `round trip quotes each leg at its own fare`() {
        val quote = FareCalculator.quote(
            farePerSeatPhp = 485.0,
            passengers = listOf(PassengerType.STUDENT),
            returnFarePerSeatPhp = 500.0,
        )

        assertEquals(2, quote.legs.size)
        assertEquals(485.0, quote.legs[0].subtotalPhp)
        assertEquals(97.0, quote.legs[0].discountPhp)
        assertEquals(500.0, quote.legs[1].subtotalPhp)
        assertEquals(100.0, quote.legs[1].discountPhp)
        assertEquals(985.0, quote.subtotalPhp)
        assertEquals(197.0, quote.discountPhp)
        assertEquals(788.0, quote.totalPhp)
    }

    @Test
    fun `amounts are rounded to centavos and still add up`() {
        // 20% of 333.33 is 66.666 → 66.67; total must be 333.33 − 66.67, not the raw double.
        val quote = FareCalculator.quote(333.33, listOf(PassengerType.PWD))

        assertEquals(333.33, quote.subtotalPhp)
        assertEquals(66.67, quote.discountPhp)
        assertEquals(266.66, quote.totalPhp)
        assertEquals(quote.subtotalPhp - quote.discountPhp, quote.totalPhp, 0.0000001)
    }

    @Test
    fun `no passengers yields a zero quote`() {
        val quote = FareCalculator.quote(485.0, emptyList())
        assertEquals(0.0, quote.totalPhp)
        assertEquals(0, quote.legs[0].seatCount)
    }

    @Test
    fun `roundCentavos rounds half up`() {
        assertEquals(0.01, FareCalculator.roundCentavos(0.005))
        assertEquals(1.23, FareCalculator.roundCentavos(1.234))
    }
}
