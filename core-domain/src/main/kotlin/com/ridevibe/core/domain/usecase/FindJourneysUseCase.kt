package com.ridevibe.core.domain.usecase

import com.ridevibe.core.domain.model.Journey
import com.ridevibe.core.domain.repository.TripRepository
import javax.inject.Inject

/** Curated multi-leg tourist routes (e.g. Manila → Siargao without flying). */
class FindJourneysUseCase @Inject constructor(
    private val tripRepository: TripRepository,
) {
    suspend operator fun invoke(query: String): Result<List<Journey>> =
        tripRepository.findJourneys(query)
}
