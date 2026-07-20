package com.ridevibe.feature.search.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ridevibe.core.domain.model.BusClass
import com.ridevibe.core.domain.model.TerminalLocation
import com.ridevibe.core.domain.model.TripType
import com.ridevibe.core.domain.session.BookingCart
import com.ridevibe.core.domain.usecase.GetSearchOptionsUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

/** Search criteria collected on the Home screen; execution happens on the results screen. */
data class SearchFormState(
    val origin: String = "",
    val destination: String = "",
    val departureDateMillis: Long? = null,
    val returnDateMillis: Long? = null,
    val tripType: TripType = TripType.ONE_WAY,
    val adults: Int = 1,
    val children: Int = 0,
    val infants: Int = 0,
    /** True when the account owner is one of the travelers; false = booking for someone else. */
    val bookingForSelf: Boolean = true,
    val busClassFilter: BusClass? = null, // null = any class
    // Reference data fetched from the backend (mock for now)
    val locations: List<TerminalLocation> = emptyList(),
    val busClasses: List<BusClass> = emptyList(),
) {
    /** Seats to book — infants ride free on a guardian's lap, no seat. */
    val seatCount: Int get() = adults + children

    val passengersLabel: String
        get() = buildList {
            add("$adults Adult${if (adults == 1) "" else "s"}")
            if (children > 0) add("$children Child${if (children == 1) "" else "ren"}")
            if (infants > 0) add("$infants Infant${if (infants == 1) "" else "s"}")
        }.joinToString(", ")

    val canSearch: Boolean
        get() = origin.isNotBlank() && destination.isNotBlank() && departureDateMillis != null &&
            seatCount >= 1 &&
            (tripType == TripType.ONE_WAY ||
                (returnDateMillis != null && returnDateMillis >= (departureDateMillis ?: 0L)))
}

@HiltViewModel
class SearchViewModel @Inject constructor(
    private val getSearchOptions: GetSearchOptionsUseCase,
    private val bookingCart: BookingCart,
) : ViewModel() {

    /** Seeds the shared cart with this search so a round trip can gather both legs. */
    fun primeCart() {
        val form = _formState.value
        bookingCart.prime(
            isRoundTrip = form.tripType == TripType.ROUND_TRIP,
            origin = form.origin,
            destination = form.destination,
            departDateMillis = form.departureDateMillis ?: 0L,
            returnDateMillis = form.returnDateMillis,
            busClass = form.busClassFilter,
            adults = form.adults,
            children = form.children,
            infants = form.infants,
            forSelf = form.bookingForSelf,
        )
    }

    private val _formState = MutableStateFlow(SearchFormState())
    val formState: StateFlow<SearchFormState> = _formState.asStateFlow()

    init {
        viewModelScope.launch {
            val locations = getSearchOptions.locations()
            val busClasses = getSearchOptions.busClasses()
            _formState.update { it.copy(locations = locations, busClasses = busClasses) }
        }
    }

    fun onOriginSelected(value: String) = _formState.update { it.copy(origin = value) }

    fun onDestinationSelected(value: String) = _formState.update { it.copy(destination = value) }

    fun onSwapLocations() = _formState.update { it.copy(origin = it.destination, destination = it.origin) }

    fun onDateSelected(epochMillis: Long) = _formState.update { it.copy(departureDateMillis = epochMillis) }

    fun onReturnDateSelected(epochMillis: Long) = _formState.update { it.copy(returnDateMillis = epochMillis) }

    fun onTripTypeChanged(type: TripType) = _formState.update {
        it.copy(tripType = type, returnDateMillis = if (type == TripType.ONE_WAY) null else it.returnDateMillis)
    }

    fun onPassengersChanged(adults: Int, children: Int, infants: Int) = _formState.update {
        it.copy(
            adults = adults.coerceIn(1, 10),
            children = children.coerceIn(0, 10),
            infants = infants.coerceIn(0, 5),
        )
    }

    fun onBookingForSelfChanged(forSelf: Boolean) = _formState.update { it.copy(bookingForSelf = forSelf) }

    fun onBusClassFilterChanged(busClass: BusClass?) = _formState.update { it.copy(busClassFilter = busClass) }
}
