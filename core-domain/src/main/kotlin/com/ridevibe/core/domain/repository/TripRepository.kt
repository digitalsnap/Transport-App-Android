package com.ridevibe.core.domain.repository

import com.ridevibe.core.domain.model.BusClass
import com.ridevibe.core.domain.model.Trip

interface TripRepository {
    suspend fun searchTrips(
        origin: String,
        destination: String,
        departureDateEpochMillis: Long,
        busClass: BusClass? = null,
    ): Result<List<Trip>>
}
