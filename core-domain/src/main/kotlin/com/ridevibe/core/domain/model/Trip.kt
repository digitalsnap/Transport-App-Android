package com.ridevibe.core.domain.model

/**
 * The transport mode operating a trip — buses, RoRo ferries, or fastcrafts.
 *
 * [sellsPassage] is the product rule that splits the booking flow: buses sell
 * chosen seats through the seat map, while ferries and fastcrafts sell passage
 * — the app auto-assigns one space per passenger (`P1…Pn`) and never shows a
 * seat map (fastcraft seating is allocated at the port).
 */
enum class RideKind(val sellsPassage: Boolean) {
    BUS(sellsPassage = false),
    FERRY(sellsPassage = true),
    FASTCRAFT(sellsPassage = true);

    /** True when the passenger must pick seats on a seat map before checkout. */
    val requiresSeatSelection: Boolean get() = !sellsPassage
}

/** "seat"/"seats" for buses, "space"/"spaces" for sea passage — the word riders see everywhere. */
fun RideKind.spaceNoun(plural: Boolean = false): String = when {
    sellsPassage -> if (plural) "spaces" else "space"
    else -> if (plural) "seats" else "seat"
}

data class Trip(
    val id: String,
    val operatorName: String,
    val origin: String,
    val destination: String,
    val departureEpochMillis: Long,
    val arrivalEpochMillis: Long,
    val busClass: BusClass,
    val farePhp: Double,
    val availableSeatCount: Int,
    val operatorRating: Double? = null,
    val rideKind: RideKind = RideKind.BUS,
)

enum class BusClass {
    ORDINARY,
    DELUXE,
    LUXURY,
}

/**
 * The class name riders see. Buses use the regulated bus classes; sea services
 * use industry accommodation names (Tourist/Business/Premium), since ferries
 * sell passage or berths rather than a bus class. Centralised here so Results,
 * Checkout and Ticket agree.
 */
fun BusClass.displayLabel(rideKind: RideKind): String = when (rideKind) {
    RideKind.BUS -> when (this) {
        BusClass.ORDINARY -> "Ordinary"
        BusClass.DELUXE -> "Deluxe"
        BusClass.LUXURY -> "Luxury"
    }
    RideKind.FERRY, RideKind.FASTCRAFT -> when (this) {
        BusClass.ORDINARY -> "Tourist Class"
        BusClass.DELUXE -> "Business Class"
        BusClass.LUXURY -> "Premium Class"
    }
}
