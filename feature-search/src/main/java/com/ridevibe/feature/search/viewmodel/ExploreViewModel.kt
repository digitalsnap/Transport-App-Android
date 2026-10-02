package com.ridevibe.feature.search.viewmodel

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ridevibe.core.domain.format.PhTime
import com.ridevibe.core.domain.model.Journey
import com.ridevibe.core.domain.model.RideKind
import com.ridevibe.core.domain.model.Trip
import com.ridevibe.core.domain.repository.ItineraryRepository
import com.ridevibe.core.domain.session.BookingCart
import com.ridevibe.core.domain.session.CartLeg
import com.ridevibe.core.domain.usecase.FindJourneysUseCase
import com.ridevibe.core.domain.usecase.GetSearchOptionsUseCase
import com.ridevibe.core.domain.usecase.SearchRelatedTripsUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

/** Which direction of a round-trip explore search a trip belongs to. */
enum class ExploreLeg { OUTBOUND, RETURN }

/** One ride-kind group of an Explore result list; always present so an empty kind can say so. */
data class ExploreSection(
    val kind: RideKind,
    val leg: ExploreLeg,
    val title: String,
    val items: List<TripListItem>,
) {
    val key: String get() = "${leg.name}-${kind.name}"

    val emptyMessage: String
        get() = when (kind) {
            RideKind.BUS -> "No buses on this date"
            RideKind.FERRY -> "No ferries on this date"
            RideKind.FASTCRAFT -> "No fastcrafts on this date"
        }
}

data class ExploreUiState(
    val query: String = "",
    val dateMillis: Long = 0L,
    /** Non-null for a round-trip explore search; the return sections are then shown under their own header. */
    val returnDateMillis: Long? = null,
    val isLoading: Boolean = true,
    /** The route carried no query: nothing to retry, only a way back. */
    val isArgsError: Boolean = false,
    val errorMessage: String? = null,
    val trips: List<Trip> = emptyList(),
    /** Curated multi-leg tourist routes matching the query (e.g. Manila → Siargao). */
    val journeys: List<Journey> = emptyList(),
    val sortOption: SortOption = SortOption.EARLIEST,
    /** True when the query names a whole region (from the locations' `region` field). */
    val isRegionQuery: Boolean = false,
    val outboundSections: List<ExploreSection> = emptyList(),
    val returnSections: List<ExploreSection> = emptyList(),
    /** One-shot message for the Snackbar (itinerary saved / failed). */
    val snackbarMessage: String? = null,
) {
    val isRoundTrip: Boolean get() = returnDateMillis != null
    val isEmpty: Boolean get() = trips.isEmpty() && journeys.isEmpty()
}

