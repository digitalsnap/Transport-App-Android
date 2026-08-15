package com.ridevibe.core.domain.usecase

import com.ridevibe.core.domain.model.Trip
import com.ridevibe.core.domain.repository.TripRepository
import javax.inject.Inject

/** Cross-mode destination search: buses, ferries, and fastcrafts related to a query. */
class SearchRelatedTripsUseCase @Inject constructor(
    private val tripRepository: TripRepository,
) {
    suspend operator fun invoke(
        query: String,
        departureDateEpochMillis: Long,
        returnDateEpochMillis: Long? = null,
    ): Result<List<Trip>> =
        tripRepository.searchRelated(query, departureDateEpochMillis, returnDateEpochMillis)
}
