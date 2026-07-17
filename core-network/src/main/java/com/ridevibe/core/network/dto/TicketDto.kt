package com.ridevibe.core.network.dto

import com.ridevibe.core.domain.model.BusClass
import com.ridevibe.core.domain.model.PassengerType
import com.ridevibe.core.domain.model.PaymentStatus
import com.ridevibe.core.domain.model.Ticket
import com.ridevibe.core.domain.model.Trip
import kotlinx.serialization.Serializable

@Serializable
data class BookingRequestDto(
    val passengerFullName: String,
    val passengerType: String,
    val discountIdImagePath: String? = null,
    val paymentStatus: String,
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
    )
}

@Serializable
data class TicketDto(
    val id: String,
    val trip: TripDto,
    val seatLabel: String,
    val passengerFullName: String,
    val passengerType: String,
    val discountIdImagePath: String? = null,
    val paymentStatus: String,
    val qrPayload: String,
) {
    fun toDomain() = Ticket(
        id = id,
        trip = trip.toDomain(),
        seatLabel = seatLabel,
        passenger = com.ridevibe.core.domain.model.Passenger(
            fullName = passengerFullName,
            type = PassengerType.valueOf(passengerType.uppercase()),
            discountIdImagePath = discountIdImagePath,
        ),
        paymentStatus = PaymentStatus.valueOf(paymentStatus.uppercase()),
        qrPayload = qrPayload,
    )
}
