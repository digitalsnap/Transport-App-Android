# CLAUDE.md — RideVibe Android

Rules for anyone working on this repo with Claude Code. Read the whole file before
changing anything. The developer's instructions in the session win over this file;
this file wins over Claude's defaults.

---

## 1. What this app is

**RideVibe** is a Philippine inter-city transport booking app for Android. Passengers
search bus, ferry and fastcraft trips between terminals and seaports, pick seats,
pay, and receive a QR ticket to show the conductor or port staff. The same APK also
carries an in-app **staff console** used by RideVibe admins and partner operators
(bus and shipping companies).

Product facts that shape the code. Do not "fix" these as if they were bugs:

- **Three ride kinds:** `BUS`, `FERRY`, `FASTCRAFT` (`RideKind` in core-domain).
  Bus trips sell **chosen seats** through the seat map. Ferries and fastcrafts sell
  **passage**: the app auto-assigns one space per passenger (`P1…Pn`) and skips the
  seat map. Fastcraft seating is allocated at the port.
- **Bus classes:** `ORDINARY`, `DELUXE`, `LUXURY`. Sea services have no bus class.
- **Passengers:** adults and children take a seat, infants ride on a lap and take
  none. `PassengerType` is `REGULAR`, `STUDENT`, `SENIOR_CITIZEN`, `PWD`; the three
  discounted types carry a 20% regulated fare discount and require a **discount ID
  photo** captured in checkout (CameraX + ML Kit OCR).
- **Payment:** `GCASH`, `QR_PH`, `CARD`, `CASH_ON_BOARD`. Cash on board still issues
  a QR ticket; the conductor collects the fare.
- **Round trips:** Home stores the search in a shared `BookingCart`. The outbound
  seat pick loops back to Results for the return leg (origin and destination
  swapped), then one itemised checkout covers both legs.
- **Booking for someone else** is supported (`forSelf` flag). When booking for self,
  the account holder is the primary passenger.
- **Staff console:** reached from My Profile. Session role `ADMIN` lands on the Admin
  Console (Overview, Support, Trips, Partners, Data); role `PARTNER` lands on the
  Partner Portal (Overview, My services, Trips, Manifest). Admins may open a
  read-only Partner Portal for any operator.
- **Terminals** are Philippine hubs (Cubao, Pasay, PITX, seaports such as Batangas
  and Calapan). The seed corpus lives in `docs/seed/`. Do not invent locations.
- **Sign-in:** Google and Facebook OAuth on the Welcome screen. Facebook needs an
  App ID that is not configured in this repo; the button shows a toast explaining
  that. Leave it that way until the ID is registered.

---

## 2. Git and GitHub rules (strict)

Commits and pushes are **manual and developer-owned**. There are **no pull
requests** in this workflow.

**Claude MUST NOT, on its own initiative:**

- run `git commit`, `git push`, `git merge`, `git rebase`, `git reset --hard`,
  `git checkout -- <file>`, `git stash drop`, `git clean`, or delete branches
