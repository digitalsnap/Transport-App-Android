package com.ridevibe.core.domain.session

import com.ridevibe.core.domain.model.BusClass
import com.ridevibe.core.domain.model.RideKind
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import javax.inject.Inject
import javax.inject.Singleton

/** Which leg the rider is picking seats for right now. */
enum class CartLeg { ONE_WAY, OUTBOUND, RETURN }

/**
 * Everything the booking-in-progress knows, as one immutable value. The search
 * fields come from Home; the leg fields fill in as the rider picks seats
 * (outbound first, then — for a round trip — the return leg with origin and
 * destination swapped). Persisted between process deaths so a rider who
 * answers a call mid-checkout does not lose their held seats.
 */
data class BookingCartState(
    val isRoundTrip: Boolean = false,
    val origin: String = "",
    val destination: String = "",
    val departDateMillis: Long = 0L,
    val returnDateMillis: Long? = null,
    val busClass: BusClass? = null,
    /** The kind of the trip picked for the outbound leg; null until a trip is chosen. */
    val rideKind: RideKind? = null,
    val adults: Int = 1,
    val children: Int = 0,
    val infants: Int = 0,
    val forSelf: Boolean = true,
    val outboundTripId: String? = null,
    val outboundSeatIds: List<String> = emptyList(),
    val outboundHoldExpiresAtEpochMillis: Long? = null,
    val outboundFarePhp: Double? = null,
    val returnTripId: String? = null,
    val returnSeatIds: List<String> = emptyList(),
    val returnHoldExpiresAtEpochMillis: Long? = null,
    val returnFarePhp: Double? = null,
) {
    /** Seats (or sea spaces) the party needs: adults and children; infants ride on a lap. */
    val seatCount: Int get() = adults + children

    val currentLeg: CartLeg
        get() = when {
            !isRoundTrip -> CartLeg.ONE_WAY
            outboundTripId == null -> CartLeg.OUTBOUND
            returnTripId == null -> CartLeg.RETURN
            else -> CartLeg.RETURN // both legs picked; checkout is next
        }

    val hasOutboundLeg: Boolean get() = outboundTripId != null
    val hasReturnLeg: Boolean get() = returnTripId != null
}

/**
 * Where [BookingCart] keeps its state between launches. The data layer owns
 * the implementation (SharedPreferences); the domain only needs load/save/clear.
 */
interface BookingCartStore {
    fun load(): BookingCartState?
    fun save(state: BookingCartState)
    fun clear()
}

/**
 * The booking currently being assembled, shared across the search → seats →
 * checkout screens. Exists so a round trip can collect BOTH legs (outbound +
 * return) before a single itemised checkout. Primed by a new search, reset
 * when a booking completes.
 *
 * Every mutation goes through a method and is written to [BookingCartStore]
 * immediately, so the cart survives process death; [reset] wipes both.
 */
@Singleton
class BookingCart @Inject constructor(
    private val store: BookingCartStore,
) {
    private val _state = MutableStateFlow(store.load() ?: BookingCartState())
    val state: StateFlow<BookingCartState> = _state.asStateFlow()

    // Read-only views so existing call sites (`cart.origin`, `cart.isRoundTrip`, …) keep working.
    val isRoundTrip: Boolean get() = _state.value.isRoundTrip
    val origin: String get() = _state.value.origin
    val destination: String get() = _state.value.destination
    val departDateMillis: Long get() = _state.value.departDateMillis
    val returnDateMillis: Long? get() = _state.value.returnDateMillis
    val busClass: BusClass? get() = _state.value.busClass
    val rideKind: RideKind? get() = _state.value.rideKind
    val adults: Int get() = _state.value.adults
    val children: Int get() = _state.value.children
    val infants: Int get() = _state.value.infants
    val forSelf: Boolean get() = _state.value.forSelf
    val outboundTripId: String? get() = _state.value.outboundTripId
    val outboundSeatIds: List<String> get() = _state.value.outboundSeatIds
    val outboundHoldExpiresAtEpochMillis: Long? get() = _state.value.outboundHoldExpiresAtEpochMillis
    val outboundFarePhp: Double? get() = _state.value.outboundFarePhp
    val returnTripId: String? get() = _state.value.returnTripId
    val returnSeatIds: List<String> get() = _state.value.returnSeatIds
    val returnHoldExpiresAtEpochMillis: Long? get() = _state.value.returnHoldExpiresAtEpochMillis
    val returnFarePhp: Double? get() = _state.value.returnFarePhp
    val currentLeg: CartLeg get() = _state.value.currentLeg

    /** A new search: replaces the whole cart, dropping any legs picked for the previous one. */
    fun prime(
        isRoundTrip: Boolean,
        origin: String,
        destination: String,
        departDateMillis: Long,
        returnDateMillis: Long?,
        busClass: BusClass?,
        adults: Int,
        children: Int,
        infants: Int,
        forSelf: Boolean,
        rideKind: RideKind? = null,
    ) = mutate {
        BookingCartState(
            isRoundTrip = isRoundTrip,
            origin = origin,
            destination = destination,
            departDateMillis = departDateMillis,
            returnDateMillis = returnDateMillis,
            busClass = busClass,
            rideKind = rideKind,
            adults = adults,
            children = children,
            infants = infants,
            forSelf = forSelf,
        )
    }

    /** Records the outbound pick. Any previously picked return leg is dropped: it belonged to the old outbound. */
    fun setOutboundLeg(
        tripId: String,
        seatIds: List<String>,
        holdExpiresAt: Long? = null,
        farePhp: Double? = null,
        rideKind: RideKind? = null,
    ) = mutate {
        it.copy(
            outboundTripId = tripId,
            outboundSeatIds = seatIds,
            outboundHoldExpiresAtEpochMillis = holdExpiresAt,
            outboundFarePhp = farePhp,
            rideKind = rideKind ?: it.rideKind,
            returnTripId = null,
            returnSeatIds = emptyList(),
            returnHoldExpiresAtEpochMillis = null,
            returnFarePhp = null,
        )
    }

    fun setReturnLeg(
        tripId: String,
        seatIds: List<String>,
        holdExpiresAt: Long? = null,
        farePhp: Double? = null,
    ) = mutate {
        it.copy(
            returnTripId = tripId,
            returnSeatIds = seatIds,
            returnHoldExpiresAtEpochMillis = holdExpiresAt,
            returnFarePhp = farePhp,
        )
    }

    /** Rider went back from the return leg's seat map; the outbound pick stays. */
    fun clearReturnLeg() = mutate {
        it.copy(
            returnTripId = null,
            returnSeatIds = emptyList(),
            returnHoldExpiresAtEpochMillis = null,
            returnFarePhp = null,
        )
    }

    /** Booking done or abandoned: nothing of the old search may leak into the next one. */
    fun reset() {
        _state.value = BookingCartState()
        store.clear()
    }

    private inline fun mutate(transform: (BookingCartState) -> BookingCartState) {
        _state.update(transform)
        store.save(_state.value)
    }
}
