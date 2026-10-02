package com.ridevibe.core.network

import com.ridevibe.core.domain.model.AllocatedSeat
import com.ridevibe.core.domain.model.ExtraTripCreated
import com.ridevibe.core.domain.model.ManifestEntry
import com.ridevibe.core.domain.model.NewService
import com.ridevibe.core.domain.model.OnsiteSaleIssued
import com.ridevibe.core.domain.model.OnsiteSaleRequest
import com.ridevibe.core.domain.model.OperatorService
import com.ridevibe.core.domain.model.PartnerOverview
import com.ridevibe.core.domain.model.ServiceChanged
import com.ridevibe.core.domain.model.ServiceCreated
import com.ridevibe.core.domain.model.ServiceUpdate
import com.ridevibe.core.domain.model.TripOccupancy
import com.ridevibe.core.domain.repository.PartnerRepository
import com.ridevibe.core.network.api.CrsApiException
import com.ridevibe.core.network.api.StaffApiService
import com.ridevibe.core.network.api.apiResult
import com.ridevibe.core.network.dto.CreateServiceRequestDto
import com.ridevibe.core.network.dto.ExtraTripRequestDto
import com.ridevibe.core.network.dto.OnsiteSaleRequestDto
import com.ridevibe.core.network.dto.UpdateServiceRequestDto
import javax.inject.Inject
import javax.inject.Singleton

/**
 * `/partner/api/…`. A PARTNER session acts for its own operator (the server
 * ignores `operatorId`); an ADMIN session passes `operatorId` and every write
 * comes back 403, surfaced here as a failed Result with the server's message.
 */
@Singleton
class PartnerRepositoryImpl @Inject constructor(
    private val api: StaffApiService,
) : PartnerRepository {

    override suspend fun getOverview(operatorId: Int?): Result<PartnerOverview> =
        apiResult { api.partnerMe(operatorId).toDomain() }

    override suspend fun getServices(operatorId: Int?): Result<List<OperatorService>> =
        apiResult { api.partnerServices(operatorId).map { it.toDomain() } }

    override suspend fun createService(service: NewService): Result<ServiceCreated> =
        apiResult { api.partnerCreateService(CreateServiceRequestDto.from(service)).toDomain() }

    override suspend fun updateService(serviceId: Int, update: ServiceUpdate): Result<ServiceChanged> {
        // Mirrors the server's "nothing to update" 400 without a round trip.
        if (update.isEmpty) return Result.failure(CrsApiException(400, "Nothing to update"))
        return apiResult { api.partnerUpdateService(serviceId, UpdateServiceRequestDto.from(update)).toDomain() }
    }

    override suspend fun addExtraTrip(serviceId: Int, dateIso: String, timeHm: String): Result<ExtraTripCreated> =
        apiResult {
            api.partnerAddExtraTrip(
                ExtraTripRequestDto(serviceId = serviceId, date = dateIso.trim(), time = timeHm.trim()),
            ).toDomain()
        }

    override suspend fun getTrips(dateIso: String?, operatorId: Int?): Result<List<TripOccupancy>> =
        apiResult { api.partnerTrips(date = dateIso?.takeIf { it.isNotBlank() }, operatorId = operatorId).map { it.toDomain() } }

    override suspend fun getTripSeats(tripId: String, operatorId: Int?): Result<List<AllocatedSeat>> =
        apiResult { api.partnerTripSeats(tripId, operatorId).map { it.toDomain() } }

    override suspend fun recordOnsiteSale(tripId: String, sale: OnsiteSaleRequest): Result<OnsiteSaleIssued> =
        apiResult { api.partnerOnsiteSale(tripId, OnsiteSaleRequestDto.from(sale)).toDomain() }

    override suspend fun getManifest(dateIso: String?, operatorId: Int?): Result<List<ManifestEntry>> =
        apiResult { api.partnerManifest(date = dateIso?.takeIf { it.isNotBlank() }, operatorId = operatorId).map { it.toDomain() } }
}
