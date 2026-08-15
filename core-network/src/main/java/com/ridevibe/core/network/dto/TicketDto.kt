package com.ridevibe.core.network.dto

import com.ridevibe.core.domain.model.BusClass
import com.ridevibe.core.domain.model.CoPassenger
import com.ridevibe.core.domain.model.Journey
import com.ridevibe.core.domain.model.JourneyLeg
import com.ridevibe.core.domain.model.Passenger
import com.ridevibe.core.domain.model.PassengerType
import com.ridevibe.core.domain.model.LocationKind
import com.ridevibe.core.domain.model.PaymentStatus
import com.ridevibe.core.domain.model.RideKind
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
        kind = runCatching { LocationKind.valueOf(kind.uppercase()) }.getOrDefault(LocationKind.CITY),
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
        type = PassengerType.valueOf(type.uppercase()),
        discountIdImagePath = discountIdImagePath,
    )
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
)

@Serializable
data class TripDto(
    val id: String,
    val operatorName: String,
    val origin: String,
    val destination: String,
    val departureEpochMillis: Long,
    val arrivalEpochMillis: Long,
    val busClass: String,
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
        busClass = BusClass.valueOf(busClass.uppercase()),
        farePhp = farePhp,
        availableSeatCount = availableSeatCount,
        operatorRating = operatorRating,
        rideKind = runCatching { RideKind.valueOf(rideKind.uppercase()) }.getOrDefault(RideKind.BUS),
    )
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
        kind = runCatching { RideKind.valueOf(kind.uppercase()) }.getOrDefault(RideKind.BUS),
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
) {
    fun toDomain() = Ticket(
        id = id,
        trip = trip.toDomain(),
        seatLabels = seatLabels,
        primaryPassenger = Passenger(
            fullName = passengerFullName,
            type = PassengerType.valueOf(passengerType.uppercase()),
            discountIdImagePath = discountIdImagePath,
        ),
        coPassengers = coPassengers.map { it.toDomain() },
        infantCount = infantCount,
        paymentStatus = PaymentStatus.valueOf(paymentStatus.uppercase()),
        qrPayload = qrPayload,
        reservationExpiresAtEpochMillis = reservationExpiresAtEpochMillis,
    )
}
