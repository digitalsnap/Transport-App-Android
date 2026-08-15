package com.ridevibe.core.domain.model

/** What kind of transport hub a location is — lets the service selector filter. */
enum class LocationKind { BUS_TERMINAL, SEAPORT, CITY }

/**
 * A pick-up/drop-off point selectable in trip search. Central terminals
 * (Cubao, Pasay, PITX) are highlighted first in the location picker.
 * [kind] separates integrated bus terminals from ferry/fastcraft seaports;
 * plain [CITY][LocationKind.CITY] entries are route endpoints without hub data.
 */
data class TerminalLocation(
    val name: String,
    val isCentralTerminal: Boolean = false,
    val kind: LocationKind = LocationKind.CITY,
    /** e.g. "Metro Manila & Luzon", "Visayas", "Mindanao". */
    val region: String = "",
    /** One-line description of what the hub serves. */
    val description: String = "",
)

enum class TripType { ONE_WAY, ROUND_TRIP }
