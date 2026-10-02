package com.ridevibe.core.domain.model

/** One leg's share of a quote, in pesos rounded to centavos. */
data class LegQuote(
    val farePerSeatPhp: Double,
    val seatCount: Int,
    val subtotalPhp: Double,
    val discountPhp: Double,
    val totalPhp: Double,
)

/** The itemised amount a party pays; [legs] has one entry per leg (outbound, then return). */
data class FareQuote(
    val subtotalPhp: Double,
    val discountPhp: Double,
    val totalPhp: Double,
    val legs: List<LegQuote>,
)

/**
 * Applies the regulated fare discount per passenger, per leg. Every amount is
 * rounded to centavos so the itemised lines add up to the total on screen and
 * on the ticket — never to the raw double.
 */
object FareCalculator {

    /**
     * [passengers] is one entry per seat-taking passenger (adults and children;
     * infants pay nothing and are not listed). [returnFarePerSeatPhp] adds a
     * second leg for the same party.
     */
    fun quote(
        farePerSeatPhp: Double,
        passengers: List<PassengerType>,
        returnFarePerSeatPhp: Double? = null,
    ): FareQuote {
        val legs = listOfNotNull(farePerSeatPhp, returnFarePerSeatPhp).map { fare -> legQuote(fare, passengers) }
        return FareQuote(
            subtotalPhp = roundCentavos(legs.sumOf { it.subtotalPhp }),
            discountPhp = roundCentavos(legs.sumOf { it.discountPhp }),
            totalPhp = roundCentavos(legs.sumOf { it.totalPhp }),
            legs = legs,
        )
    }

    private fun legQuote(farePerSeatPhp: Double, passengers: List<PassengerType>): LegQuote {
        val subtotal = roundCentavos(farePerSeatPhp * passengers.size)
        val discount = roundCentavos(passengers.sumOf { roundCentavos(farePerSeatPhp * it.discountRate) })
        return LegQuote(
            farePerSeatPhp = farePerSeatPhp,
            seatCount = passengers.size,
            subtotalPhp = subtotal,
            discountPhp = discount,
            totalPhp = roundCentavos(subtotal - discount),
        )
    }

    /** Pesos are settled to the centavo; half a centavo rounds up like a cashier would. */
    fun roundCentavos(amount: Double): Double = Math.round(amount * 100) / 100.0
}
