package com.ridevibe.core.network

import com.ridevibe.core.domain.model.BusClass
import com.ridevibe.core.domain.model.Journey
import com.ridevibe.core.domain.model.TerminalLocation
import com.ridevibe.core.domain.model.Trip
import com.ridevibe.core.domain.repository.TripRepository
import com.ridevibe.core.network.api.CrsApiService
import com.ridevibe.core.network.api.passengerApiResult
import com.ridevibe.core.network.dto.toBusClass
import javax.inject.Inject
import javax.inject.Singleton

/**
 * `/v1` catalog and search. Every call goes through [passengerApiResult] so a
 * server `{message}`, a dead connection (code 0) and cancellation are handled
 * the same way the seat and checkout repositories handle them.
 */
@Singleton
class TripRepositoryImpl @Inject constructor(
    private val apiService: CrsApiService,
) : TripRepository {

    override suspend fun searchTrips(
        origin: String,
        destination: String,
        departureDateEpochMillis: Long,
        busClass: BusClass?,
    ): Result<List<Trip>> = passengerApiResult {
        apiService.searchTrips(origin, destination, departureDateEpochMillis, busClass?.name)
            .map { it.toDomain() }
    }

    override suspend fun getTrip(tripId: String): Result<Trip> =
        passengerApiResult { apiService.getTrip(tripId).toDomain() }

    override suspend fun searchRelated(
        query: String,
        departureDateEpochMillis: Long,
        returnDateEpochMillis: Long?,
    ): Result<List<Trip>> = passengerApiResult {
        apiService.searchRelatedTrips(query, departureDateEpochMillis, returnDateEpochMillis).map { it.toDomain() }
    }

    override suspend fun findJourneys(query: String): Result<List<Journey>> =
        passengerApiResult { apiService.findJourneys(query).map { it.toDomain() } }

    override suspend fun getLocations(): Result<List<TerminalLocation>> =
        passengerApiResult { apiService.getLocations().map { it.toDomain() } }

    // Lenient: an unknown class name collapses to ORDINARY (and is logged) rather than crashing Home.
    override suspend fun getAvailableBusClasses(): Result<List<BusClass>> =
        passengerApiResult { apiService.getBusClasses().map { it.toBusClass() }.distinct().sorted() }
}
