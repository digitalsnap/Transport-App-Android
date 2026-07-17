package com.ridevibe.core.network.dto

import com.ridevibe.core.domain.model.BusClass
import com.ridevibe.core.domain.model.CoPassenger
import com.ridevibe.core.domain.model.Passenger
import com.ridevibe.core.domain.model.PassengerType
import com.ridevibe.core.domain.model.PaymentStatus
import com.ridevibe.core.domain.model.TerminalLocation
import com.ridevibe.core.domain.model.Ticket
import com.ridevibe.core.domain.model.Trip
import kotlinx.serialization.Serializable

@Serializable
data class LocationDto(
    val name: String,
    val isCentralTerminal: Boolean = false,
) {
    fun toDomain() = TerminalLocation(name = name, isCentralTerminal = isCentralTerminal)
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
    )
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
