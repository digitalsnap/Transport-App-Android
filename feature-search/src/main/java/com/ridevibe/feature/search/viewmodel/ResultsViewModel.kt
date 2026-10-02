package com.ridevibe.feature.search.viewmodel

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ridevibe.core.domain.format.formatPhp
import com.ridevibe.core.domain.model.BusClass
import com.ridevibe.core.domain.model.FareCalculator
import com.ridevibe.core.domain.model.PassengerType
import com.ridevibe.core.domain.model.RideKind
import com.ridevibe.core.domain.model.Trip
import com.ridevibe.core.domain.model.spaceNoun
import com.ridevibe.core.domain.session.BookingCart
import com.ridevibe.core.domain.session.BookingCartState
import com.ridevibe.core.domain.usecase.SearchTripsUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

/** The leg already in the cart, shown above return-leg results so the rider sees what they are pairing. */
data class OutboundSummary(
    val origin: String,
    val destination: String,
    val dateMillis: Long,
    val seatIds: List<String>,
    val farePhp: Double?,
    val rideKind: RideKind?,
) {
    val seatsLabel: String
        get() {
            val noun = (rideKind ?: RideKind.BUS).spaceNoun(plural = seatIds.size != 1)
            return if (seatIds.isEmpty()) "No $noun picked" else "${seatIds.size} $noun: ${seatIds.joinToString(", ")}"
        }
}

data class ResultsUiState(
    val origin: String = "",
    val destination: String = "",
    val departureDateMillis: Long = 0L,
    val adults: Int = 1,
    val children: Int = 0,
    val infants: Int = 0,
    /** "ONE" (one-way), "OUT" (round-trip outbound leg), "RET" (return leg). */
    val leg: String = "ONE",
    /** Set when the route args could not be read; the screen shows only a back button then. */
    val argsError: String? = null,
    /** Ride kind from the Home tile; pre-selects the kind filter (the API has no kind param). */
    val requestedRideKind: RideKind? = null,
    val sortOption: SortOption = SortOption.EARLIEST,
    val rideKindFilter: RideKind? = null,
    val departureWindow: DepartureWindow? = null,
    val operatorFilter: String? = null,
    val isLoading: Boolean = true,
    val isRefreshing: Boolean = false,
    /** Everything the search returned, before client-side filters. */
    val results: List<Trip> = emptyList(),
    /** [results] after filters and sort, computed once per change — never in a getter. */
    val visibleResults: List<TripListItem> = emptyList(),
    /** Distinct operators in [results], for the operator dropdown. */
    val operators: List<String> = emptyList(),
    val errorMessage: String? = null,
    val outbound: OutboundSummary? = null,
    /** "Running total" for a return-leg search: the outbound leg's undiscounted fare for the party. */
    val runningTotalLabel: String? = null,
) {
    val seatCount: Int get() = adults + children

    val passengerSummary: String
        get() = "$seatCount Passenger${if (seatCount == 1) "" else "s"}" +
            if (infants > 0) " + $infants infant${if (infants == 1) "" else "s"}" else ""

    val legLabel: String?
        get() = when (leg) {
            "OUT" -> "Outbound leg"
            "RET" -> "Return leg"
            else -> null
        }

    val isReturnLeg: Boolean get() = leg == "RET"

    val hasActiveFilters: Boolean
        get() = rideKindFilter != null || departureWindow != null || operatorFilter != null
}

