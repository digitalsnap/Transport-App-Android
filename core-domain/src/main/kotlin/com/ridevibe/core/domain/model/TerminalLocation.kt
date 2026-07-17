package com.ridevibe.core.domain.model

/**
 * A pick-up/drop-off point selectable in trip search. Central terminals
 * (Cubao, Pasay, PITX) are highlighted first in the location picker.
 */
data class TerminalLocation(
    val name: String,
    val isCentralTerminal: Boolean = false,
)

enum class TripType { ONE_WAY, ROUND_TRIP }
