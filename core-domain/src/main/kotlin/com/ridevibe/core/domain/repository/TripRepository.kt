package com.ridevibe.core.domain.repository

import com.ridevibe.core.domain.model.BusClass
import com.ridevibe.core.domain.model.Journey
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

    /**
     * Free-text destination search across every ride kind: buses heading to
     * matching places plus ferry/fastcraft sailings touching matching ports,
     * on [departureDateEpochMillis]. A non-null [returnDateEpochMillis] also
     * includes reverse-direction trips on that date (round trip).
     */
    suspend fun searchRelated(
        query: String,
        departureDateEpochMillis: Long,
        returnDateEpochMillis: Long? = null,
    ): Result<List<Trip>>

    /** Curated multi-leg tourist routes whose destination matches [query]. */
    suspend fun findJourneys(query: String): Result<List<Journey>>

    /** Selectable origins/destinations, central terminals flagged. */
    suspend fun getLocations(): List<TerminalLocation>

    /** Bus classes that actually exist in inventory — drives the filter chips. */
    suspend fun getAvailableBusClasses(): List<BusClass>
}
