package com.ridevibe.feature.search.viewmodel

import com.ridevibe.core.domain.format.PhTime
import com.ridevibe.core.domain.format.formatPhp
import com.ridevibe.core.domain.model.FareCalculator
import com.ridevibe.core.domain.model.PassengerType
import com.ridevibe.core.domain.model.Trip
import com.ridevibe.core.domain.model.spaceNoun

/** Sort orders shared by Results and Explore so the chips read the same on both screens. */
enum class SortOption(val label: String) {
    EARLIEST("Earliest"),
    CHEAPEST("Cheapest"),
    FASTEST("Fastest"),
}

fun List<Trip>.sortedBy(option: SortOption): List<Trip> = when (option) {
    SortOption.EARLIEST -> sortedBy { it.departureEpochMillis }
    SortOption.CHEAPEST -> sortedWith(compareBy({ it.farePhp }, { it.departureEpochMillis }))
    SortOption.FASTEST -> sortedWith(
        compareBy({ it.arrivalEpochMillis - it.departureEpochMillis }, { it.departureEpochMillis }),
    )
}

/** Time-of-day buckets for the departure filter, in Asia/Manila hours. */
enum class DepartureWindow(val label: String, private val hours: IntRange) {
    MORNING("Morning", 5..11),
    AFTERNOON("Afternoon", 12..16),
    EVENING("Evening", 17..20),
    NIGHT("Night", 21..28); // 21:00 through 04:59; hours past midnight are offset by 24 below

    fun contains(epochMillis: Long): Boolean {
        val hour = PhTime.hourOf(epochMillis)
        return hour in hours || (hour + 24) in hours
    }
}

/** How a trip can serve the party: [SHORT] means fewer free seats than passengers. */
enum class TripAvailability { OPEN, LOW, SHORT, SOLD_OUT }

/**
 * One row of a trip list, pre-computed by the view model so the card only
 * renders strings: availability against the party, per-seat fare, and the
 * undiscounted total (the regulated discount is only known at checkout,
 * once passenger types and IDs are captured).
 */
data class TripListItem(
    val trip: Trip,
    val seatCount: Int,
    val availability: TripAvailability,
    val availabilityLabel: String,
    val fareLabel: String,
    val totalLabel: String,
    val ctaLabel: String,
    /** Total across both legs when an outbound leg is already in the cart (return-leg results). */
    val combinedTotalLabel: String? = null,
) {
    val isBookable: Boolean get() = availability == TripAvailability.OPEN || availability == TripAvailability.LOW

    companion object {
        /** Seats at or below this count get the Gold low-seat warning. */
        private const val LOW_SEAT_THRESHOLD = 10

        fun of(trip: Trip, seatCount: Int, outboundFarePhp: Double? = null): TripListItem {
            val seats = seatCount.coerceAtLeast(1)
            val availability = when {
                trip.availableSeatCount <= 0 -> TripAvailability.SOLD_OUT
                trip.availableSeatCount < seats -> TripAvailability.SHORT
                trip.availableSeatCount <= LOW_SEAT_THRESHOLD -> TripAvailability.LOW
                else -> TripAvailability.OPEN
            }
            val noun = trip.rideKind.spaceNoun(plural = trip.availableSeatCount != 1)
            val availabilityLabel = when (availability) {
                TripAvailability.SOLD_OUT -> "Sold out"
                TripAvailability.SHORT -> "Only ${trip.availableSeatCount} $noun left"
                TripAvailability.LOW, TripAvailability.OPEN -> "${trip.availableSeatCount} $noun left"
            }
            val passengers = List(seats) { PassengerType.REGULAR }
            val quote = FareCalculator.quote(trip.farePhp, passengers)
            val combined = outboundFarePhp?.let { FareCalculator.quote(it, passengers, returnFarePerSeatPhp = trip.farePhp) }
            return TripListItem(
                trip = trip,
                seatCount = seats,
                availability = availability,
                availabilityLabel = availabilityLabel,
                fareLabel = formatPhp(trip.farePhp, showCentavos = false),
                totalLabel = "Total for $seats passenger${if (seats == 1) "" else "s"}: " +
                    formatPhp(quote.totalPhp, showCentavos = false),
                ctaLabel = when {
                    availability == TripAvailability.SOLD_OUT -> "Sold out"
                    trip.rideKind.sellsPassage -> "Book passage"
                    else -> "View seats"
                },
                combinedTotalLabel = combined?.let { "Both legs: ${formatPhp(it.totalPhp, showCentavos = false)}" },
            )
        }
    }
}
