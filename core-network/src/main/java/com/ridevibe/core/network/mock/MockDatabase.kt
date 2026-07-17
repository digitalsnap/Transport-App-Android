package com.ridevibe.core.network.mock

import com.ridevibe.core.domain.model.BusClass
import com.ridevibe.core.domain.model.CoPassenger
import com.ridevibe.core.domain.model.Passenger
import com.ridevibe.core.domain.model.PaymentMethod
import com.ridevibe.core.domain.model.PaymentStatus
import com.ridevibe.core.domain.model.Seat
import com.ridevibe.core.domain.model.SeatStatus
import com.ridevibe.core.domain.model.SeatStatusEvent
import com.ridevibe.core.domain.model.TerminalLocation
import com.ridevibe.core.domain.model.Ticket
import com.ridevibe.core.domain.model.Trip
import com.ridevibe.core.domain.model.UserProfile
import com.ridevibe.core.domain.model.Vehicle
import kotlinx.coroutines.flow.MutableSharedFlow
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import kotlin.random.Random
import javax.inject.Inject
import javax.inject.Singleton

// ═════════════════════════════════════════════════════════════════════════════
// MOCK DATA LAYER — DELETE BEFORE GOING LIVE
//
// In-memory stand-in for the real CRS backend. To go live: delete this package
// and set USE_MOCK_DATA = false in di/RepositoryModule.kt.
//
// Fares are indicative published fares researched July 2026 from operator
// sites/aggregators (Victory Liner, Genesis/JoyBus, DLTB, JAM, Partas,
// Five Star, Solid North, Isarog). Real pricing must come from the CRS.
// ═════════════════════════════════════════════════════════════════════════════

@Singleton
class MockDatabase @Inject constructor() {

    // ── Terminals & routes ──────────────────────────────────────────────────

    private val centralTerminals = listOf("Cubao", "Pasay", "PITX")

    private data class RouteService(
        val operatorName: String,
        val busClass: BusClass,
        val farePhp: Double,
        val rating: Double,
        val departureHours: List<Int>,
        val durationMinutes: Long,
    )

