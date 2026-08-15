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
    val completedCount: Int get() = doneLegIndices.count { it in journey.legs.indices }
    val isComplete: Boolean get() = completedCount == journey.legs.size
}
