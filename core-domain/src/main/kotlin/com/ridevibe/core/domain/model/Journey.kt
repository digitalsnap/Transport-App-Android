package com.ridevibe.core.domain.model

/** One bookable segment of a multi-leg journey. */
data class JourneyLeg(
    val kind: RideKind,
    val from: String,
    val to: String,
    val durationMinutes: Long,
    val indicativeFarePhp: Double,
    /** Transfer tip shown under the leg (e.g. "Grab from NAIA to PITX ~20 min"). */
    val note: String? = null,
)

/**
 * A curated multi-leg route for travellers who don't know the archipelago —
 * e.g. Manila → Siargao overland: bus to Bicol, RoRo to Samar, bus through
 * Leyte, RoRo to Surigao, then a boat to Dapa. Each leg is searchable on its
 * own; fares/durations are indicative.
 */
data class Journey(
    val title: String,
    val from: String,
    val to: String,
    val legs: List<JourneyLeg>,
) {
    val totalFarePhp: Double get() = legs.sumOf { it.indicativeFarePhp }
    val totalDurationMinutes: Long get() = legs.sumOf { it.durationMinutes }
    val transferCount: Int get() = (legs.size - 1).coerceAtLeast(0)
}
