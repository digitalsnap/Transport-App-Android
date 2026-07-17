package com.ridevibe.core.network

import com.ridevibe.core.domain.model.BusClass
import com.ridevibe.core.domain.model.TerminalLocation
import com.ridevibe.core.domain.model.Trip
import com.ridevibe.core.domain.repository.TripRepository
import com.ridevibe.core.network.api.CrsApiService
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class TripRepositoryImpl @Inject constructor(
    private val apiService: CrsApiService,
) : TripRepository {

    override suspend fun searchTrips(
        origin: String,
        destination: String,
        departureDateEpochMillis: Long,
        busClass: BusClass?,
    ): Result<List<Trip>> = runCatching {
        apiService.searchTrips(origin, destination, departureDateEpochMillis, busClass?.name)
            .map { it.toDomain() }
    }

    override suspend fun getTrip(tripId: String): Result<Trip> =
        runCatching { apiService.getTrip(tripId).toDomain() }

    override suspend fun getLocations(): List<TerminalLocation> =
        runCatching { apiService.getLocations().map { it.toDomain() } }.getOrDefault(emptyList())

    override suspend fun getAvailableBusClasses(): List<BusClass> =
        runCatching { apiService.getBusClasses().map { BusClass.valueOf(it.uppercase()) } }
            .getOrDefault(BusClass.entries.toList())
}