    /** (origin, destination) → services. Reverse directions are auto-registered. */
    private val routeServices: Map<Pair<String, String>, List<RouteService>> = buildMap {
        fun route(origin: String, destination: String, vararg services: RouteService) {
            put(origin to destination, services.toList())
            put(destination to origin, services.toList()) // buses run both ways
        }

        route(
            "Cubao", "Baguio",
            RouteService("Victory Liner", BusClass.ORDINARY, 485.0, 4.5, listOf(5, 9, 13, 21), 360),
            RouteService("Genesis Transport", BusClass.DELUXE, 795.0, 4.6, listOf(6, 10, 14, 22), 330),
            RouteService("JoyBus Premiere", BusClass.LUXURY, 1065.0, 4.8, listOf(7, 11, 23), 285),
        )
        route(
            "Pasay", "Baguio",
            RouteService("Victory Liner", BusClass.ORDINARY, 485.0, 4.5, listOf(6, 10, 22), 375),
            RouteService("JoyBus Executive", BusClass.DELUXE, 1005.0, 4.7, listOf(8, 12, 23), 300),
            RouteService("Victory Liner Royal Class", BusClass.LUXURY, 1546.0, 4.9, listOf(9, 23), 285),
        )
        route(
            "PITX", "Baguio",
            RouteService("Solid North Transit", BusClass.DELUXE, 950.0, 4.6, listOf(7, 13, 21), 315),
            RouteService("Solid North Luxury P2P", BusClass.LUXURY, 1250.0, 4.8, listOf(8, 22), 285),
        )
        route(
            "PITX", "Batangas Port",
            RouteService("DLTB Co.", BusClass.ORDINARY, 250.0, 4.3, listOf(5, 7, 9, 11, 13, 15, 17), 150),
            RouteService("JAM Liner", BusClass.ORDINARY, 230.0, 4.2, listOf(6, 8, 10, 12, 14, 16, 18), 150),
        )
        route(
            "Pasay", "Batangas Port",
            RouteService("DLTB Co.", BusClass.ORDINARY, 230.0, 4.3, listOf(5, 8, 11, 14, 17), 165),
            RouteService("JAM Liner", BusClass.ORDINARY, 230.0, 4.2, listOf(6, 9, 12, 15, 18), 165),
        )
        route(
            "Cubao", "Olongapo",
            RouteService("Victory Liner", BusClass.ORDINARY, 280.0, 4.4, listOf(5, 7, 9, 12, 15, 18), 210),
            RouteService("Victory Liner Deluxe", BusClass.DELUXE, 350.0, 4.5, listOf(8, 13, 17), 195),
        )
        route(
            "PITX", "Naga",
            RouteService("DLTB Co.", BusClass.ORDINARY, 850.0, 4.3, listOf(7, 17, 20), 540),
            RouteService("Isarog Elite", BusClass.DELUXE, 1150.0, 4.6, listOf(18, 21), 510),
            RouteService("Isarog Sleeper", BusClass.LUXURY, 1850.0, 4.8, listOf(20, 22), 480),
        )
        route(
            "Cubao", "Naga",
            RouteService("DLTB Co.", BusClass.ORDINARY, 850.0, 4.3, listOf(6, 16, 19), 540),
            RouteService("DLTB Lazyboy", BusClass.LUXURY, 1600.0, 4.7, listOf(20, 22), 495),
        )
        route(
            "Cubao", "Dagupan",
            RouteService("Five Star", BusClass.ORDINARY, 585.0, 4.4, listOf(5, 8, 11, 14, 17, 20), 270),
            RouteService("Five Star Deluxe", BusClass.DELUXE, 700.0, 4.6, listOf(7, 13, 19), 255),
        )
        route(
            "Cubao", "Vigan",
            RouteService("Partas", BusClass.ORDINARY, 950.0, 4.4, listOf(7, 20, 22), 480),
            RouteService("Partas Deluxe", BusClass.DELUXE, 1100.0, 4.6, listOf(21, 23), 450),
        )
        route(
            "Cubao", "Laoag",
            RouteService("Partas", BusClass.ORDINARY, 1150.0, 4.4, listOf(19, 21), 570),
            RouteService("Fariñas First Class", BusClass.LUXURY, 1450.0, 4.7, listOf(20, 22), 540),
        )
        route(
            "Cubao", "Tuguegarao",
            RouteService("Victory Liner", BusClass.ORDINARY, 1300.0, 4.4, listOf(18, 20), 600),
            RouteService("Five Star", BusClass.DELUXE, 1250.0, 4.5, listOf(19, 21), 585),
        )
    }

    /** Fallback services so ANY searched route still returns demo inventory. */
    private val fallbackServices = listOf(
        RouteService("Genesis Transport", BusClass.LUXURY, 850.0, 4.8, listOf(6, 14), 285),
        RouteService("Victory Liner", BusClass.DELUXE, 620.0, 4.5, listOf(8, 16), 345),
        RouteService("Philtranco", BusClass.ORDINARY, 550.0, 4.2, listOf(9, 17), 420),
    )

    // ── "Tables" ────────────────────────────────────────────────────────────

    private val trips = ConcurrentHashMap<String, Trip>()
    private val seatMaps = ConcurrentHashMap<String, MutableList<Seat>>()
    private val tickets = ConcurrentHashMap<String, Ticket>()
    private var profile: UserProfile = UserProfile()
    private val vehicles = ConcurrentHashMap<String, Vehicle>()

    /** Live seat updates, mimicking the WebSocket `seat_status_changed` channel. */
    val seatEvents = MutableSharedFlow<SeatStatusEvent>(extraBufferCapacity = 32)

    init {
        seedPastBookings()
    }

