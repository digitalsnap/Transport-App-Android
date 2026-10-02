package com.ridevibe.app.ui.itinerary

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ridevibe.core.domain.format.PhTime
import com.ridevibe.core.domain.model.Itinerary
import com.ridevibe.core.domain.repository.ItineraryRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class ItineraryUiState(
    val isLoading: Boolean = true,
    val error: String? = null,
    val itineraries: List<Itinerary> = emptyList(),
    /** The plan just removed, kept until the Undo snackbar times out. */
    val pendingUndo: Itinerary? = null,
    /** Failure of a toggle/remove/restore; shown once via Snackbar. */
    val actionError: String? = null,
)

// ItineraryRepository is injected directly (no use-case layer): CRUD over the
// traveller's saved plans plus the leg-date arithmetic below.
@HiltViewModel
class ItineraryViewModel @Inject constructor(
    private val itineraryRepository: ItineraryRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(ItineraryUiState())
    val uiState: StateFlow<ItineraryUiState> = _uiState.asStateFlow()

    init {
        refresh()
    }

    fun refresh() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null) }
            try {
                val itineraries = itineraryRepository.getItineraries()
                _uiState.update { it.copy(isLoading = false, itineraries = itineraries) }
            } catch (e: Exception) {
                _uiState.update { it.copy(isLoading = false, error = e.message ?: "Could not load your plans") }
            }
        }
    }

    fun toggleLeg(itinerary: Itinerary, legIndex: Int) {
        runAction {
            itineraryRepository.setLegDone(itinerary.id, legIndex, legIndex !in itinerary.doneLegIndices)
        }
    }

    /** Removes the plan and keeps it as [ItineraryUiState.pendingUndo] until the snackbar resolves. */
    fun remove(itinerary: Itinerary) {
        runAction(before = { it.copy(pendingUndo = itinerary) }) {
            itineraryRepository.removeItinerary(itinerary.id)
        }
    }

    /**
     * Puts the removed plan back. The repository has no restore call, so it is
     * re-added from its journey and the ticked legs are replayed onto the new id.
     */
    fun undoRemove() {
        val itinerary = _uiState.value.pendingUndo ?: return
        runAction(before = { it.copy(pendingUndo = null) }) {
            val restored = itineraryRepository.addItinerary(itinerary.journey, itinerary.startDateMillis)
            itinerary.doneLegIndices.forEach { index -> itineraryRepository.setLegDone(restored.id, index, done = true) }
        }
    }

    fun onUndoExpired() = _uiState.update { it.copy(pendingUndo = null) }

    fun onActionErrorShown() = _uiState.update { it.copy(actionError = null) }

    private fun runAction(before: (ItineraryUiState) -> ItineraryUiState = { it }, action: suspend () -> Unit) {
        viewModelScope.launch {
            _uiState.update(before)
            try {
                action()
                _uiState.update { it.copy(itineraries = itineraryRepository.getItineraries()) }
            } catch (e: Exception) {
                _uiState.update { it.copy(actionError = e.message ?: "Something went wrong. Try again.") }
            }
        }
    }

    companion object {
        /**
         * The calendar day to search for leg [legIndex]: the plan's start date
         * pushed forward by the travel time of every earlier leg, so the third
         * leg of a two-day overland trip is searched on day two, not day one.
         * Snapped to PH midnight because Results searches a calendar day.
         */
        fun legSearchDateMillis(itinerary: Itinerary, legIndex: Int): Long {
            val minutesBefore = itinerary.journey.legs.take(legIndex).sumOf { it.durationMinutes }
            return PhTime.startOfDay(itinerary.startDateMillis + minutesBefore * MINUTE_MILLIS)
        }

        private const val MINUTE_MILLIS = 60_000L
    }
}
