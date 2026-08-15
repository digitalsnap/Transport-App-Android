package com.ridevibe.core.domain.repository

import com.ridevibe.core.domain.model.Itinerary
import com.ridevibe.core.domain.model.Journey

interface ItineraryRepository {
    suspend fun getItineraries(): List<Itinerary>

    /** Saves [journey] for [startDateMillis]; returns the existing plan if already saved. */
    suspend fun addItinerary(journey: Journey, startDateMillis: Long): Itinerary

    suspend fun setLegDone(itineraryId: String, legIndex: Int, done: Boolean)

    suspend fun removeItinerary(itineraryId: String)
}
