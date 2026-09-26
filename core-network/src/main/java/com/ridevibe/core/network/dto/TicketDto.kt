package com.ridevibe.core.network.dto

import com.ridevibe.core.domain.model.CoPassenger
import com.ridevibe.core.domain.model.Journey
import com.ridevibe.core.domain.model.JourneyLeg
import com.ridevibe.core.domain.model.Passenger
import com.ridevibe.core.domain.model.TerminalLocation
import com.ridevibe.core.domain.model.Ticket
import com.ridevibe.core.domain.model.Trip
import kotlinx.serialization.Serializable

@Serializable
data class LocationDto(
    val name: String,
    val isCentralTerminal: Boolean = false,
    /** BUS_TERMINAL | SEAPORT | CITY (defaults to CITY for plain route endpoints). */
    val kind: String = "CITY",
    val region: String = "",
    val description: String = "",
) {
    fun toDomain() = TerminalLocation(
        name = name,
        isCentralTerminal = isCentralTerminal,
        kind = kind.toLocationKind(),
        region = region,
        description = description,
    )
}

@Serializable
data class CoPassengerDto(
    val firstName: String,
    val lastName: String,
    val mobileNumber: String? = null,
    val type: String = "REGULAR",
    val discountIdImagePath: String? = null,
) {
    fun toDomain() = CoPassenger(
        firstName = firstName,
        lastName = lastName,
        mobileNumber = mobileNumber,
        type = type.toPassengerType(),
        discountIdImagePath = discountIdImagePath,
    )

    companion object {
        fun from(passenger: CoPassenger) = CoPassengerDto(
            firstName = passenger.firstName,
            lastName = passenger.lastName,
            mobileNumber = passenger.mobileNumber,
            type = passenger.type.name,
            discountIdImagePath = passenger.discountIdImagePath,
        )
    }
}

@Serializable
data class BookingRequestDto(
    val seatIds: List<String>,
    val passengerFullName: String,
    val passengerType: String,
    val discountIdImagePath: String? = null,
    val coPassengers: List<CoPassengerDto> = emptyList(),
    val infantCount: Int = 0,
    val paymentMethod: String,
    val promoCode: String? = null,
    /** Idempotency key, generated once per checkout attempt; the server dedupes on it (24 h). */
    val clientReference: String,
)

@Serializable
data class TripDto(
    val id: String,
    val operatorName: String,
    val origin: String,
    val destination: String,
    val departureEpochMillis: Long,
    val arrivalEpochMillis: Long,
    /** ORDINARY | DELUXE | LUXURY; unknown or missing falls back to ORDINARY. */
    val busClass: String = "ORDINARY",
    val farePhp: Double,
    val availableSeatCount: Int,
    val operatorRating: Double? = null,
    /** BUS | FERRY | FASTCRAFT. */
    val rideKind: String = "BUS",
) {
    fun toDomain() = Trip(
        id = id,
        operatorName = operatorName,
        origin = origin,
        destination = destination,
        departureEpochMillis = departureEpochMillis,
        arrivalEpochMillis = arrivalEpochMillis,
        busClass = busClass.toBusClass(),
        farePhp = farePhp,
        availableSeatCount = availableSeatCount,
        operatorRating = operatorRating,
        rideKind = rideKind.toRideKind(),
    )

    companion object {
        fun from(trip: Trip) = TripDto(
            id = trip.id,
            operatorName = trip.operatorName,
            origin = trip.origin,
            destination = trip.destination,
            departureEpochMillis = trip.departureEpochMillis,
            arrivalEpochMillis = trip.arrivalEpochMillis,
            busClass = trip.busClass.name,
            farePhp = trip.farePhp,
            availableSeatCount = trip.availableSeatCount,
            operatorRating = trip.operatorRating,
            rideKind = trip.rideKind.name,
        )
    }
}

@Serializable
data class JourneyLegDto(
    val kind: String,
    val from: String,
    val to: String,
    val durationMinutes: Long,
    val indicativeFarePhp: Double,
    val note: String? = null,
) {
    fun toDomain() = JourneyLeg(
        kind = kind.toRideKind(),
        from = from,
        to = to,
        durationMinutes = durationMinutes,
        indicativeFarePhp = indicativeFarePhp,
        note = note,
    )
}

@Serializable
data class JourneyDto(
    val title: String,
    val from: String,
    val to: String,
    val legs: List<JourneyLegDto>,
) {
    fun toDomain() = Journey(title = title, from = from, to = to, legs = legs.map { it.toDomain() })
}

@Serializable
data class TicketDto(
    val id: String,
    val trip: TripDto,
    val seatLabels: List<String>,
    val passengerFullName: String,
    val passengerType: String,
    val discountIdImagePath: String? = null,
    val coPassengers: List<CoPassengerDto> = emptyList(),
    val infantCount: Int = 0,
    val paymentStatus: String,
    val qrPayload: String,
    val reservationExpiresAtEpochMillis: Long? = null,
    /** CONFIRMED | CANCELLED | REFUNDED; absent means CONFIRMED (older servers). */
    val status: String? = null,
    val clientReference: String? = null,
) {
    fun toDomain() = Ticket(
        id = id,
        trip = trip.toDomain(),
        seatLabels = seatLabels,
        primaryPassenger = Passenger(
            fullName = passengerFullName,
            type = passengerType.toPassengerType(),
            discountIdImagePath = discountIdImagePath,
        ),
        coPassengers = coPassengers.map { it.toDomain() },
        infantCount = infantCount,
        paymentStatus = paymentStatus.toPaymentStatus(),
        qrPayload = qrPayload,
        reservationExpiresAtEpochMillis = reservationExpiresAtEpochMillis,
        status = status.toBookingStatus(),
        clientReference = clientReference,
    )

    companion object {
        /** For the ticket cache: the mocks and the real client share one on-disk shape. */
        fun from(ticket: Ticket) = TicketDto(
            id = ticket.id,
            trip = TripDto.from(ticket.trip),
            seatLabels = ticket.seatLabels,
            passengerFullName = ticket.primaryPassenger.fullName,
            passengerType = ticket.primaryPassenger.type.name,
            discountIdImagePath = ticket.primaryPassenger.discountIdImagePath,
            coPassengers = ticket.coPassengers.map { CoPassengerDto.from(it) },
            infantCount = ticket.infantCount,
            paymentStatus = ticket.paymentStatus.name,
            qrPayload = ticket.qrPayload,
            reservationExpiresAtEpochMillis = ticket.reservationExpiresAtEpochMillis,
            status = ticket.status.name,
            clientReference = ticket.clientReference,
        )
    }
}
