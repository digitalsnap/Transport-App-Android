# Pending backend changes

Spec edits made on the Android side that the backend repo
(`Database-RideVibe-App`, `spec/openapi.yaml`) has not adopted yet. The backend
owns the specs (CLAUDE.md section 5): port each item below into `spec/`, keep
the file byte-identical to `docs/api/openapi.yaml` here (CRLF on the backend
copy), rerun `pnpm test` (contract tests), and delete the item from this list.

`docs/api/staff-openapi.yaml` is unchanged. The staff DTOs now *read*
`Session.expiresInDays` and `AuthConfig.googleClientId` (both already in the
staff spec) but the spec did not need editing.

## 1. `BookingRequest.clientReference` (required) — idempotent booking

**File:** `docs/api/openapi.yaml`, `components.schemas.BookingRequest`
(added to `required`, new `clientReference` property, `maxLength: 64`); new
`x-open-questions` entry `idempotent-booking`.

**Why:** a rider on a flaky provincial connection can lose the `201` response
of `POST /v1/trips/{tripId}/book` after the server booked. The client now
generates a UUID once per checkout attempt (`newClientReference()` in
core-domain), sends it as `clientReference`, and reuses it on retry.

**Backend must:** store it per `(user, clientReference)`, dedupe for 24 hours,
and answer a repeat with the ticket already issued (same body, `200` or
`201`). Until then a retry books twice. **If the zod body schema is `.strict()`,
the extra key is a `400` today — this is the one change that breaks the live
flow, so port it first.**

## 2. `Ticket.status` and `Ticket.clientReference` (optional)

**File:** `openapi.yaml`, `components.schemas.Ticket` — new optional `status`
(`BookingStatus`: `CONFIRMED | CANCELLED | REFUNDED`, absent = `CONFIRMED`)
and nullable `clientReference` (the key from item 1, echoed back). New schema
`BookingStatus`.

**Why:** My Bookings and the ticket screen must show that support cancelled or
refunded a ticket, or that an unpaid cash-on-board reservation lapsed, instead
of a still-valid-looking QR. The client is lenient: an absent or unknown value
reads as `CONFIRMED`, so an older server keeps working.

**Backend must:** include `status` on every Ticket it returns (`/book`,
`/tickets/{id}`, `/bookings`), reflecting admin cancel/refund and the cash
reservation sweep.

## 3. `POST …/seats/{seatId}/lock` may return `200` + `SeatLockResponse`

**File:** `openapi.yaml`, lock path (new `200` response beside the existing
`204`); new schema `SeatLockResponse` (all fields optional:
`seatId`, `lockedByUserId`, `lockExpiresAtEpochMillis`); `x-open-questions`
`hold-ttl` marked `resolved-client-side`.

**Why:** the client only learnt a hold's expiry from the socket event. It now
reads `lockExpiresAtEpochMillis` from the lock response when present
(`SeatRepository.lockSeat` returns `Result<Long?>`), and assumes the documented
10-minute TTL on a `204`. **No backend change is required for the app to
work** — this records that a `200` body is now understood, so the server can
start sending it whenever convenient.

## 4. `Seat.lockExpiresAtEpochMillis` (optional, nullable)

**File:** `openapi.yaml`, `components.schemas.Seat`.

**Why:** after a WebSocket reconnect the client re-fetches `GET
/v1/trips/{tripId}/seatmap`; carrying the expiry on held seats lets it restore
the countdown for its own hold without waiting for the next event. Optional;
absent means unknown.

## 5. Enum leniency documented (no wire change)

**File:** `openapi.yaml`, descriptions of `BusClass`, `PassengerType`,
`PaymentStatus`, `SeatStatus`; `x-open-questions` `strict-enums` marked
`resolved`; `/v1/bus-classes` description.

**Why:** the client no longer crashes on an unknown enum value. Fallbacks are
the safe direction — `busClass → ORDINARY`, `passengerType → REGULAR`,
`paymentStatus → CASH_ON_BOARD`, seat status → `OCCUPIED` (previously
`AVAILABLE`, which could have put a misspelled sold seat back on sale). Every
fallback is logged under the `RideVibe` tag. The enum *values* are unchanged;
only prose moved. Contract tests that compare descriptions will need the
refreshed text.

## 6. `POST /partner/api/manifest/{tripId}/check-in` — boarding check-in (new endpoint)

**File:** `docs/api/staff-openapi.yaml` (not edited yet — this item is a request,
no client-side spec change has been made). Consumer: `PartnerApiService` /
`PartnerRepository`, feature-admin Manifest tab.

**Why:** the partner Manifest tab now scans the rider's QR ticket (CameraX +
ML Kit) and marks the booking as boarded. There is no endpoint for that, so
boarded state lives in `feature-admin/.../manifest/ManifestCheckInStore`
(SharedPreferences on the conductor's phone). Two devices scanning the same
departure cannot see each other's check-ins, and a reinstall forgets them.

**Backend should provide:**

- `POST /partner/api/manifest/{tripId}/check-in` with `x-partner-token` /
  partner session. Request body: `{ ticketId: string, seatLabels: string[],
  scannedAtEpochMillis: number }`. Response `200`: the updated `ManifestEntry`
  with a new `boarded: boolean` and `boardedAtEpochMillis: number | null`.
  `409` when already boarded (body: the existing entry), `404` when the
  ticket is not on that trip's manifest, `410` when the booking is cancelled.
- `ManifestEntry` gains `boarded` / `boardedAtEpochMillis` so
  `GET /partner/api/bookings` (the manifest feed) returns the marks and the "Boarded x / y" count
  is server-truth. Optional but useful: `contactNumber` on the entry — the tab
  shows the booking id per row today because the model has no phone number.

When it lands: replace the store with the repository call, keep the local
store only as an offline queue, and remove the `TODO(backend)` in
`ManifestCheckInStore.kt`.

## 7. `POST /partner/api/trips/{id}/onsite` — QR payload and passenger types

**File:** `docs/api/staff-openapi.yaml` (request, no spec edit made).
Consumer: `PartnerTripsViewModel.recordOnsiteSale`.

**Why (a):** the response (`OnsiteSaleIssued`) carries only `ticketId` and
`seatLabels`, so the app cannot render a QR for a counter sale. The sale
dialog prints the ticket id large with the caption "QR not available until the
API returns a payload". Please add `qrPayload: string` (same
`RIDEVIBE|…` format the rider ticket uses) so the counter can show or print a
scannable ticket that the Manifest check-in (item 6) will accept.

**Why (b):** the request has no per-passenger fare type. The Trips tab lets
the counter pick REGULAR / STUDENT / SENIOR_CITIZEN / PWD per passenger and
shows the 20%-discounted amount to collect, but can only send it by appending
the rider-QR tags to `passengerFullName` (`Maria Santos (SR+R)`). Add
`passengers: [{ fullName, type: PassengerType }]` (or at least
`passengerTypes: PassengerType[]`) to the request and stop reading tags out of
the name; the fare the server records should apply the discount.