    /** Two completed sample bookings so the history section has demo content. */
    private fun seedPastBookings() {
        val now = System.currentTimeMillis()

        val baguioTrip = Trip(
            id = "TRIP-PAST-BAGUIO",
            operatorName = "Victory Liner",
            origin = "Cubao",
            destination = "Baguio",
            departureEpochMillis = now - 14L * 86_400_000L,
            arrivalEpochMillis = now - 14L * 86_400_000L + 360 * 60_000L,
            busClass = BusClass.ORDINARY,
            farePhp = 485.0,
            availableSeatCount = 0,
            operatorRating = 4.5,
        )
        trips[baguioTrip.id] = baguioTrip
        tickets["RV-HIST0001"] = Ticket(
            id = "RV-HIST0001",
            trip = baguioTrip,
            seatLabels = listOf("8C"),
            primaryPassenger = Passenger(fullName = "Juan Dela Cruz", type = com.ridevibe.core.domain.model.PassengerType.REGULAR),
            paymentStatus = PaymentStatus.PAID,
            qrPayload = "RIDEVIBE|RV-HIST0001|${baguioTrip.id}|8C|Juan Dela Cruz(R)|PAID",
        )

        val batangasTrip = Trip(
            id = "TRIP-PAST-BATANGAS",
            operatorName = "JAM Liner",
            origin = "PITX",
            destination = "Batangas Port",
            departureEpochMillis = now - 32L * 86_400_000L,
            arrivalEpochMillis = now - 32L * 86_400_000L + 150 * 60_000L,
            busClass = BusClass.ORDINARY,
            farePhp = 230.0,
            availableSeatCount = 0,
            operatorRating = 4.2,
        )
        trips[batangasTrip.id] = batangasTrip
        tickets["RV-HIST0002"] = Ticket(
            id = "RV-HIST0002",
            trip = batangasTrip,
            seatLabels = listOf("3C", "3D"),
            primaryPassenger = Passenger(fullName = "Juan Dela Cruz", type = com.ridevibe.core.domain.model.PassengerType.REGULAR),
            coPassengers = listOf(CoPassenger(firstName = "Maria", lastName = "Dela Cruz")),
            paymentStatus = PaymentStatus.PAID,
            qrPayload = "RIDEVIBE|RV-HIST0002|${batangasTrip.id}|3C+3D|Juan Dela Cruz(R)+Maria Dela Cruz(R)|PAID",
        )
    }

    fun allTickets(): List<Ticket> =
        tickets.values.sortedByDescending { it.trip.departureEpochMillis }

    // ── Reference data ──────────────────────────────────────────────────────

    fun locations(): List<TerminalLocation> {
        val destinations = routeServices.keys.flatMap { listOf(it.first, it.second) }.toSortedSet()
        return destinations.map { name ->
            TerminalLocation(name = name, isCentralTerminal = name in centralTerminals)
        }.sortedByDescending { it.isCentralTerminal }
    }

    fun availableBusClasses(): List<BusClass> =
        routeServices.values.flatten().map { it.busClass }.distinct().sorted()

    // ── Trips ───────────────────────────────────────────────────────────────

    fun searchTrips(origin: String, destination: String, dateMillis: Long, busClass: BusClass?): List<Trip> {
        val key = normalize(origin) to normalize(destination)
        val services = routeServices.entries
            .firstOrNull { (route, _) -> normalize(route.first) == key.first && normalize(route.second) == key.second }
            ?.value
            ?: fallbackServices
        val dayStart = dateMillis - (dateMillis % 86_400_000L)

        return services
            .filter { busClass == null || it.busClass == busClass }
            .flatMap { service ->
                service.departureHours.map { hour ->
                    val departure = dayStart + hour * 3_600_000L
                    val id = "TRIP-${(origin + destination + service.operatorName + departure).hashCode().toUInt()}"
                    val trip = Trip(
                        id = id,
                        operatorName = service.operatorName,
                        origin = origin,
                        destination = destination,
                        departureEpochMillis = departure,
                        arrivalEpochMillis = departure + service.durationMinutes * 60_000L,
                        busClass = service.busClass,
                        farePhp = service.farePhp,
                        availableSeatCount = seatMap(id).count { it.status == SeatStatus.AVAILABLE },
                        operatorRating = service.rating,
                    )
                    trips[id] = trip
                    trip
                }
            }
            .sortedBy { it.departureEpochMillis }
    }

    fun getTrip(tripId: String): Trip? = trips[tripId]

    private fun normalize(value: String) = value.trim().lowercase()

    // ── Seats ───────────────────────────────────────────────────────────────

    /** 10 rows × (A,B | aisle | C,D); occupancy seeded by tripId so it's stable. */
    fun seatMap(tripId: String): List<Seat> = seatMaps.getOrPut(tripId) {
        val random = Random(tripId.hashCode())
        val columnsToLetters = listOf(1 to "A", 2 to "B", 3 to "C", 4 to "D")
        (1..10).flatMap { row ->
            columnsToLetters.map { (column, letter) ->
                val roll = random.nextFloat()
                Seat(
                    id = "$row$letter",
                    label = "$row$letter",
                    row = row,
                    column = column,
                    status = when {
                        roll < 0.25f -> SeatStatus.OCCUPIED
                        roll < 0.30f -> SeatStatus.LOCKED
                        else -> SeatStatus.AVAILABLE
                    },
                    lockedByUserId = if (roll in 0.25f..0.30f) "other-passenger" else null,
                )
            }
        }.toMutableList()
    }