@HiltViewModel
class ResultsViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val searchTripsUseCase: SearchTripsUseCase,
    private val bookingCart: BookingCart,
) : ViewModel() {

    // Route args arrive as strings; a malformed deep link must land on an error state, not a crash.
    private val origin: String = savedStateHandle.get<String>("origin").orEmpty()
    private val destination: String = savedStateHandle.get<String>("destination").orEmpty()
    private val departureDateMillis: Long? = savedStateHandle.get<String>("dateMillis")?.toLongOrNull()
    private val busClass: BusClass? = savedStateHandle.get<String>("busClass")
        ?.let { arg -> BusClass.entries.firstOrNull { it.name == arg } }
    private val requestedRideKind: RideKind? = savedStateHandle.get<String>("rideKind")
        ?.let { arg -> RideKind.entries.firstOrNull { it.name == arg } }

    private val _uiState = MutableStateFlow(
        ResultsUiState(
            origin = origin,
            destination = destination,
            departureDateMillis = departureDateMillis ?: 0L,
            adults = savedStateHandle.get<String>("adults")?.toIntOrNull()?.coerceAtLeast(1) ?: 1,
            children = savedStateHandle.get<String>("children")?.toIntOrNull()?.coerceAtLeast(0) ?: 0,
            infants = savedStateHandle.get<String>("infants")?.toIntOrNull()?.coerceAtLeast(0) ?: 0,
            leg = savedStateHandle.get<String>("leg") ?: "ONE",
            requestedRideKind = requestedRideKind,
            rideKindFilter = requestedRideKind,
            argsError = when {
                origin.isBlank() || destination.isBlank() -> "This link is missing the origin or destination."
                departureDateMillis == null -> "This link has no valid travel date."
                else -> null
            },
            isLoading = departureDateMillis != null && origin.isNotBlank() && destination.isNotBlank(),
        ),
    )
    val uiState: StateFlow<ResultsUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            bookingCart.state.collect { cart -> _uiState.update { derive(it.withCart(cart)) } }
        }
        if (_uiState.value.argsError == null) search()
    }

    fun search() = runSearch(refreshing = false)

    /** Pull-to-refresh: keeps the current list on screen while the new one loads. */
    fun refresh() = runSearch(refreshing = true)

    private fun runSearch(refreshing: Boolean) {
        val date = departureDateMillis ?: return
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = !refreshing, isRefreshing = refreshing, errorMessage = null) }
            searchTripsUseCase(origin, destination, date, busClass)
                .onSuccess { trips ->
                    _uiState.update { derive(it.copy(isLoading = false, isRefreshing = false, results = trips)) }
                }
                .onFailure { throwable ->
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            isRefreshing = false,
                            errorMessage = throwable.message ?: "Search failed. Check your connection and try again.",
                        )
                    }
                }
        }
    }

    fun onSortOptionChanged(option: SortOption) = _uiState.update { derive(it.copy(sortOption = option)) }

    fun onRideKindFilterChanged(kind: RideKind?) = _uiState.update { derive(it.copy(rideKindFilter = kind)) }

    fun onDepartureWindowChanged(window: DepartureWindow?) =
        _uiState.update { derive(it.copy(departureWindow = window)) }

    fun onOperatorFilterChanged(operator: String?) = _uiState.update { derive(it.copy(operatorFilter = operator)) }

    fun onClearFilters() = _uiState.update {
        derive(it.copy(rideKindFilter = null, departureWindow = null, operatorFilter = null))
    }

    private fun ResultsUiState.withCart(cart: BookingCartState): ResultsUiState {
        // Only a return-leg search pairs with the outbound already in the cart.
        if (leg != "RET" || !cart.hasOutboundLeg) return copy(outbound = null, runningTotalLabel = null)
        val summary = OutboundSummary(
            origin = cart.origin,
            destination = cart.destination,
            dateMillis = cart.departDateMillis,
            seatIds = cart.outboundSeatIds,
            farePhp = cart.outboundFarePhp,
            rideKind = cart.rideKind,
        )
        val passengers = List(cart.seatCount.coerceAtLeast(1)) { PassengerType.REGULAR }
        val total = cart.outboundFarePhp?.let { FareCalculator.quote(it, passengers).totalPhp }
        return copy(
            outbound = summary,
            runningTotalLabel = total?.let { "Running total: ${formatPhp(it, showCentavos = false)} (outbound only)" },
        )
    }

    /** Applies filters and sort to [ResultsUiState.results]; called on every input change. */
    private fun derive(state: ResultsUiState): ResultsUiState {
        val filtered = state.results.asSequence()
            .filter { state.rideKindFilter == null || it.rideKind == state.rideKindFilter }
            .filter { state.departureWindow?.contains(it.departureEpochMillis) ?: true }
            .filter { state.operatorFilter == null || it.operatorName == state.operatorFilter }
            .toList()
            .sortedBy(state.sortOption)
        val outboundFare = state.outbound?.farePhp
        return state.copy(
            visibleResults = filtered.map { TripListItem.of(it, state.seatCount, outboundFarePhp = outboundFare) },
            operators = state.results.map { it.operatorName }.distinct().sorted(),
        )
    }
}