- create, edit, merge, close or comment on pull requests, by any means (`gh`,
  the GitHub API, the desktop app's PR tools, or web automation)
- enable auto-merge, create GitHub releases, or change repo settings
- push to `main` under any circumstances
- force-push any branch
- amend or rewrite commits that already exist

**What Claude MAY do:**

- read history freely (`git status`, `git diff`, `git log`, `git blame`, `git show`)
- stage files and create a commit **only when the developer explicitly asks for a
  commit in that same message**, using the branch that is already checked out
- push **only when the developer explicitly asks for a push in that same message**,
  and only the current branch to `origin` with a plain `git push`

"Explicitly asks" means the developer wrote the words commit or push. A request
to "finish", "wrap up", "ship it" or "save" is **not** a request to commit or push.
Approval given for one commit does not carry over to the next.

When a task is done, end by telling the developer which files changed and suggest a
commit message. They will commit and push themselves.

The `gh` CLI is not installed on the primary machine. Do not install it and do not
work around its absence.

Remote: `https://github.com/digitalsnap/Transport-App-Android`. Default branch is
`main`. Feature work happens on a named branch (currently `scaffold-ridevibe-app`).

---

## 3. Other hard restrictions

- **Secrets.** Never read, print, commit, or copy `keystore.properties`,
  `local.properties`, `*.jks`, `*.keystore`, `firebase-service-account.json`, or
  any file that holds a token, password, or key. Never paste values from them into
  chat, code, or docs. `keystore.properties.template` is the only file of that
  family that belongs in git.
- **Do not delete the mock package** `core-network/.../mock/`. It powers the app
  with no backend and is a permanent dev asset. Removing it is a product decision
  the developer makes, not a cleanup.
- **Do not flip `android.nonTransitiveRClass`** in `gradle.properties`. It must
  stay `false`: the Facebook SDK's androidx.startup initializer crashes with
  non-transitive R classes.
- **Do not change `ridevibe.useMocks` or the API base URLs** in
  `gradle.properties` as a side effect of another task. Override per build on the
  command line instead (see section 6).
- **Do not run destructive Gradle or ADB commands** (`adb uninstall`, wiping
  emulator data, deleting non-build directories) unless asked.
- **Do not add dependencies without saying so.** New libraries go through
  `gradle/libs.versions.toml` with a pinned version and a one-line comment saying
  what they are for. Never inline a coordinate in a module's `build.gradle.kts`.
- **Do not bump `ridevibe.versionCode` / `versionName`** unless asked. Those drive
  Firebase App Distribution and duplicates are rejected.
- **Do not change the API contracts unilaterally.** See section 5.
- **Do not download assets or code from the internet into the repo** (fonts,
  images, jars) without asking first.
- **No secrets, credentials, or personal data in logs.** The logging interceptor
  stays debug-only.
- **Do not rewrite files in bulk** (mass reformatting, import reordering across
  modules). Keep diffs scoped to the task so manual review stays possible.

---

## 4. Architecture

Kotlin 1.9, Jetpack Compose (Material 3), Hilt, Retrofit + OkHttp + kotlinx
serialization, Coroutines/Flow, Navigation Compose. `compileSdk 34`, `minSdk 24`,
JVM target 17, AGP 8.5.

Modules (all listed in `settings.gradle.kts`):

| Module | Role | Depends on |
|---|---|---|
| `app` | Entry point, `MainActivity`, `RideVibeNavGraph`, bottom nav, and the screens that have no feature module yet: Welcome, Profile, Bookings, Itinerary, Wallet, Support/Chat | everything below |
| `core-domain` | Pure Kotlin. Models, repository **interfaces**, use cases, `BookingCart` session state. **No Android imports.** Source root is `src/main/kotlin` | nothing |
| `core-network` | Retrofit services (`CrsApiService` passenger, `StaffApiService` staff), DTOs, mappers, real repository impls, the **mock** package, Hilt modules (`NetworkModule`, `RepositoryModule`), staff session store | `core-domain` |
| `feature-search` | Home, Explore, Results screens and view models | `core-domain`, `core-network` |
| `feature-seatmap` | Seat map with live seat events over WebSocket | same |
| `feature-checkout` | Passenger forms, discount ID capture (CameraX, ML Kit OCR, `DiscountIdImageStore`), payment selection, confirm | same |
| `feature-ticket` | Ticket display with QR (ZXing), share | same |
| `feature-admin` | Staff console: `StaffConsoleRoot`, `StaffLoginScreen`, `admin/` tabs, `partner/` tabs | same |

Rules that follow from this layout:

- **Dependency direction is one way:** app → feature-* → core-network → core-domain.
  Feature modules never depend on each other. If two features need the same thing,
  it goes in `core-domain` (pure) or `core-network` (Android/network).
- **Navigation lives only in `app`** (`RideVibeNavGraph.kt`). Feature screens take
  lambdas (`onBack`, `onTripSelected`, …) and never touch `NavController`.
- **Screens are stateless composables fed by a Hilt `ViewModel`** exposing a single
  `StateFlow<XxxUiState>` data class. Events are public functions on the view model.
  No business logic in composables.
- **Repositories are interfaces in `core-domain`**, chosen in `RepositoryModule`
  by the `USE_MOCK_DATA` BuildConfig flag: `if (USE_MOCK_DATA) mock.get() else real.get()`.
  Profile, Wallet, Support and Itinerary are **mock-only** today (marked `TODO` in
  that module) because the backend has no contract for them yet.
- **Every real repository needs a mock twin** in the `mock` package, and the mock
  must be good enough to demo the full flow offline.
- **Source roots:** `core-domain` uses `src/main/kotlin`; every other module uses
  `src/main/java`. Match the module you are in.
- Package root is `com.ridevibe.<module>`.

Navigation reference: read `RideVibeNavGraph.kt`. Route strings are private
constants in that file. The passenger bottom nav shows on every screen except
Welcome and the staff console.

---

## 5. API contracts and the backend

The backend (**CRS**: Fastify 5 + TypeScript + Postgres) is a **separate repo** kept
outside this one on purpose. On the primary machine it is at
`D:\The Bus App\Database-RideVibe\Database-RideVibe-App`. Do not assume it exists on
every developer's machine; ask before touching it.

Two OpenAPI files are mirrored in both repos and must stay byte-identical
(backend copies use CRLF line endings):

| This repo | Backend | Android consumer |
|---|---|---|
| `docs/api/openapi.yaml` | `spec/openapi.yaml` | `CrsApiService` (`/v1/*`, seat-events WebSocket, `X-Device-Id` header) |
| `docs/api/staff-openapi.yaml` | `spec/staff-openapi.yaml` | `StaffApiService` (`/auth/*`, `/admin/api/*`, `/partner/api/*`, `x-admin-token` / `x-partner-token`) |

**The backend owns the specs.** When changing `CrsApiService`, `StaffApiService`, a
DTO, or a spec:

1. Edit the spec in `docs/api/` here and the Kotlin side together.
2. Tell the developer the backend copy in `spec/` must be updated and its contract
   tests (`pnpm test`) rerun. Do that copy only if asked.
3. Never let a `/v1` passenger call carry a staff token, and never let a staff call
   rely on `X-Device-Id`.

`docs/api/README.md` explains the headers, auth options and open questions.
`docs/BACKLOG.md` is the authoritative status of what the backend does and does
not yet provide. The older roadmap it references is stale; trust the backlog.

---

## 6. Build, run, verify

Run Gradle from the repo root. On the primary Windows machine `JAVA_HOME` must be
Android Studio's bundled JDK:

```bash
export JAVA_HOME="/c/Program Files/Android/Android Studio/jbr"
./gradlew assembleDebug
```

Point a debug build at a real backend without editing tracked files:

```bash
./gradlew installDebug -Pridevibe.useMocks=false -Pridevibe.apiBaseUrl.debug=http://192.168.1.5:8080/
```

Defaults in `gradle.properties`: mocks **on**, debug base URL `http://10.0.2.2:8080/`
(emulator loopback). Debug allows cleartext HTTP; release is HTTPS only.

Regenerate the seed JSON after changing the mock corpus:

```bash
./gradlew :core-network:exportSeedData
```

Before reporting a task as done:

- `./gradlew assembleDebug` must pass. For changes touching `feature-admin` or
  release config, also run `assembleRelease`.
- If you changed a screen, install on a device or emulator and walk the affected
  flow (Welcome → Home → Results → Seat Map or passage → Checkout → Ticket, or the
  staff console). Say plainly what you verified and what you did not.
- There is almost no automated test coverage (one unit test that exports seed
  data). Do not claim "tests pass" as evidence of correctness.

Release and beta distribution (Firebase App Distribution, Crashlytics, signing)
are documented in `RELEASING.md`. Claude does not run release or upload tasks.

---

## 7. Conventions

- **Kotlin official code style.** Trailing commas in multi-line parameter lists,
  named arguments for booleans and numbers at call sites.
- **Compose:** Material 3 only. Use `MaterialTheme.colorScheme` and
  `MaterialTheme.typography`; no hard-coded colors except brand marks that must be
  exact (Facebook blue is the one existing case). `ExperimentalMaterial3Api` opt-in
  is already set at the compiler level. Previews are welcome but optional.
- **State:** `collectAsStateWithLifecycle` in screens. Immutable `data class`
  UI state with `_uiState.update { it.copy(...) }` in view models.
- **Strings:** user-facing copy is currently inline in composables. Keep the tone
  of existing copy (short, direct, Philippine English, "conductor" not "driver").
- **Money:** Philippine pesos. Fares are `Double` today; do not change the type
  without a coordinated change across DTOs, mocks and specs.
- **Dates:** epoch millis (`Long`) travel through routes and DTOs; format only in UI.
- **Comments:** explain *why*, especially product rules (see the existing comments
  in the nav graph about sea passage and round trips). Do not add comments that
  restate the code.
- **TODOs:** every `TODO` names what unblocks it (a backend endpoint, a credential,
  a decision). No bare `TODO`.
- **Mock data** must remain realistic: real operator names, real terminals, plausible
  fares and schedules. It is what testers and demos see.

---

## 8. Where to look

| Need | Location |
|---|---|
| Screen-to-screen flow | `app/.../navigation/RideVibeNavGraph.kt` |
| Domain rules (discounts, seat status, ride kinds) | `core-domain/.../model/` |
| Which repo impl is live | `core-network/.../di/RepositoryModule.kt` |
| Passenger API | `core-network/.../api/CrsApiService.kt`, `docs/api/openapi.yaml` |
| Staff API | `core-network/.../api/StaffApiService.kt`, `docs/api/staff-openapi.yaml` |
| Mock backend | `core-network/.../mock/` |
| Seed corpus (45 terminals, 388 routes, 696 services, 73 operators) | `docs/seed/` |
| Project status and drift from roadmap | `docs/BACKLOG.md` |
| Beta release runbook | `RELEASING.md` |
| Backend scaffold prompt | `docs/prompts/phase0-backend-scaffold.md` |

---

## 9. How to work in a session

1. Read the relevant screen, its view model, the repository interface and the mock
   before editing. Most product rules are in comments there.
2. State assumptions up front when a request is ambiguous about bus vs sea, one-way
   vs round trip, or passenger vs staff.
3. Keep the change inside the module that owns it. If a change needs a new
   cross-module type, put it in `core-domain` and say so.
4. Build. Run the flow if UI changed. Report exactly what was verified.
5. List changed files and a suggested commit message. Stop. The developer commits
   and pushes.
