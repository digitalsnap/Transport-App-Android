package com.ridevibe.core.domain.model

/**
 * A traveller's saved plan: a multi-leg [Journey] pinned to a start date, with
 * per-leg completion so tourists can tick off segments as they go.
 */
data class Itinerary(
    val id: String,
    val journey: Journey,
    val startDateMillis: Long,
    val doneLegIndices: Set<Int> = emptySet(),
) {
    /** Only indices that name a real leg count — a stale index from an edited journey must not. */
    val completedCount: Int get() = doneLegIndices.count { it in journey.legs.indices }

    /** Every leg ticked, and at least one leg to tick — an empty journey is never "complete". */
    val isComplete: Boolean
        get() = journey.legs.isNotEmpty() && completedCount == journey.legs.size
}
