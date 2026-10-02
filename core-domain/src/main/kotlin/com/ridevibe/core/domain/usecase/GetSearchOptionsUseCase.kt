package com.ridevibe.core.domain.usecase

import com.ridevibe.core.domain.model.BusClass
import com.ridevibe.core.domain.model.TerminalLocation
import com.ridevibe.core.domain.repository.TripRepository
import javax.inject.Inject

/** Static-ish reference data the search form needs: locations and bus classes. */
class GetSearchOptionsUseCase @Inject constructor(
    private val tripRepository: TripRepository,
) {
    suspend fun locations(): Result<List<TerminalLocation>> = tripRepository.getLocations()
    suspend fun busClasses(): Result<List<BusClass>> = tripRepository.getAvailableBusClasses()
}
