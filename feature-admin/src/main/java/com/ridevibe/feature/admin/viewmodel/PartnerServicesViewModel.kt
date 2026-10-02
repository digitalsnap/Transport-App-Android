package com.ridevibe.feature.admin.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ridevibe.core.domain.model.BusClass
import com.ridevibe.core.domain.model.NewService
import com.ridevibe.core.domain.model.OperatorService
import com.ridevibe.core.domain.model.RideKind
import com.ridevibe.core.domain.model.ServiceUpdate
import com.ridevibe.core.domain.repository.PartnerRepository
import com.ridevibe.core.domain.repository.TripRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

private const val MIN_DURATION_MINUTES = 15

/** What a ferry or fastcraft sails with when the service does not say — a caption tells the operator it is a default. */
private const val DEFAULT_SEA_CAPACITY = 200

data class PartnerServicesUiState(
    val isLoading: Boolean = true,
    val error: String? = null,
    val services: List<OperatorService> = emptyList(),
    /** Create or edit in flight. */
    val isSaving: Boolean = false,
    /** Error from validation or the last create/edit call, shown inline on the form. */
    val formError: String? = null,
    /** Service open in the edit sheet (fare / hours / duration only). */
    val editing: OperatorService? = null,
    /** Increments after a successful create so the add form can reset itself. */
    val createdCount: Int = 0,
    /** Terminal and port names the origin/destination pickers offer; operators cannot type new places. */
    val locations: List<String> = emptyList(),
    val isLocationsLoading: Boolean = false,
    val locationsError: String? = null,
    /** An edit that drops departure hours waits here for the operator's confirmation. */
    val pendingUpdate: ServiceUpdate? = null,
    val pendingRemovedHours: List<Int> = emptyList(),
    val message: String? = null,
)

/**
 * "My services": what the operator runs; creates services and edits fare/hours/duration.
 * Validation and the update diff live here so the form composables stay dumb.
 */
