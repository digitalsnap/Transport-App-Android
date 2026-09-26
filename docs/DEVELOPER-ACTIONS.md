# Developer actions — things code alone cannot finish

Written 2026-09-26 after the full-codebase sweep. Everything that could be done
in code is done and verified (see the session summary in the commit that carries
this file). The items below need a credential, a console, a product decision or
the backend repo. Ordered by how hard they block a real release.

## A. Release blockers (Play / signing / toolchain)

1. **Upload keystore.** `keystore.properties` points at `ridevibe-upload.jks`,
   which does not exist on this machine. `assembleRelease` compiles and shrinks
   cleanly and fails only at `packageRelease`. Follow `RELEASING.md` Part 1:
   generate the keystore, back it up offline, fill `keystore.properties`.
2. **Firebase + Facebook fingerprints.** Register the release SHA-1/SHA-256 in
   Firebase and re-download `app/google-services.json`; register the key hash
   with Facebook. Create the App Distribution tester group `beta`. Install and
   authenticate `firebase-tools`.
3. **targetSdk 35/36 + Kotlin 2 migration.** All modules are on compileSdk /
   targetSdk 34, AGP 8.5.2, Kotlin 1.9.24, Compose compiler 1.5.14, Firebase BoM
   33.7.0. Play's floor is 35 (36 from Aug 2026). This is one coordinated bump
   (AGP, Gradle, Kotlin, Compose compiler plugin, Firebase BoM, CameraX, ML Kit)
   and should be done on a branch with a device pass. Decide the timing.
4. **16 KB page size.** The emulator warns "This app isn't 16 KB compatible".
   Two native libs have 4 KB ELF LOAD alignment: `libbarhopper_v3.so` (ML Kit
   barcode-scanning 17.2.0) and `libimage_processing_util_jni.so` (CameraX
   1.3.4). Newer versions fix both; bump them as part of item 3.
5. **Play listing assets.** Privacy policy URL (required: CAMERA + OCR of
   government IDs), Data Safety form, screenshots, feature graphic, 512 px icon
   PNG (the adaptive icon and legacy mipmaps are generated; export a 512 px
   version of `ic_launcher_foreground` on the charcoal background).
6. **AAB.** App Distribution is configured for APK. Play needs an AAB
   (`bundleRelease`); decide whether to keep both or switch.
7. **Release backend URL.** `gradle.properties` still points release at
   `https://api.ridevibe.example.com/`. Set the real host when the CRS is
   deployed; release refuses cleartext by design.
8. **Mocks in release.** `ridevibe.useMocks=true` applies to release too (the
   build now warns loudly). Beta on mocks is intentional; flip it per build with
   `-Pridevibe.useMocks=false` when the backend is reachable.

## B. Product decisions

9. **Passenger identity.** Google sign-in now requests an ID token and persists a
   device-local session (`PassengerSessionStore`); sign-out and delete-account
   exist. Nothing is sent to the CRS because there is no passenger account API.
   Decide the model (Firebase ID tokens matched by verified email is the
   documented option in `openapi.yaml` `x-open-questions.auth`) so `X-Device-Id`
   can be retired before go-live.
10. **Guest mode.** "Get Started" is an explicit guest path (a Guest chip with
    "Sign in" shows on Profile). Keep or remove.
11. **Payment provider(s)** for GCash / QR Ph / Card. Checkout now shows an
    honest "Payment provider not connected yet" gate before confirming, and the
    booking request carries an idempotency key. Nothing moves money. The
    `TODO(payments)` in `CheckoutScreen.kt` marks the seam.
12. **Facebook login.** `strings.xml` carries a real App ID and client token
    (contradicting CLAUDE.md §1, which says Facebook is unconfigured). Either
    update CLAUDE.md, or move both values to `gradle.properties` /
    `local.properties` + `resValue` so they leave the tracked resource file.
    The dead `"0"` sentinel branch was removed; a blank-ID guard remains.
13. **Money type.** Fares are still `Double` (centavo rounding is now
    centralised in `FareCalculator`). Move to minor units + currency before more
    schema lands in the backend.
14. **i18n.** All copy is inline English per CLAUDE.md §7. Decide when to
    extract to `strings.xml` (Filipino/Cebuano) — mechanical but large.
15. **`READ_CONTACTS` removed.** Profile now uses the permission-free contacts
    picker. Confirm this is acceptable (it avoids a Play permissions
    declaration).
16. **Tracked `google-services.json` and `scratch_welcome.png`.** The JSON is
    conventional but documented as stale in `RELEASING.md`; keep tracked and
    restrict the API key by package + SHA-1, or gitignore it. The PNG is now
    ignored by pattern but is still in history; `git rm --cached` it if you
    want it gone from the tree.

## C. Backend repo work (`Database-RideVibe-App`)

All recorded with reasons in `docs/api/PENDING-BACKEND.md`:

17. Port the passenger spec changes to `spec/openapi.yaml` and rerun `pnpm test`:
    `BookingRequest.clientReference` (idempotency, **breaks a strict zod schema
    if not ported**), `Ticket.status` / `clientReference`, `SeatLockResponse`
    on `200`, `Seat.lockExpiresAtEpochMillis`, enum-leniency prose.
18. New staff endpoint `POST /partner/api/manifest/{tripId}/check-in` and a
    `boarded` flag on `ManifestEntry`. Until it exists, boarded marks live only
    on the conductor's phone (`ManifestCheckInStore`).
19. `OnsiteSaleIssued.qrPayload` and per-passenger `type` on the onsite-sale
    request, so counter sales get a scannable ticket and regulated discounts.
20. Contracts for Profile, Wallet, Support and Itinerary (still mock-only).
21. QR signing. Ticket QR payloads are plaintext; the check-in scanner matches
    on ticket + trip id only. Needs an HMAC or signed token from the server.
22. Passenger cancel/refund endpoint (the app has no cancel action because the
    passenger spec has none).

## D. Verification you should repeat on a real device

Verified on the emulator (Pixel 10 Pro XL image, mocks): Welcome → guest →
round-trip bus search → seat hold with countdown → return leg → checkout with
merged hold timer → demo payment gate → two-leg ticket; ferry passage with cash
on board → ticket; Bookings (upcoming/past, ride-kind labels); Profile; staff
partner login → Manifest → camera scanner opens → manual "Mark boarded" → sign
out. `assembleDebug`, `:app:lintDebug`, 104 unit tests and the API contract
test pass; `assembleRelease` compiles through R8.

Not verified (needs hardware or accounts): real Google / Facebook sign-in,
discount-ID camera capture + OCR, gallery import, ticket share / save to
Photos / calendar, QR scan of a printed ticket, hold-expiry dialogs at 10
minutes, WebSocket reconnect against the live backend, dark theme, tablet
`NavigationRail`, API 24–25 devices.
