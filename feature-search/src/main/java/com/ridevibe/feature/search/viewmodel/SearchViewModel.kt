package com.ridevibe.feature.search.viewmodel

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ridevibe.core.domain.format.PhTime
import com.ridevibe.core.domain.model.BusClass
import com.ridevibe.core.domain.model.LocationKind
import com.ridevibe.core.domain.model.PartyError
import com.ridevibe.core.domain.model.PartyRules
import com.ridevibe.core.domain.model.RideKind
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

/** Everything Results needs to run the search; the nav graph turns it into route args. */
data class SearchRequest(
    val origin: String,
    val destination: String,
    val dateMillis: Long,
    val busClass: BusClass?,
    val adults: Int,
    val children: Int,
    val infants: Int,
    val bookingForSelf: Boolean,
    /** Null when the rider searched without picking a transport tile; Results then shows every kind. */
    val rideKind: RideKind?,
    val isRoundTrip: Boolean,
    val returnDateMillis: Long?,
)

/**
 * A curated corridor on the Home rail. Presentation-only demo content: the
 * badge and price are marketing copy, not live fares. Tapping one prefills
 * the trip form so the rider still goes through a real search.
 */
data class FeaturedRoute(
    val id: String,
    val badge: String,
    val tagline: String,
    val title: String,
    val priceLine: String,
    val origin: String,
    val destination: String,
    val rideKind: RideKind,
)

/** The header search ("Search trips"): pick a place or a whole region, then dates. */
data class ExploreSearchState(
    val query: String = "",
    /** The place or region the rider committed to; null while still typing. */
    val selectedPlace: String? = null,
    val roundTrip: Boolean = false,
    val departMillis: Long? = null,
    val returnMillis: Long? = null,
    // Derived by the view model on every query change.
    val suggestions: List<TerminalLocation> = emptyList(),
    val regionSuggestions: List<String> = emptyList(),
) {
    val canDisplay: Boolean
        get() = selectedPlace != null && departMillis != null && (!roundTrip || returnMillis != null)
}

/** Search criteria collected on the Home screen; execution happens on the results screen. */
data class SearchFormState(
    val origin: String = "",
    val destination: String = "",
    /** PH start-of-day millis; defaults to today (Asia/Manila). */
    val departureDateMillis: Long? = null,
    val returnDateMillis: Long? = null,
    val tripType: TripType = TripType.ONE_WAY,
    val adults: Int = 1,
    val children: Int = 0,
    val infants: Int = 0,
    /** True when the account owner is one of the travelers; false = booking for someone else. */
    val bookingForSelf: Boolean = true,
    val busClassFilter: BusClass? = null, // null = any class
    /** The transport tile the rider picked (Buses / Ferries / Fastcrafts); null until one is tapped. */
    val rideKind: RideKind? = null,
    // Reference data fetched from the backend (mock for now)
    val locations: List<TerminalLocation> = emptyList(),
    val busClasses: List<BusClass> = emptyList(),
    val isLoadingOptions: Boolean = true,
    val optionsError: String? = null,
    /** Earliest and latest bookable day, both PH start-of-day millis. */
    val minDateMillis: Long = 0L,
    val maxDateMillis: Long = Long.MAX_VALUE,
    val explore: ExploreSearchState = ExploreSearchState(),
    val featuredRoutes: List<FeaturedRoute> = emptyList(),
) {
    /** Seats to book — infants ride free on a guardian's lap, no seat. */
    val seatCount: Int get() = PartyRules.seatsFor(adults, children)

    val partyError: PartyError? get() = PartyRules.validate(adults, children, infants)

    /** Sea services have no bus class, so the chips only make sense for buses (or no tile yet). */
    val showsBusClassFilter: Boolean get() = rideKind?.sellsPassage != true

    val passengersLabel: String
        get() = buildList {
            add("$adults Adult${if (adults == 1) "" else "s"}")
            if (children > 0) add("$children Child${if (children == 1) "" else "ren"}")
            if (infants > 0) add("$infants Infant${if (infants == 1) "" else "s"}")
        }.joinToString(", ")

    /** Why the Search button is disabled, in one line; null when the form is ready. */
    val searchBlockedReason: String?
        get() = when {
            origin.isBlank() && destination.isBlank() -> "Pick where you're leaving from and going to."
            origin.isBlank() -> "Pick where you're leaving from."
            destination.isBlank() -> "Pick where you're going."
            origin.equals(destination, ignoreCase = true) -> "Origin and destination can't be the same place."
            partyError != null -> partyError?.message
            departureDateMillis == null -> "Pick a departure date."
            tripType == TripType.ROUND_TRIP && returnDateMillis == null -> "Pick a return date."
            tripType == TripType.ROUND_TRIP && returnDateMillis != null && returnDateMillis < departureDateMillis ->
                "Return date must be on or after departure."
            else -> null
        }

    val canSearch: Boolean get() = searchBlockedReason == null
}

