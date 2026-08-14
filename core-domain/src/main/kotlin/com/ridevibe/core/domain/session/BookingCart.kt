package com.ridevibe.core.domain.session

import com.ridevibe.core.domain.model.BusClass
import javax.inject.Inject
import javax.inject.Singleton

/**
 * In-memory state for the booking currently being assembled, shared across
 * the search → seats → checkout screens. Exists so a round trip can collect
 * BOTH legs (outbound + return) before a single itemized checkout.
 * Cleared when a new search starts or a booking completes.
 */
@Singleton
class BookingCart @Inject constructor() {

    var isRoundTrip: Boolean = false
        private set
    var origin: String = ""
        private set
    var destination: String = ""
        private set
    var departDateMillis: Long = 0L
        private set
    var returnDateMillis: Long? = null
        private set
    var busClass: BusClass? = null
        private set
    var adults: Int = 1
        private set
    var children: Int = 0
        private set
    var infants: Int = 0
        private set
    var forSelf: Boolean = true
        private set

    var outboundTripId: String? = null
    var outboundSeatIds: List<String> = emptyList()
    var returnTripId: String? = null
    var returnSeatIds: List<String> = emptyList()

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
    ) {
        this.isRoundTrip = isRoundTrip
        this.origin = origin
        this.destination = destination
        this.departDateMillis = departDateMillis
        this.returnDateMillis = returnDateMillis
        this.busClass = busClass
        this.adults = adults
        this.children = children
        this.infants = infants
        this.forSelf = forSelf
        outboundTripId = null
        outboundSeatIds = emptyList()
        returnTripId = null
        returnSeatIds = emptyList()
    }

    fun reset() {
        isRoundTrip = false
        outboundTripId = null
        outboundSeatIds = emptyList()
        returnTripId = null
        returnSeatIds = emptyList()
    }
}
