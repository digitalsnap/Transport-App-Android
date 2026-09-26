package com.ridevibe.core.network.session

import com.ridevibe.core.domain.model.BusClass
import com.ridevibe.core.domain.model.RideKind
import com.ridevibe.core.domain.session.BookingCartState
import com.ridevibe.core.domain.session.BookingCartStore
import com.ridevibe.core.network.log.RideVibeLog
import com.ridevibe.core.network.storage.CartStore
import com.ridevibe.core.network.storage.KeyValueStore
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Keeps the booking in progress in the plain `ridevibe_cart` preferences as
 * one JSON document, so a rider who is interrupted mid-checkout (a call, a
 * low-memory kill) comes back to the same held seats.
 *
 * The JSON shape is a private mirror of [BookingCartState]: the domain class
 * is not `@Serializable` (core-domain has no serialization plugin) and a
 * persisted format must not change every time the domain class does.
 */
@Singleton
class PrefsBookingCartStore @Inject constructor(
    @CartStore private val store: KeyValueStore,
    private val json: Json,
) : BookingCartStore {

    @Serializable
    private data class CartStateDto(
        val version: Int = FORMAT_VERSION,
        val isRoundTrip: Boolean = false,
        val origin: String = "",
        val destination: String = "",
        val departDateMillis: Long = 0L,
        val returnDateMillis: Long? = null,
        val busClass: String? = null,
        val rideKind: String? = null,
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
    )

    override fun load(): BookingCartState? {
        val raw = store.get(KEY_STATE) ?: return null
        return runCatching { json.decodeFromString(CartStateDto.serializer(), raw) }
            .onFailure {
                // A cart we cannot read must not brick Home: drop it and start clean.
                RideVibeLog.w("Discarding unreadable booking cart", it)
                store.remove(KEY_STATE)
            }
            .getOrNull()
            ?.takeIf { it.version == FORMAT_VERSION }
            ?.toState()
    }

    override fun save(state: BookingCartState) {
        store.put(KEY_STATE, json.encodeToString(CartStateDto.serializer(), state.toDto()))
    }

    override fun clear() = store.remove(KEY_STATE)

    private fun BookingCartState.toDto() = CartStateDto(
        isRoundTrip = isRoundTrip,
        origin = origin,
        destination = destination,
        departDateMillis = departDateMillis,
        returnDateMillis = returnDateMillis,
        busClass = busClass?.name,
        rideKind = rideKind?.name,
        adults = adults,
        children = children,
        infants = infants,
        forSelf = forSelf,
        outboundTripId = outboundTripId,
        outboundSeatIds = outboundSeatIds,
        outboundHoldExpiresAtEpochMillis = outboundHoldExpiresAtEpochMillis,
        outboundFarePhp = outboundFarePhp,
        returnTripId = returnTripId,
        returnSeatIds = returnSeatIds,
        returnHoldExpiresAtEpochMillis = returnHoldExpiresAtEpochMillis,
        returnFarePhp = returnFarePhp,
    )

    private fun CartStateDto.toState() = BookingCartState(
        isRoundTrip = isRoundTrip,
        origin = origin,
        destination = destination,
        departDateMillis = departDateMillis,
        returnDateMillis = returnDateMillis,
        busClass = busClass?.let { name -> BusClass.values().firstOrNull { it.name == name } },
        rideKind = rideKind?.let { name -> RideKind.values().firstOrNull { it.name == name } },
        adults = adults,
        children = children,
        infants = infants,
        forSelf = forSelf,
        outboundTripId = outboundTripId,
        outboundSeatIds = outboundSeatIds,
        outboundHoldExpiresAtEpochMillis = outboundHoldExpiresAtEpochMillis,
        outboundFarePhp = outboundFarePhp,
        returnTripId = returnTripId,
        returnSeatIds = returnSeatIds,
        returnHoldExpiresAtEpochMillis = returnHoldExpiresAtEpochMillis,
        returnFarePhp = returnFarePhp,
    )

    private companion object {
        const val KEY_STATE = "cart_state"

        /** Bump when the JSON shape changes incompatibly; an old cart is then discarded. */
        const val FORMAT_VERSION = 1
    }
}
