package com.ridevibe.core.network.dto

import com.ridevibe.core.domain.model.BookingStatus
import com.ridevibe.core.domain.model.BusClass
import com.ridevibe.core.domain.model.LocationKind
import com.ridevibe.core.domain.model.PassengerType
import com.ridevibe.core.domain.model.PaymentMethod
import com.ridevibe.core.domain.model.PaymentStatus
import com.ridevibe.core.domain.model.RefundStatus
import com.ridevibe.core.domain.model.RideKind
import com.ridevibe.core.domain.model.SeatStatus
import com.ridevibe.core.domain.model.StaffRole
import com.ridevibe.core.network.log.RideVibeLog

/*
 * Lenient enum parsing for every DTO, passenger and staff alike: a screen
 * must never crash because the server grew a new value. Unknown strings fall
 * back to the SAFE default for that field and are logged once per occurrence
 * so the drift is visible in logcat:
 *
 *  - seat status → OCCUPIED (unbookable: never offer a seat we cannot read)
 *  - paymentStatus → CASH_ON_BOARD (unpaid: the conductor collects rather than
 *    the app claiming a fare was paid)
 *  - busClass → ORDINARY, passengerType → REGULAR (no discount claimed),
 *    booking status → CONFIRMED, refund → NONE, role → PARTNER (least power).
 *
 * A null/absent value is simply the default — nothing to log.
 */

internal inline fun <reified E : Enum<E>> String?.toEnumOr(default: E, field: String): E {
    val raw = this?.trim()?.takeIf { it.isNotEmpty() } ?: return default
    val match = enumValues<E>().firstOrNull { it.name.equals(raw, ignoreCase = true) }
    if (match == null) RideVibeLog.w("Unknown $field '$raw' from the CRS; using ${default.name}")
    return match ?: default
}

internal fun String?.toBusClass(): BusClass = toEnumOr(BusClass.ORDINARY, "busClass")
internal fun String?.toRideKind(): RideKind = toEnumOr(RideKind.BUS, "rideKind")
internal fun String?.toLocationKind(): LocationKind = toEnumOr(LocationKind.CITY, "locationKind")
internal fun String?.toPassengerType(): PassengerType = toEnumOr(PassengerType.REGULAR, "passengerType")
internal fun String?.toPaymentMethod(): PaymentMethod = toEnumOr(PaymentMethod.CASH_ON_BOARD, "paymentMethod")
internal fun String?.toPaymentStatus(): PaymentStatus = toEnumOr(PaymentStatus.CASH_ON_BOARD, "paymentStatus")
internal fun String?.toSeatStatusLenient(): SeatStatus = toEnumOr(SeatStatus.OCCUPIED, "seatStatus")
internal fun String?.toBookingStatus(): BookingStatus = toEnumOr(BookingStatus.CONFIRMED, "bookingStatus")
internal fun String?.toRefundStatus(): RefundStatus = toEnumOr(RefundStatus.NONE, "refundStatus")
internal fun String?.toStaffRole(): StaffRole = toEnumOr(StaffRole.PARTNER, "role")
