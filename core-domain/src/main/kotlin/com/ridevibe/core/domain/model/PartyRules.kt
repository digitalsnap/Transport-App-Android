package com.ridevibe.core.domain.model

/**
 * Who can travel together on one booking. Adults and children each take a
 * seat (or a space on sea passage); infants ride on a lap and take none.
 */
object PartyRules {
    /**
     * The CRS caps active holds per user at 12 (409 beyond that — see
     * `x-open-questions.hold-semantics` in docs/api/openapi.yaml), so a party
     * larger than that could never hold all its seats at once.
     */
    const val MAX_SEATS_PER_BOOKING = 12

    /** Lap-held infants; one per lap is the realistic ceiling, five is the hard one. */
    const val MAX_INFANTS = 5

    /** Seats a party needs: infants sit on laps. */
    fun seatsFor(adults: Int, children: Int): Int = adults + children

    /** Null when the party is bookable; otherwise the first rule it breaks. */
    fun validate(adults: Int, children: Int, infants: Int): PartyError? = when {
        adults < 1 -> PartyError.NO_ADULT
        seatsFor(adults, children) > MAX_SEATS_PER_BOOKING -> PartyError.TOO_MANY_SEATS
        infants > MAX_INFANTS -> PartyError.TOO_MANY_INFANTS
        infants > adults -> PartyError.INFANTS_EXCEED_ADULTS
        else -> null
    }
}

enum class PartyError(val message: String) {
    NO_ADULT("At least one adult must travel."),
    TOO_MANY_SEATS("Up to ${PartyRules.MAX_SEATS_PER_BOOKING} seats per booking. Book the rest separately."),
    TOO_MANY_INFANTS("Up to ${PartyRules.MAX_INFANTS} infants per booking."),
    INFANTS_EXCEED_ADULTS("Each infant needs an adult's lap. Add an adult or book a seat for the child."),
}
