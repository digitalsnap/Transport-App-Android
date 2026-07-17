package com.ridevibe.core.domain.repository

import com.ridevibe.core.domain.model.BusClass
import com.ridevibe.core.domain.model.TerminalLocation
import com.ridevibe.core.domain.model.Trip

interface TripRepository {
    suspend fun searchTrips(
        origin: String,
        destination: String,
        departureDateEpochMillis: Long,
        busClass: BusClass? = null,
    ): Result<List<Trip>>

    suspend fun getTrip(tripId: String): Result<Trip>

    /** Selectable origins/destinations, central terminals flagged. */
    suspend fun getLocations(): List<TerminalLocation>

    /** Bus classes that actually exist in inventory — drives the filter chips. */
    suspend fun getAvailableBusClasses(): List<BusClass>
}
