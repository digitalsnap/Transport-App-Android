package com.ridevibe.feature.search.viewmodel

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ridevibe.core.domain.model.BusClass
import com.ridevibe.core.domain.model.Trip
import com.ridevibe.core.domain.usecase.SearchTripsUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

enum class SortOption { RECOMMENDED, CHEAPEST, EARLIEST }

data class ResultsUiState(
    val origin: String = "",
    val destination: String = "",
    val departureDateMillis: Long = 0L,
    val adults: Int = 1,
    val children: Int = 0,
    val infants: Int = 0,
    val sortOption: SortOption = SortOption.RECOMMENDED,
    val isLoading: Boolean = true,
    val results: List<Trip> = emptyList(),
    val errorMessage: String? = null,
) {
    val seatCount: Int get() = adults + children

    val passengerSummary: String
        get() = "$seatCount Passenger${if (seatCount == 1) "" else "s"}" +
            if (infants > 0) " + $infants infant${if (infants == 1) "" else "s"}" else ""

    val sortedResults: List<Trip>
        get() = when (sortOption) {
            SortOption.RECOMMENDED -> results // server ranking order
            SortOption.CHEAPEST -> results.sortedBy { it.farePhp }
            SortOption.EARLIEST -> results.sortedBy { it.departureEpochMillis }
        }
}

@HiltViewModel
class ResultsViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val searchTripsUseCase: SearchTripsUseCase,
) : ViewModel() {

    private val origin: String = checkNotNull(savedStateHandle["origin"])
    private val destination: String = checkNotNull(savedStateHandle["destination"])
    private val departureDateMillis: Long =
        checkNotNull(savedStateHandle.get<String>("dateMillis")).toLong()
    private val busClass: BusClass? =
        savedStateHandle.get<String>("busClass")?.takeIf { it != "ANY" }?.let(BusClass::valueOf)
    private val adults: Int = savedStateHandle.get<String>("adults")?.toIntOrNull() ?: 1
    private val children: Int = savedStateHandle.get<String>("children")?.toIntOrNull() ?: 0
    private val infants: Int = savedStateHandle.get<String>("infants")?.toIntOrNull() ?: 0

    private val _uiState = MutableStateFlow(
        ResultsUiState(
            origin = origin,
            destination = destination,
            departureDateMillis = departureDateMillis,
            adults = adults,
            children = children,
            infants = infants,
        ),
    )
    val uiState: StateFlow<ResultsUiState> = _uiState.asStateFlow()

    init {
        search()
    }

    fun search() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, errorMessage = null) }
            searchTripsUseCase(origin, destination, departureDateMillis, busClass)
                .onSuccess { trips -> _uiState.update { it.copy(isLoading = false, results = trips) } }
                .onFailure { throwable ->
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            errorMessage = throwable.message ?: "Search failed. Check your connection and try again.",
                        )
                    }
                }
        }
    }

    fun onSortOptionChanged(option: SortOption) = _uiState.update { it.copy(sortOption = option) }
}
