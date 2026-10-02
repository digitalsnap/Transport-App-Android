package com.ridevibe.feature.ticket.format

import com.ridevibe.core.domain.format.PhTime
import com.ridevibe.core.domain.model.PassengerType
import com.ridevibe.core.domain.model.RideKind
import com.ridevibe.core.domain.model.Ticket
import com.ridevibe.core.domain.model.displayLabel

/**
 * Every string the ticket screen, the share sheet and the exported PNG show
 * about a [Ticket], in one place so they cannot drift apart. Pure functions;
 * dates are Philippine time (departures are PH departures whatever the
 * device zone).
 */
object TicketFormatter {
    private const val DATE_PATTERN = "MMM d, yyyy"
    private const val TIME_PATTERN = "hh:mm a"

    fun dateLabel(epochMillis: Long): String = PhTime.formatDateTime(epochMillis, DATE_PATTERN)

    fun timeLabel(epochMillis: Long): String = PhTime.formatDateTime(epochMillis, TIME_PATTERN)

    /** Class name plus the primary passenger's fare type, e.g. "Deluxe • Senior" or "Tourist Class". */
    fun classLabel(ticket: Ticket): String {
        val base = ticket.trip.busClass.displayLabel(ticket.trip.rideKind)
        return when (ticket.primaryPassenger.type) {
            PassengerType.REGULAR -> base
            PassengerType.STUDENT -> "$base • Student"
            PassengerType.SENIOR_CITIZEN -> "$base • Senior"
            PassengerType.PWD -> "$base • PWD"
        }
    }

    fun passengerTypeLabel(type: PassengerType): String = when (type) {
        PassengerType.REGULAR -> "Regular Passenger"
        PassengerType.STUDENT -> "Student Passenger"
        PassengerType.SENIOR_CITIZEN -> "Senior Citizen"
        PassengerType.PWD -> "PWD Passenger"
    }

    fun coPassengerTypeLabel(type: PassengerType): String = when (type) {
        PassengerType.REGULAR -> "Regular"
        PassengerType.STUDENT -> "Student"
        PassengerType.SENIOR_CITIZEN -> "Senior Citizen"
        PassengerType.PWD -> "PWD"
    }

    /** "Seat" for buses; sea passage assigns spaces (P1…Pn), never seats. */
    fun seatNoun(rideKind: RideKind, plural: Boolean = false): String = when {
        rideKind.sellsPassage -> if (plural) "Spaces" else "Space"
        else -> if (plural) "Seats" else "Seat"
    }

    /** "Seat 2A", "Seats 2A, 2B", "Space P1". */
    fun seatLabel(ticket: Ticket): String =
        "${seatNoun(ticket.trip.rideKind, plural = ticket.seatLabels.size > 1)} ${ticket.seatLabels.joinToString(", ")}"

    /** Who scans the QR: the conductor on a bus, port staff at the pier. */
    fun staffNoun(rideKind: RideKind): String = if (rideKind.sellsPassage) "the port staff" else "the conductor"

    fun countdown(totalSeconds: Long): String {
        val hours = totalSeconds / 3600
        val minutes = (totalSeconds % 3600) / 60
        val seconds = totalSeconds % 60
        return "%02d:%02d:%02d".format(hours, minutes, seconds)
    }

    /**
     * Leg names by route shape, not departure order: the leg that runs the
     * first ticket's route backwards is the return. Anything else (a
     * multi-city itinerary someday) is numbered.
     */
    fun legLabel(index: Int, ticket: Ticket?, first: Ticket?): String = when {
        index == 0 -> "Outbound"
        ticket != null && first != null &&
            ticket.trip.origin == first.trip.destination && ticket.trip.destination == first.trip.origin -> "Return"
        else -> "Leg ${index + 1}"
    }

    /**
     * What the share sheet sends alongside the PNG. Deliberately names only
     * the primary passenger: co-passengers did not consent to having their
     * names forwarded to whoever the rider messages.
     */
    fun shareText(tickets: List<Ticket>, legLabels: List<String>): String = buildString {
        tickets.forEachIndexed { index, ticket ->
            if (tickets.size > 1) appendLine(legLabels.getOrNull(index)?.uppercase() ?: "LEG ${index + 1}")
            appendLine("RideVibe Ticket ${ticket.id}")
            appendLine("${ticket.trip.origin} → ${ticket.trip.destination}")
            appendLine("${dateLabel(ticket.trip.departureEpochMillis)} • ${timeLabel(ticket.trip.departureEpochMillis)}")
            appendLine("${seatLabel(ticket)} • ${ticket.trip.operatorName}")
            appendLine("Passenger: ${ticket.primaryPassenger.fullName}")
            val extra = ticket.coPassengers.size
            if (extra > 0) appendLine("+ $extra co-passenger${if (extra == 1) "" else "s"}")
            if (ticket.infantCount > 0) appendLine("Infants (free): ${ticket.infantCount}")
            if (index < tickets.size - 1) appendLine()
        }
    }

    /** Safe file stem for exports: ticket ids are server-issued and may carry slashes or spaces. */
    fun fileStem(ticket: Ticket): String =
        "ridevibe-ticket-" + ticket.id.replace(Regex("[^A-Za-z0-9._-]"), "_")
}
