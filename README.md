# RideVibe — Android

RideVibe is a Philippine inter-city transport booking app. Passengers search bus,
ferry and fastcraft trips between terminals and seaports, pick seats (bus) or
passage (sea), pay, and receive a QR ticket to show the conductor or port staff.
The same APK carries an in-app staff console for RideVibe admins and partner
operators (bus and shipping companies).

**Status:** the app runs on an in-app mock backend by default
(`ridevibe.useMocks=true`). The real CRS backend (Fastify + Postgres) lives in a
separate repo; its API contracts are mirrored in `docs/api/`.

## Modules

| Module | Role |
|---|---|
| `app` | Entry point, `MainActivity`, `RideVibeNavGraph`, bottom nav, and screens without a feature module yet (Welcome, Profile, Bookings, Itinerary, Wallet, Support/Chat) |
| `core-domain` | Pure Kotlin: models, repository interfaces, use cases, `BookingCart` session state. No Android imports |
| `core-network` | Retrofit services (`CrsApiService`, `StaffApiService`), DTOs, mappers, real repository impls, the mock backend, Hilt modules |
| `feature-search` | Home, Explore, Results |
| `feature-seatmap` | Seat map with live seat events over WebSocket |
| `feature-checkout` | Passenger forms, discount ID capture (CameraX + ML Kit OCR), payment, confirm |
| `feature-ticket` | Ticket display with QR, share |
| `feature-admin` | Staff console: Admin Console and Partner Portal |

Dependency direction is one way: `app` → `feature-*` → `core-network` → `core-domain`.
Feature modules never depend on each other. Navigation lives only in `app`.

Stack: Kotlin 1.9, Jetpack Compose (Material 3), Hilt, Retrofit + OkHttp +
kotlinx serialization, Coroutines/Flow. `compileSdk 34`, `minSdk 24`, JVM 17.

## Build

Run Gradle from the repo root. On Windows, `JAVA_HOME` must point at Android
Studio's bundled JDK (Git Bash shown):

```bash
export JAVA_HOME="/c/Program Files/Android/Android Studio/jbr"
./gradlew assembleDebug
```

Mocks are on by default. Point a debug build at a real CRS backend without
editing tracked files:

```bash
./gradlew installDebug -Pridevibe.useMocks=false -Pridevibe.apiBaseUrl.debug=http://192.168.1.5:8080/
```

Defaults in `gradle.properties`: debug base URL `http://10.0.2.2:8080/`
(emulator loopback). Debug allows cleartext HTTP; release is HTTPS only.

Other useful tasks:

```bash
./gradlew testDebugUnitTest :core-domain:test       # unit tests, incl. the API contract test
./gradlew :app:lintDebug                             # lint against app/lint-baseline.xml
./gradlew :core-network:exportSeedData               # regenerate docs/seed/*.json from the mock corpus
```

CI (`.github/workflows/android.yml`) runs the same steps and fails if the seed
JSON drifts from the Kotlin mock.

## Docs

- [`CLAUDE.md`](CLAUDE.md) — product rules, architecture and working conventions
- [`docs/BACKLOG.md`](docs/BACKLOG.md) — what the backend does and does not yet provide
- [`docs/api/README.md`](docs/api/README.md) — passenger and staff API specs, headers, auth
- [`RELEASING.md`](RELEASING.md) — beta release runbook (signing, Firebase App Distribution)
