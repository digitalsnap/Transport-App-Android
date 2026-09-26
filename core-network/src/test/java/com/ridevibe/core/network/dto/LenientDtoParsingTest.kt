package com.ridevibe.core.network.dto

import com.ridevibe.core.domain.model.BookingStatus
import com.ridevibe.core.domain.model.BusClass
import com.ridevibe.core.domain.model.LocationKind
import com.ridevibe.core.domain.model.PassengerType
import com.ridevibe.core.domain.model.PaymentMethod
import com.ridevibe.core.domain.model.PaymentStatus
import com.ridevibe.core.domain.model.RideKind
import com.ridevibe.core.domain.model.SeatStatus
import com.ridevibe.core.domain.model.StaffRole
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * The server may grow enum values before the app does. Every DTO must
 * degrade to the SAFE default instead of crashing a screen.
 */
class LenientDtoParsingTest {

    private val json = Json {
        ignoreUnknownKeys = true
        coerceInputValues = true
    }

    @Test
    fun `unknown bus class becomes ORDINARY and missing busClass defaults`() {
        val trip = json.decodeFromString(
            TripDto.serializer(),
            """{"id":"T1","operatorName":"Victory Liner","origin":"Cubao","destination":"Baguio",
               "departureEpochMillis":1,"arrivalEpochMillis":2,"busClass":"SLEEPER","farePhp":485.0,
               "availableSeatCount":3,"rideKind":"HOVERCRAFT"}""",
        ).toDomain()
        assertEquals(BusClass.ORDINARY, trip.busClass)
        assertEquals(RideKind.BUS, trip.rideKind)

        val noClass = json.decodeFromString(
            TripDto.serializer(),
            """{"id":"T2","operatorName":"x","origin":"a","destination":"b","departureEpochMillis":1,
               "arrivalEpochMillis":2,"farePhp":1.0,"availableSeatCount":0}""",
        ).toDomain()
        assertEquals(BusClass.ORDINARY, noClass.busClass)
    }

    @Test
    fun `unknown seat status is OCCUPIED — never bookable`() {
        val seat = json.decodeFromString(
            SeatDto.serializer(),
            """{"id":"1A","label":"1A","row":1,"column":1,"status":"OCUPIED","lockExpiresAtEpochMillis":99}""",
        ).toDomain()
        assertEquals(SeatStatus.OCCUPIED, seat.status)
        assertEquals(99L, seat.lockExpiresAtEpochMillis)

        val event = json.decodeFromString(
            SeatStatusEventDto.serializer(),
            """{"tripId":"T1","seatId":"1A","status":"RESERVED"}""",
        ).toDomain()
        assertEquals(SeatStatus.OCCUPIED, event.status)

        val adminSeat = json.decodeFromString(
            AdminSeatDto.serializer(),
            """{"id":"s1","label":"1A","status":"???"}""",
        ).toDomain()
        assertEquals(SeatStatus.OCCUPIED, adminSeat.status)
    }

    @Test
    fun `known values still parse case-insensitively`() {
        assertEquals(SeatStatus.LOCKED, "locked".toSeatStatusLenient())
        assertEquals(BusClass.LUXURY, " Luxury ".toBusClass())
        assertEquals(PassengerType.PWD, "pwd".toPassengerType())
    }

    @Test
    fun `ticket with unknown passenger and payment values degrades to no discount and unpaid`() {
        val ticket = json.decodeFromString(TicketDto.serializer(), ticketJson(passengerType = "TEACHER", paymentStatus = "PENDING"))
            .toDomain()
        assertEquals(PassengerType.REGULAR, ticket.primaryPassenger.type)
        assertEquals(PaymentStatus.CASH_ON_BOARD, ticket.paymentStatus)
        assertEquals(BookingStatus.CONFIRMED, ticket.status)
        assertNull(ticket.clientReference)
    }

    @Test
    fun `ticket status and client reference round-trip`() {
        val ticket = json.decodeFromString(
            TicketDto.serializer(),
            ticketJson(status = "REFUNDED", clientReference = "attempt-7"),
        ).toDomain()
        assertEquals(BookingStatus.REFUNDED, ticket.status)
        assertEquals("attempt-7", ticket.clientReference)

        val unknownStatus = json.decodeFromString(TicketDto.serializer(), ticketJson(status = "ARCHIVED")).toDomain()
        assertEquals(BookingStatus.CONFIRMED, unknownStatus.status)

        val encoded = json.encodeToString(TicketDto.serializer(), TicketDto.from(ticket))
        assertEquals(ticket, json.decodeFromString(TicketDto.serializer(), encoded).toDomain())
    }

