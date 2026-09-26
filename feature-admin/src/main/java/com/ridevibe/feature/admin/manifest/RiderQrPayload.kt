package com.ridevibe.feature.admin.manifest

/**
 * The ticket and trip ids inside a scanned rider QR.
 *
 * Rider tickets encode `RIDEVIBE|<ticketId>|<tripId>|<seats>|<passengers>|…`
 * (see `MockDatabase.createTicket` in core-network). Support-reissued and
 * counter-sale payloads use the shorter `RIDEVIBE|v0|<ticketId>[|…]` form with
 * no trip, so [tripId] is null there and the manifest match falls back to the
 * ticket id alone.
 */
data class RiderQrPayload(val ticketId: String, val tripId: String?) {

    companion object {
        private const val PREFIX = "RIDEVIBE"
        private const val VERSIONED_MARKER = "v0"

        /** Null when the text is not a RideVibe ticket at all (another app's QR, a URL, noise). */
        fun parse(raw: String): RiderQrPayload? {
            val parts = raw.trim().split('|')
            if (parts.size < 2 || parts[0] != PREFIX) return null
            return if (parts[1] == VERSIONED_MARKER) {
                parts.getOrNull(2)?.takeIf { it.isNotBlank() }?.let { RiderQrPayload(ticketId = it, tripId = null) }
            } else {
                RiderQrPayload(ticketId = parts[1], tripId = parts.getOrNull(2)?.takeIf { it.isNotBlank() })
            }
        }
    }
}