@HiltViewModel
class PartnerServicesViewModel @Inject constructor(
    private val partnerRepository: PartnerRepository,
    private val tripRepository: TripRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(PartnerServicesUiState())
    val uiState: StateFlow<PartnerServicesUiState> = _uiState.asStateFlow()

    private var operatorId: Int? = null
    private var sessionKey: String? = null

    fun start(sessionKey: String, operatorId: Int?) {
        if (this.sessionKey == sessionKey && this.operatorId == operatorId) return
        this.sessionKey = sessionKey
        this.operatorId = operatorId
        _uiState.value = PartnerServicesUiState()
        load()
        loadLocations()
    }

    fun load() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null) }
            partnerRepository.getServices(operatorId)
                .onSuccess { services -> _uiState.update { it.copy(isLoading = false, services = services) } }
                .onFailure { throwable ->
                    _uiState.update { it.copy(isLoading = false, error = throwable.staffMessage("Unable to load services")) }
                }
        }
    }

    /**
     * Partners have no terminals endpoint of their own; the passenger location
     * list is the same corpus (45 PH hubs) and its mock works offline.
     */
    fun loadLocations() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLocationsLoading = true, locationsError = null) }
            tripRepository.getLocations()
                .onSuccess { locations ->
                    _uiState.update { it.copy(isLocationsLoading = false, locations = locations.map { l -> l.name }.distinct().sorted()) }
                }
                .onFailure { throwable ->
                    _uiState.update {
                        it.copy(isLocationsLoading = false, locationsError = throwable.staffMessage("Unable to load the terminal list"))
                    }
                }
        }
    }

    /**
     * Seats a departure of this class and kind sells. Must agree with the seat
     * maps core-network generates: LUXURY 2+1 × 10 = 30, other buses 2+2 × 11 = 44.
     * Sea services have no seat map; the service's own capacity is used when it
     * states one, otherwise [DEFAULT_SEA_CAPACITY] (and the UI says so).
     */
    fun capacityFor(busClass: BusClass, rideKind: RideKind, service: OperatorService? = null): Int = when {
        rideKind == RideKind.BUS -> if (busClass == BusClass.LUXURY) 30 else 44
        else -> service?.seatLayout?.let { Regex("\\d+").find(it)?.value?.toIntOrNull() } ?: DEFAULT_SEA_CAPACITY
    }

    /** True when [capacityFor] fell back to the sea default rather than a number the service carries. */
    fun isDefaultSeaCapacity(rideKind: RideKind, service: OperatorService? = null): Boolean =
        rideKind != RideKind.BUS && service?.seatLayout?.let { Regex("\\d+").containsMatchIn(it) } != true

    /** Validates the add form and creates the service; trips for the next 14 days generate immediately. */
    fun createService(
        origin: String,
        destination: String,
        rideKind: RideKind,
        busClass: BusClass,
        fareText: String,
        durationText: String,
        hours: Set<Int>,
    ) {
        val fare = fareText.trim().toDoubleOrNull()
        val duration = durationText.trim().toIntOrNull()
        val problem = when {
            origin.isBlank() || destination.isBlank() -> "Pick an origin and a destination"
            origin.trim().equals(destination.trim(), ignoreCase = true) -> "Origin and destination must differ"
            fare == null || fare <= 0 -> "Enter the fare in PHP"
            duration == null || duration < MIN_DURATION_MINUTES -> "Duration must be at least $MIN_DURATION_MINUTES minutes"
            hours.isEmpty() -> "Pick at least one departure hour"
            else -> null
        }
        if (problem != null || fare == null || duration == null) {
            _uiState.update { it.copy(formError = problem) }
            return
        }
        val service = NewService(
            origin = origin.trim(),
            destination = destination.trim(),
            busClass = busClass,
            rideKind = rideKind,
            farePhp = fare,
            departureHours = hours.sorted(),
            durationMinutes = duration,
        )
        viewModelScope.launch {
            _uiState.update { it.copy(isSaving = true, formError = null) }
            partnerRepository.createService(service)
                .onSuccess { created ->
                    _uiState.update {
                        it.copy(
                            isSaving = false,
                            createdCount = it.createdCount + 1,
                            message = "Service created — ${created.generatedTrips} trips / ${created.generatedSeats} seats generated",
                        )
                    }
                    load()
                }
                .onFailure { throwable ->
                    _uiState.update { it.copy(isSaving = false, formError = throwable.staffMessage("Unable to create service")) }
                }
        }
    }

    fun openEdit(service: OperatorService) = _uiState.update { it.copy(editing = service, formError = null) }

    fun closeEdit() = _uiState.update { it.copy(editing = null, formError = null, pendingUpdate = null, pendingRemovedHours = emptyList()) }

    /**
     * Turns the edit form into a [ServiceUpdate] holding only what changed.
     * Dropping departure hours deletes future trips, so that case pauses for a
     * confirmation before [saveEdit] runs.
     */
    fun requestSaveEdit(fareText: String, durationText: String, hours: Set<Int>) {
        val service = _uiState.value.editing ?: return
        val fare = fareText.trim().toDoubleOrNull()
        val duration = durationText.trim().toIntOrNull()
        val problem = when {
            fare == null || fare <= 0 -> "Enter the fare in PHP"
            duration == null || duration < MIN_DURATION_MINUTES -> "Duration must be at least $MIN_DURATION_MINUTES minutes"
            hours.isEmpty() -> "Pick at least one departure hour"
            else -> null
        }
        if (problem != null || fare == null || duration == null) {
            _uiState.update { it.copy(formError = problem) }
            return
        }
        val sortedHours = hours.sorted()
        val update = ServiceUpdate(
            farePhp = fare.takeIf { it != service.farePhp },
            durationMinutes = duration.takeIf { it != service.durationMinutes },
            departureHours = sortedHours.takeIf { it != service.departureHours.sorted() },
        )
        if (update.isEmpty) {
            _uiState.update { it.copy(formError = "Change the fare, hours or duration first") }
            return
        }
        val removed = service.departureHours.filter { it !in hours }.sorted()
        if (removed.isNotEmpty()) {
            _uiState.update { it.copy(formError = null, pendingUpdate = update, pendingRemovedHours = removed) }
        } else {
            saveEdit(update)
        }
    }

    fun confirmPendingEdit() {
        val update = _uiState.value.pendingUpdate ?: return
        _uiState.update { it.copy(pendingUpdate = null, pendingRemovedHours = emptyList()) }
        saveEdit(update)
    }

    fun cancelPendingEdit() = _uiState.update { it.copy(pendingUpdate = null, pendingRemovedHours = emptyList()) }

    /** Applies to future departures with no sold seats only; reports repriced/removed/added counts. */
    private fun saveEdit(update: ServiceUpdate) {
        val service = _uiState.value.editing ?: return
        viewModelScope.launch {
            _uiState.update { it.copy(isSaving = true, formError = null) }
            partnerRepository.updateService(service.id, update)
                .onSuccess { changed ->
                    _uiState.update {
                        it.copy(
                            isSaving = false,
                            editing = null,
                            message = "Saved — repriced ${changed.repricedTrips}, removed ${changed.removedTrips}, " +
                                "added ${changed.addedTrips} trips",
                        )
                    }
                    load()
                }
                .onFailure { throwable ->
                    _uiState.update { it.copy(isSaving = false, formError = throwable.staffMessage("Unable to save changes")) }
                }
        }
    }

    fun consumeMessage() = _uiState.update { it.copy(message = null) }
}
