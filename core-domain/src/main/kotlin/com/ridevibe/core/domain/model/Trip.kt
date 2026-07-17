package com.ridevibe.core.domain.model

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
)

enum class BusClass {
    ORDINARY,
    DELUXE,
    LUXURY,
}
