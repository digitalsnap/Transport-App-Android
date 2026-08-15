package com.ridevibe.core.domain.model

/** The transport mode operating a trip — buses, RoRo ferries, or fastcrafts. */
enum class RideKind { BUS, FERRY, FASTCRAFT }

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