    fun updateSeat(tripId: String, seatId: String, status: SeatStatus, lockedBy: String?): Boolean {
        val seats = seatMaps[tripId] ?: return false
        val index = seats.indexOfFirst { it.id == seatId }
        if (index == -1) return false
        seats[index] = seats[index].copy(status = status, lockedByUserId = lockedBy)
        seatEvents.tryEmit(
            SeatStatusEvent(tripId = tripId, seatId = seatId, status = status, lockedByUserId = lockedBy),
        )
        return true
    }

    fun availableSeats(tripId: String): List<Seat> =
        seatMaps[tripId]?.filter { it.status == SeatStatus.AVAILABLE } ?: emptyList()

    // ── Tickets ─────────────────────────────────────────────────────────────

    fun createTicket(
        tripId: String,
        seatIds: List<String>,
        primaryPassenger: Passenger,
        coPassengers: List<CoPassenger>,
        infantCount: Int,
        paymentMethod: PaymentMethod,
    ): Ticket? {
        val trip = trips[tripId] ?: return null
        val seatLabels = seatIds.map { seatId ->
            seatMaps[tripId]?.firstOrNull { it.id == seatId }?.label ?: seatId
        }
        val ticketId = "RV-${UUID.randomUUID().toString().take(8).uppercase()}"
        val status = paymentMethod.paymentStatus

        // One QR carrying the whole manifest: seats, every passenger + fare type, status.
        fun typeTag(type: com.ridevibe.core.domain.model.PassengerType) = when (type) {
            com.ridevibe.core.domain.model.PassengerType.REGULAR -> "R"
            com.ridevibe.core.domain.model.PassengerType.STUDENT -> "ST"
            com.ridevibe.core.domain.model.PassengerType.SENIOR_CITIZEN -> "SR"
            com.ridevibe.core.domain.model.PassengerType.PWD -> "PWD"
        }
        val manifest = buildString {
            append("RIDEVIBE|").append(ticketId).append('|').append(tripId)
            append('|').append(seatLabels.joinToString("+"))
            append('|').append(primaryPassenger.fullName).append('(').append(typeTag(primaryPassenger.type)).append(')')
            coPassengers.forEach {
                append('+').append(it.firstName).append(' ').append(it.lastName)
                append('(').append(typeTag(it.type)).append(')')
            }
            if (infantCount > 0) append("|INF:").append(infantCount)
            append('|').append(status.name)
        }

        val ticket = Ticket(
            id = ticketId,
            trip = trip,
            seatLabels = seatLabels,
            primaryPassenger = primaryPassenger,
            coPassengers = coPassengers,
            infantCount = infantCount,
            paymentStatus = status,
            qrPayload = manifest,
            reservationExpiresAtEpochMillis = if (status == PaymentStatus.CASH_ON_BOARD) {
                System.currentTimeMillis() + 20 * 60_000L
            } else {
                null
            },
        )
        tickets[ticketId] = ticket
        seatIds.forEach { updateSeat(tripId, it, SeatStatus.OCCUPIED, lockedBy = null) }
        return ticket
    }

    fun getTicket(ticketId: String): Ticket? = tickets[ticketId]

    // ── Profile & vehicles ──────────────────────────────────────────────────

    fun getProfile(): UserProfile = profile

    fun saveProfile(updated: UserProfile) {
        profile = updated
    }

    fun getVehicles(): List<Vehicle> = vehicles.values.sortedBy { it.plateNumber }

    fun addVehicle(plateNumber: String, ltoCertificateUri: String): Vehicle {
        val vehicle = Vehicle(
            id = UUID.randomUUID().toString(),
            plateNumber = plateNumber.uppercase().trim(),
            ltoCertificateUri = ltoCertificateUri,
        )
        vehicles[vehicle.id] = vehicle
        return vehicle
    }

    fun removeVehicle(vehicleId: String) {
        vehicles.remove(vehicleId)
    }
}