// ItineraryRepository is injected directly (no use-case layer): a plain save
// with no domain logic, consistent with the other CRUD-only screens.
@HiltViewModel
class ExploreViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val searchRelatedTripsUseCase: SearchRelatedTripsUseCase,
    private val findJourneysUseCase: FindJourneysUseCase,
    private val getSearchOptions: GetSearchOptionsUseCase,
    private val itineraryRepository: ItineraryRepository,
    private val bookingCart: BookingCart,
) : ViewModel() {

    private val query: String = savedStateHandle.get<String>("query").orEmpty().trim()
    // Party from the Home form (route args); absent on older entry points → one adult.
    private val partyAdults: Int = savedStateHandle.get<String>("adults")?.toIntOrNull()?.coerceAtLeast(1) ?: 1
    private val partyChildren: Int = savedStateHandle.get<String>("children")?.toIntOrNull()?.coerceAtLeast(0) ?: 0
    private val partyInfants: Int = savedStateHandle.get<String>("infants")?.toIntOrNull()?.coerceAtLeast(0) ?: 0
    private val departureDateMillis: Long = PhTime.startOfDay(
        savedStateHandle.get<String>("date")?.toLongOrNull()?.takeIf { it > 0 } ?: System.currentTimeMillis(),
    )
    private val returnDateMillis: Long? =
        savedStateHandle.get<String>("returnDate")?.toLongOrNull()?.takeIf { it > 0 }?.let(PhTime::startOfDay)

    private val _uiState = MutableStateFlow(
        ExploreUiState(
            query = query,
            dateMillis = departureDateMillis,
            returnDateMillis = returnDateMillis,
            isLoading = query.isNotBlank(),
            isArgsError = query.isBlank(),
            errorMessage = if (query.isBlank()) "Nothing to search for. Go back and pick a place or region." else null,
        ),
    )
    val uiState: StateFlow<ExploreUiState> = _uiState.asStateFlow()

    init {
        if (query.isNotBlank()) load()
    }

    fun load() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, errorMessage = null) }
            // Region detection comes from the same locations list Home uses, not a hard-coded trio.
            val regions = getSearchOptions.locations().getOrDefault(emptyList())
                .map { it.region }.filter { it.isNotBlank() }.distinct()
            val isRegion = regions.any { it.equals(query, ignoreCase = true) || it.contains(query, ignoreCase = true) }
            val journeys = findJourneysUseCase(query).getOrDefault(emptyList())
            searchRelatedTripsUseCase(query, departureDateMillis, returnDateMillis)
                .onSuccess { trips ->
                    _uiState.update {
                        derive(it.copy(isLoading = false, trips = trips, journeys = journeys, isRegionQuery = isRegion))
                    }
                }
                .onFailure { throwable ->
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            journeys = journeys,
                            isRegionQuery = isRegion,
                            errorMessage = throwable.message ?: "Search failed. Check your connection and try again.",
                        )
                    }
                }
        }
    }

    fun onSortOptionChanged(option: SortOption) = _uiState.update { derive(it.copy(sortOption = option)) }

    fun addToItinerary(journey: Journey) {
        viewModelScope.launch {
            // The repository has no Result type yet (mock-only); a thrown error is the failure signal.
            val message = runCatching { itineraryRepository.addItinerary(journey, departureDateMillis) }
                .fold(
                    onSuccess = { "Saved to your Itinerary tab" },
                    onFailure = { "Couldn't save this route. Try again." },
                )
            _uiState.update { it.copy(snackbarMessage = message) }
        }
    }

    fun onSnackbarShown() = _uiState.update { it.copy(snackbarMessage = null) }

    /**
     * The earliest day a journey leg can start: the chosen date plus every
     * earlier leg's travel time, so tapping leg 3 of Manila → Siargao searches
     * the day the rider would actually reach that port.
     */
    fun legDateMillis(journey: Journey, legIndex: Int): Long {
        val elapsedMinutes = journey.legs.take(legIndex).sumOf { it.durationMinutes }
        return PhTime.startOfDay(departureDateMillis + elapsedMinutes * 60_000L)
    }

    /**
     * Seeds the shared cart for the tapped trip and says which leg the caller
     * should open. A round-trip explore only becomes a two-leg cart when the
     * caller can route legs ([callerSupportsLegs]); otherwise it stays one-way
     * so checkout never waits for a return leg nobody can pick.
     */
    fun onTripChosen(trip: Trip, leg: ExploreLeg, callerSupportsLegs: Boolean): CartLeg {
        val state = _uiState.value
        // A pick from the Return section while this explore's outbound leg is
        // already in the cart completes the round trip; re-priming would wipe it.
        if (callerSupportsLegs && state.isRoundTrip && leg == ExploreLeg.RETURN &&
            bookingCart.isRoundTrip && bookingCart.outboundTripId != null
        ) {
            return CartLeg.RETURN
        }
        val roundTrip = callerSupportsLegs && state.isRoundTrip && leg == ExploreLeg.OUTBOUND
        bookingCart.prime(
            isRoundTrip = roundTrip,
            origin = trip.origin,
            destination = trip.destination,
            departDateMillis = PhTime.startOfDay(trip.departureEpochMillis),
            returnDateMillis = state.returnDateMillis.takeIf { roundTrip },
            busClass = null,
            adults = partyAdults,
            children = partyChildren,
            infants = partyInfants,
            forSelf = true,
            rideKind = trip.rideKind,
        )
        return if (roundTrip) CartLeg.OUTBOUND else CartLeg.ONE_WAY
    }

    /** Splits trips by direction (return-day departures) and ride kind, sorted; recomputed per input change. */
    private fun derive(state: ExploreUiState): ExploreUiState {
        val returnStart = state.returnDateMillis?.takeIf { it > state.dateMillis }
        val place = state.query.trim()
        // Same-day round trip: the date cannot separate the legs, so a trip that
        // leaves the searched place (and does not head to it) is the return leg.
        // Region queries match neither name and stay outbound.
        fun Trip.departsFromPlace() = place.isNotBlank() &&
            origin.contains(place, ignoreCase = true) && !destination.contains(place, ignoreCase = true)
        val (returnTrips, outboundTrips) = state.trips.partition {
            state.isRoundTrip && if (returnStart != null) it.departureEpochMillis >= returnStart else it.departsFromPlace()
        }
        fun sections(trips: List<Trip>, leg: ExploreLeg) = RideKind.entries.map { kind ->
            ExploreSection(
                kind = kind,
                leg = leg,
                title = when (kind) {
                    RideKind.BUS -> "Provincial Buses"
                    RideKind.FERRY -> "Ferries"
                    RideKind.FASTCRAFT -> "Fastcrafts"
                },
                items = trips.filter { it.rideKind == kind }.sortedBy(state.sortOption)
                    .map { TripListItem.of(it, seatCount = 1) },
            )
        }
        return state.copy(
            outboundSections = sections(outboundTrips, ExploreLeg.OUTBOUND),
            returnSections = if (state.isRoundTrip) sections(returnTrips, ExploreLeg.RETURN) else emptyList(),
        )
    }
}