    @Test
    fun `co-passenger with unknown type is REGULAR`() {
        val co = json.decodeFromString(CoPassengerDto.serializer(), """{"firstName":"A","lastName":"B","type":"CHILD"}""").toDomain()
        assertEquals(PassengerType.REGULAR, co.type)
    }

    @Test
    fun `location kind falls back to CITY`() {
        val location = json.decodeFromString(LocationDto.serializer(), """{"name":"Somewhere","kind":"AIRPORT"}""").toDomain()
        assertEquals(LocationKind.CITY, location.kind)
    }

    @Test
    fun `staff booking with unknown enums lands on the safe side`() {
        val booking = json.decodeFromString(
            SupportBookingDto.serializer(),
            """{"id":"b1","status":"LOST","refundStatus":"MAYBE","passengerType":"VIP","paymentMethod":"CRYPTO",
               "paymentStatus":"AUTHORISED","busClass":"SLEEPER","rideKind":"PLANE"}""",
        ).toDomain()
        assertEquals(BookingStatus.CONFIRMED, booking.status)
        assertEquals(PassengerType.REGULAR, booking.passengerType)
        assertEquals(PaymentMethod.CASH_ON_BOARD, booking.paymentMethod)
        assertEquals(PaymentStatus.CASH_ON_BOARD, booking.paymentStatus)
        assertEquals(BusClass.ORDINARY, booking.busClass)
        assertEquals(RideKind.BUS, booking.rideKind)
    }

    @Test
    fun `session expiry is anchored to sign-in time and google client id survives`() {
        val session = json.decodeFromString(
            StaffSessionDto.serializer(),
            """{"token":"s_abc","email":"admin@admin.com","role":"ADMIN","expiresInDays":7}""",
        ).toDomain(nowEpochMillis = 1_000L)
        assertEquals(1_000L + 7 * 86_400_000L, session.expiresAtEpochMillis)
        assertEquals(StaffRole.ADMIN, session.role)

        val noExpiry = json.decodeFromString(
            StaffSessionDto.serializer(),
            """{"token":"s_abc","email":"p@p.com","role":"OWNER"}""",
        ).toDomain()
        assertNull(noExpiry.expiresAtEpochMillis)
        assertEquals(StaffRole.PARTNER, noExpiry.role)

        val config = json.decodeFromString(
            StaffAuthConfigDto.serializer(),
            """{"googleEnabled":true,"googleClientId":"123.apps.googleusercontent.com","emailKeyEnabled":false}""",
        ).toDomain()
        assertEquals("123.apps.googleusercontent.com", config.googleClientId)
    }

    @Test
    fun `booking request always carries the client reference`() {
        val body = json.encodeToString(
            BookingRequestDto.serializer(),
            BookingRequestDto(
                seatIds = listOf("1A"),
                passengerFullName = "Juan",
                passengerType = "REGULAR",
                paymentMethod = "GCASH",
                clientReference = "attempt-1",
            ),
        )
        assert(body.contains("\"clientReference\":\"attempt-1\"")) { body }
    }

    private fun ticketJson(
        passengerType: String = "REGULAR",
        paymentStatus: String = "PAID",
        status: String? = null,
        clientReference: String? = null,
    ): String = buildString {
        append("""{"id":"RV-1","trip":{"id":"T1","operatorName":"x","origin":"a","destination":"b",""")
        append(""""departureEpochMillis":1,"arrivalEpochMillis":2,"busClass":"DELUXE","farePhp":1.0,"availableSeatCount":0},""")
        append(""""seatLabels":["1A"],"passengerFullName":"Juan","passengerType":"$passengerType",""")
        append(""""paymentStatus":"$paymentStatus","qrPayload":"RIDEVIBE|RV-1"""")
        if (status != null) append(""","status":"$status"""")
        if (clientReference != null) append(""","clientReference":"$clientReference"""")
        append("}")
    }
}
