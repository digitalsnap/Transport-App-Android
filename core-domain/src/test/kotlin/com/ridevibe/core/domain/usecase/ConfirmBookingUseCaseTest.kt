package com.ridevibe.core.domain.usecase

import com.ridevibe.core.domain.model.BusClass
import com.ridevibe.core.domain.model.CoPassenger
import com.ridevibe.core.domain.model.Passenger
import com.ridevibe.core.domain.model.PassengerType
import com.ridevibe.core.domain.model.PaymentMethod
import com.ridevibe.core.domain.model.PaymentStatus
import com.ridevibe.core.domain.model.Ticket
import com.ridevibe.core.domain.model.Trip
import com.ridevibe.core.domain.repository.CheckoutRepository
import kotlinx.coroutines.test.runTest
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ConfirmBookingUseCaseTest {

    private class RecordingRepository : CheckoutRepository {
        var calls = 0
        var lastClientReference: String? = null

        override suspend fun confirmBooking(
            tripId: String,
            seatIds: List<String>,
            primaryPassenger: Passenger,
            coPassengers: List<CoPassenger>,
            infantCount: Int,
            paymentMethod: PaymentMethod,
            promoCode: String?,
            clientReference: String,
        ): Result<Ticket> {
            calls++
            lastClientReference = clientReference
            return Result.success(
                Ticket(
                    id = "RV-TEST",
                    trip = trip,
                    seatLabels = seatIds,
                    primaryPassenger = primaryPassenger,
                    coPassengers = coPassengers,
                    infantCount = infantCount,
                    paymentStatus = paymentMethod.paymentStatus,
                    qrPayload = "RIDEVIBE|RV-TEST",
                    clientReference = clientReference,
                ),
            )
        }

        override suspend fun getTicket(ticketId: String): Result<Ticket> = Result.failure(NotImplementedError())
        override suspend fun getMyBookings(): Result<List<Ticket>> = Result.success(emptyList())
        override fun cachedBookings(): List<Ticket> = emptyList()
    }

    private val repository = RecordingRepository()
    private val useCase = ConfirmBookingUseCase(repository)
    private val regular = Passenger(fullName = "Juan Dela Cruz", type = PassengerType.REGULAR)

    @Test
    fun `no seats never reaches the repository`() = runTest {
        val result = useCase("T1", emptyList(), regular, emptyList(), 0, PaymentMethod.GCASH)

        assertIs<BookingValidationException>(result.exceptionOrNull())
        assertEquals(0, repository.calls)
    }

    @Test
    fun `blank seat ids count as no seats`() = runTest {
        val result = useCase("T1", listOf("", " "), regular, emptyList(), 0, PaymentMethod.GCASH)
        assertIs<BookingValidationException>(result.exceptionOrNull())
    }

    @Test
    fun `discounted primary passenger without an ID photo is refused`() = runTest {
        val student = Passenger(fullName = "Ana Cruz", type = PassengerType.STUDENT, discountIdImagePath = "")

        val result = useCase("T1", listOf("1A"), student, emptyList(), 0, PaymentMethod.CASH_ON_BOARD)

        val error = assertIs<BookingValidationException>(result.exceptionOrNull())
        assertTrue(error.message!!.contains("Ana Cruz"))
        assertEquals(0, repository.calls)
    }

    @Test
    fun `discounted co-passenger without an ID photo is refused`() = runTest {
        val senior = CoPassenger(firstName = "Lola", lastName = "Reyes", type = PassengerType.SENIOR_CITIZEN)

        val result = useCase("T1", listOf("1A", "1B"), regular, listOf(senior), 0, PaymentMethod.CARD)

        val error = assertIs<BookingValidationException>(result.exceptionOrNull())
        assertTrue(error.message!!.contains("Lola Reyes"))
    }

    @Test
    fun `valid booking passes through with its client reference`() = runTest {
        val pwd = CoPassenger(firstName = "Ben", lastName = "Tan", type = PassengerType.PWD, discountIdImagePath = "/tmp/id.jpg")

        val result = useCase(
            tripId = "T1",
            seatIds = listOf("1A", "1B"),
            primaryPassenger = regular,
            coPassengers = listOf(pwd),
            infantCount = 1,
            paymentMethod = PaymentMethod.QR_PH,
            clientReference = "attempt-1",
        )

        assertTrue(result.isSuccess)
        assertEquals(1, repository.calls)
        assertEquals("attempt-1", repository.lastClientReference)
        assertEquals("attempt-1", result.getOrThrow().clientReference)
    }

    @Test
    fun `a client reference is generated when the caller passes none`() = runTest {
        useCase("T1", listOf("1A"), regular, emptyList(), 0, PaymentMethod.GCASH)
        assertTrue(!repository.lastClientReference.isNullOrBlank())
        assertNull(repository.lastClientReference?.takeIf { it.length < 32 })
    }

    private companion object {
        val trip = Trip(
            id = "T1",
            operatorName = "Victory Liner",
            origin = "Cubao",
            destination = "Baguio",
            departureEpochMillis = 1L,
            arrivalEpochMillis = 2L,
            busClass = BusClass.ORDINARY,
            farePhp = 485.0,
            availableSeatCount = 10,
        )
    }
}