@HiltViewModel
class SearchViewModel @Inject constructor(
    private val savedStateHandle: SavedStateHandle,
    private val getSearchOptions: GetSearchOptionsUseCase,
    private val bookingCart: BookingCart,
) : ViewModel() {

    private val _formState = MutableStateFlow(restoreForm())
    val formState: StateFlow<SearchFormState> = _formState.asStateFlow()

    init {
        loadOptions()
    }

    /** Fetches locations and bus classes; Home's pickers show a retry when this fails (offline). */
    fun loadOptions() {
        viewModelScope.launch {
            _formState.update { it.copy(isLoadingOptions = true, optionsError = null) }
            val locations = getSearchOptions.locations()
            val busClasses = getSearchOptions.busClasses()
            val failure = locations.exceptionOrNull() ?: busClasses.exceptionOrNull()
            _formState.update { form ->
                form.copy(
                    locations = locations.getOrDefault(form.locations),
                    busClasses = busClasses.getOrDefault(form.busClasses),
                    isLoadingOptions = false,
                    optionsError = failure?.let { it.message ?: "Couldn't load terminals. Check your connection." },
                ).let { it.copy(explore = deriveExplore(it.explore, it.locations)) }
            }
        }
    }

    // ── Trip form ──────────────────────────────────────────────────────────

    fun onRideKindSelected(kind: RideKind) = updateForm {
        // Sea services carry no bus class, so a stale bus filter must not leak into a ferry search.
        it.copy(rideKind = kind, busClassFilter = if (kind.sellsPassage) null else it.busClassFilter)
    }

    fun onOriginSelected(value: String) = updateForm { it.copy(origin = value) }

    fun onDestinationSelected(value: String) = updateForm { it.copy(destination = value) }

    fun onSwapLocations() = updateForm { it.copy(origin = it.destination, destination = it.origin) }

    /** Accepts any instant on the chosen day and stores PH start-of-day, clamped to the booking window. */
    fun onDateSelected(epochMillis: Long) = updateForm { form ->
        val day = form.clampDay(epochMillis)
        form.copy(
            departureDateMillis = day,
            // A return before the new departure is impossible; pull it forward rather than silently keep it.
            returnDateMillis = form.returnDateMillis?.coerceAtLeast(day),
        )
    }

    fun onReturnDateSelected(epochMillis: Long) = updateForm { form ->
        val day = form.clampDay(epochMillis)
        form.copy(returnDateMillis = day.coerceAtLeast(form.departureDateMillis ?: day))
    }

    fun onTripTypeChanged(type: TripType) = updateForm {
        it.copy(tripType = type, returnDateMillis = if (type == TripType.ONE_WAY) null else it.returnDateMillis)
    }

    fun onPassengersChanged(adults: Int, children: Int, infants: Int) = updateForm {
        it.copy(adults = adults.coerceAtLeast(1), children = children.coerceAtLeast(0), infants = infants.coerceAtLeast(0))
    }

    fun onBookingForSelfChanged(forSelf: Boolean) = updateForm { it.copy(bookingForSelf = forSelf) }

    fun onBusClassFilterChanged(busClass: BusClass?) = updateForm { it.copy(busClassFilter = busClass) }

    /** A featured corridor prefills the form; the rider still picks the date and party. */
    fun onFeaturedRouteSelected(route: FeaturedRoute) = updateForm {
        it.copy(
            origin = route.origin,
            destination = route.destination,
            rideKind = route.rideKind,
            tripType = TripType.ONE_WAY,
            returnDateMillis = null,
            busClassFilter = null,
        )
    }

    /**
     * Assembles the request and seeds the shared cart so a round trip can
     * gather both legs. Null when the form is not ready (the button is disabled
     * then, but a stale click must still not navigate).
     */
    fun submitSearch(): SearchRequest? {
        val form = _formState.value
        if (!form.canSearch) return null
        val dateMillis = form.departureDateMillis ?: return null
        val isRoundTrip = form.tripType == TripType.ROUND_TRIP
        val returnDate = form.returnDateMillis.takeIf { isRoundTrip }
        bookingCart.prime(
            isRoundTrip = isRoundTrip,
            origin = form.origin,
            destination = form.destination,
            departDateMillis = dateMillis,
            returnDateMillis = returnDate,
            busClass = form.busClassFilter,
            adults = form.adults,
            children = form.children,
            infants = form.infants,
            forSelf = form.bookingForSelf,
            rideKind = form.rideKind,
        )
        return SearchRequest(
            origin = form.origin,
            destination = form.destination,
            dateMillis = dateMillis,
            busClass = form.busClassFilter,
            adults = form.adults,
            children = form.children,
            infants = form.infants,
            bookingForSelf = form.bookingForSelf,
            rideKind = form.rideKind,
            isRoundTrip = isRoundTrip,
            returnDateMillis = returnDate,
        )
    }

    // ── Location picker ────────────────────────────────────────────────────

    /**
     * Suggestions for the From/To picker. Blank query → a "Popular" list
     * (central terminals first). Otherwise matches name, region, description
     * and common abbreviations. Buses never depart seaports and sea services
     * never leave bus terminals, so the chosen tile narrows the list.
     */
    fun locationSuggestions(query: String): List<TerminalLocation> {
        val form = _formState.value
        val pool = form.locations.filter { it.suitsRideKind(form.rideKind) }
        val ranked = pool.sortedWith(compareByDescending<TerminalLocation> { it.isCentralTerminal }.thenBy { it.name })
        val needle = query.trim()
        val matches = if (needle.isBlank()) {
            ranked.filter { it.isCentralTerminal || it.kind != LocationKind.CITY }
        } else {
            ranked.filter { it.matches(needle) }
        }
        return matches.take(MAX_PICKER_RESULTS)
    }

    // ── Header explore search ──────────────────────────────────────────────

    fun onExploreQueryChanged(query: String) = updateForm { form ->
        // Editing the text restarts the suggestion stage.
        form.copy(explore = deriveExplore(form.explore.copy(query = query, selectedPlace = null), form.locations))
    }

    fun onExplorePlaceSelected(place: String) = updateForm { form ->
        form.copy(explore = form.explore.copy(query = place, selectedPlace = place, suggestions = emptyList(), regionSuggestions = emptyList()))
    }

    fun onExploreRoundTripChanged(roundTrip: Boolean) = updateForm { form ->
        form.copy(explore = form.explore.copy(roundTrip = roundTrip, returnMillis = form.explore.returnMillis.takeIf { roundTrip }))
    }

    fun onExploreDepartSelected(epochMillis: Long) = updateForm { form ->
        val day = form.clampDay(epochMillis)
        form.copy(explore = form.explore.copy(departMillis = day, returnMillis = form.explore.returnMillis?.coerceAtLeast(day)))
    }

    fun onExploreReturnSelected(epochMillis: Long) = updateForm { form ->
        val day = form.clampDay(epochMillis)
        form.copy(explore = form.explore.copy(returnMillis = day.coerceAtLeast(form.explore.departMillis ?: day)))
    }

    /** Clears the header search so reopening it starts fresh. */
    fun onExploreReset() = updateForm { form ->
        form.copy(explore = ExploreSearchState(departMillis = form.minDateMillis))
    }

    private fun deriveExplore(explore: ExploreSearchState, locations: List<TerminalLocation>): ExploreSearchState {
        val needle = explore.query.trim()
        if (needle.isBlank() || explore.selectedPlace != null) {
            return explore.copy(suggestions = emptyList(), regionSuggestions = emptyList())
        }
        val regions = locations.map { it.region }.filter { it.isNotBlank() }.distinct()
            .filter { it.contains(needle, ignoreCase = true) }
        val places = locations
            .sortedWith(compareByDescending<TerminalLocation> { it.isCentralTerminal }.thenBy { it.name })
            .filter { it.matches(needle) }
            .take(MAX_PICKER_RESULTS)
        return explore.copy(suggestions = places, regionSuggestions = regions)
    }

    // ── Persistence ────────────────────────────────────────────────────────

    private inline fun updateForm(transform: (SearchFormState) -> SearchFormState) {
        _formState.update(transform)
        persist(_formState.value)
    }

    private fun SearchFormState.clampDay(epochMillis: Long): Long =
        PhTime.startOfDay(epochMillis).coerceIn(minDateMillis, maxDateMillis)

    private fun restoreForm(): SearchFormState {
        val today = PhTime.todayStartMillis()
        val max = today + BOOKING_WINDOW_DAYS * PhTime.DAY_MILLIS
        val depart = savedStateHandle.get<Long>(KEY_DEPART)?.coerceIn(today, max) ?: today
        return SearchFormState(
            origin = savedStateHandle[KEY_ORIGIN] ?: "",
            destination = savedStateHandle[KEY_DESTINATION] ?: "",
            departureDateMillis = depart,
            returnDateMillis = savedStateHandle.get<Long>(KEY_RETURN)?.coerceIn(depart, max),
            tripType = savedStateHandle.get<String>(KEY_TRIP_TYPE)?.let { name -> TripType.entries.firstOrNull { it.name == name } }
                ?: TripType.ONE_WAY,
            adults = savedStateHandle[KEY_ADULTS] ?: 1,
            children = savedStateHandle[KEY_CHILDREN] ?: 0,
            infants = savedStateHandle[KEY_INFANTS] ?: 0,
            bookingForSelf = savedStateHandle[KEY_FOR_SELF] ?: true,
            busClassFilter = savedStateHandle.get<String>(KEY_BUS_CLASS)?.let { name -> BusClass.entries.firstOrNull { it.name == name } },
            rideKind = savedStateHandle.get<String>(KEY_RIDE_KIND)?.let { name -> RideKind.entries.firstOrNull { it.name == name } },
            minDateMillis = today,
            maxDateMillis = max,
            explore = ExploreSearchState(departMillis = today),
            featuredRoutes = featuredRoutes,
        )
    }

    private fun persist(form: SearchFormState) {
        savedStateHandle[KEY_ORIGIN] = form.origin
        savedStateHandle[KEY_DESTINATION] = form.destination
        savedStateHandle[KEY_DEPART] = form.departureDateMillis
        savedStateHandle[KEY_RETURN] = form.returnDateMillis
        savedStateHandle[KEY_TRIP_TYPE] = form.tripType.name
        savedStateHandle[KEY_ADULTS] = form.adults
        savedStateHandle[KEY_CHILDREN] = form.children
        savedStateHandle[KEY_INFANTS] = form.infants
        savedStateHandle[KEY_FOR_SELF] = form.bookingForSelf
        savedStateHandle[KEY_BUS_CLASS] = form.busClassFilter?.name
        savedStateHandle[KEY_RIDE_KIND] = form.rideKind?.name
    }

    private companion object {
        const val BOOKING_WINDOW_DAYS = 60
        const val MAX_PICKER_RESULTS = 30

        const val KEY_ORIGIN = "form.origin"
        const val KEY_DESTINATION = "form.destination"
        const val KEY_DEPART = "form.depart"
        const val KEY_RETURN = "form.return"
        const val KEY_TRIP_TYPE = "form.tripType"
        const val KEY_ADULTS = "form.adults"
        const val KEY_CHILDREN = "form.children"
        const val KEY_INFANTS = "form.infants"
        const val KEY_FOR_SELF = "form.forSelf"
        const val KEY_BUS_CLASS = "form.busClass"
        const val KEY_RIDE_KIND = "form.rideKind"

        /**
         * Short forms riders actually type. Keys are lower-case needles; values
         * are the hub names they should surface even though the name doesn't
         * contain the needle.
         */
        val locationAliases: Map<String, List<String>> = mapOf(
            "manila" to listOf("PITX", "Cubao", "Pasay", "Avenida", "Manila North Harbor"),
            "mnl" to listOf("PITX", "Cubao", "Pasay", "Avenida", "Manila North Harbor"),
            "metro manila" to listOf("PITX", "Cubao", "Pasay", "Avenida"),
            "paranaque" to listOf("PITX"),
            "parañaque" to listOf("PITX"),
            "qc" to listOf("Cubao"),
            "quezon city" to listOf("Cubao"),
            "edsa" to listOf("Cubao", "Pasay"),
            "nlet" to listOf("NLET Bocaue"),
            "vgbc" to listOf("Valenzuela Gateway"),
            "cdo" to listOf("Cagayan de Oro Port", "Cagayan de Oro"),
            "siargao" to listOf("Dapa Port (Siargao)", "Surigao Port"),
            "boracay" to listOf("Caticlan", "Batangas Port"),
            "bora" to listOf("Caticlan", "Batangas Port"),
        )

        /** The three corridors Home advertises; the mock corpus seeds inventory for each. */
        val featuredRoutes: List<FeaturedRoute> = listOf(
            FeaturedRoute(
                id = "boracay",
                badge = "20% OFF",
                tagline = "SUMMER GETAWAY",
                title = "Boracay via Batangas",
                priceLine = "Starting at ₱850",
                origin = "Batangas Port",
                destination = "Caticlan",
                rideKind = RideKind.FERRY,
            ),
            FeaturedRoute(
                id = "tagaytay",
                badge = "15% OFF",
                tagline = "CITY ESCAPE",
                title = "Tagaytay Weekender",
                priceLine = "Starting at ₱320",
                origin = "PITX",
                destination = "Tagaytay",
                rideKind = RideKind.BUS,
            ),
            FeaturedRoute(
                id = "baguio",
                badge = "10% OFF",
                tagline = "NORTH EXPRESS",
                title = "Baguio Night Trip",
                priceLine = "Starting at ₱690",
                origin = "Cubao",
                destination = "Baguio",
                rideKind = RideKind.BUS,
            ),
        )

        fun TerminalLocation.suitsRideKind(kind: RideKind?): Boolean = when (kind) {
            null -> true
            RideKind.BUS -> this.kind != LocationKind.SEAPORT
            // Sea routes end at towns the corpus lists as plain cities (Calapan,
            // Caticlan, Puerto Galera), not only at named ports, so cities stay
            // searchable for sea kinds; only bus-only terminals are excluded.
            RideKind.FERRY, RideKind.FASTCRAFT -> this.kind != LocationKind.BUS_TERMINAL
        }

        fun TerminalLocation.matches(needle: String): Boolean {
            if (name.contains(needle, ignoreCase = true)) return true
            if (region.contains(needle, ignoreCase = true)) return true
            if (description.contains(needle, ignoreCase = true)) return true
            val lower = needle.lowercase()
            return locationAliases.any { (alias, hubs) -> alias.startsWith(lower) && name in hubs }
        }
    }
}
