# CRS API specs

Two OpenAPI 3.0.3 files describe the backend the app talks to. **The backend
repo (`Database-RideVibe-App`) owns both** — its `spec/` directory is the
source of truth and its contract tests lock each spec to the implemented
routes in both directions. The copies here are for building and reviewing the
Android client; when they disagree with the backend's copies, the backend wins
and these should be refreshed.

| File | Surface | Backend test | Android consumer |
|---|---|---|---|
| `openapi.yaml` | **Passenger** contract: `/v1/*` and the seat-events WebSocket (`x-websocket`) | `test/contract.spec.ts` | `CrsApiService` in `core-network` (12 endpoints; sends `X-Device-Id`) |
| `staff-openapi.yaml` | **Staff** contract: `/auth/*` sign-in, `/admin/api/*` admin console, `/partner/api/*` operator portal | `test/staff-contract.spec.ts` | `StaffApiService` in `core-network` (the in-app staff console) |

## Passenger contract (`openapi.yaml`)

Originally derived from `CrsApiService.kt` and the DTOs in `core-network`, and
now implemented by the backend. Every path+method in it exists on the server
and no `/v1` route exists outside it. Passenger calls identify the device with
the `X-Device-Id` header (interim scheme until real auth lands — see
`x-open-questions`); authenticated responses echo the resolved user id in
`X-User-Id`, which is what to compare against `lockedByUserId`.

## Staff contract (`staff-openapi.yaml`)

The same routes the backend's web dashboards (`admin/dashboard.html`,
`admin/partner.html`) use, so the app's staff console and the dashboards are
guaranteed to agree. `StaffApiService` is built against this file. Key points
it documents:

- **Sign-in** — `POST /auth/login` (email + password), `POST /auth/email-code`
  + `/auth/email-code/verify` (6-digit emailed key), `POST /auth/google`
  (Google ID token). All return a `Session` with an `s_…` token, `role`
  (`ADMIN` | `PARTNER`) and `operatorId`. `GET /auth/config` says which options
  are available; `GET /auth/me` / `POST /auth/logout` manage the session.
- **Headers** — the session token goes in `x-admin-token` for ADMIN accounts
  (also accepted as `Authorization: Bearer`) and in `x-partner-token` for
  PARTNER accounts. These headers go on staff routes **only**; passenger
  (`/v1`) calls keep using `X-Device-Id` and must never carry a staff token.
- **Admin read-only view of `/partner/api/*`** — an admin credential may read
  any operator's partner data by adding `?operatorId=`, but every partner
  write returns 403.
- **Errors** — every error body is `{ "message": string }`; the spec lists
  each status a handler can return and the exact messages.
- **Rate limits** — `x-rate-limits` mirrors the backend's per-IP ceilings on
  the sign-in routes (429).
- **UI shells** — `GET /admin` and `GET /partner` are HTML pages, not API
  operations; they are listed under `x-ui-shells`.

Enum schemas (`BusClass`, `RideKind`, `PassengerType`, `PaymentMethod`,
`PaymentStatus`, `SeatStatus`) are the same values as the passenger contract;
the staff spec adds `BookingStatus`, `RefundStatus` and `AccountRole`.

## Viewing them

```bash
npx --yes @redocly/cli preview-docs docs/api/openapi.yaml
npx --yes @redocly/cli preview-docs docs/api/staff-openapi.yaml
```

## Validating them

```bash
npx --yes @redocly/cli lint docs/api/openapi.yaml
npx --yes @redocly/cli lint docs/api/staff-openapi.yaml
```

## Keeping them honest

The backend already runs a two-way contract test per spec, so the server
cannot drift silently. On the app side, the same discipline is worth adding:

1. **A contract test in the app's CI** that parses each file and asserts every
   `@GET`/`@POST`/`@PATCH`/`@DELETE` path in `CrsApiService` (passenger) and
   `StaffApiService` (staff) appears in the matching spec, and vice versa.
2. **Generate, don't hand-write.** Since the backend owns both specs, generate
   the Retrofit interfaces from them rather than maintaining both by hand.

## Read this before changing the passenger surface

`x-open-questions` at the bottom of `openapi.yaml` lists the unresolved issues
(authentication and the four unspecified domains are blockers). They are
decisions, not tasks — several change the shape of the schema.

`x-websocket` documents the seat-inventory channel, which OpenAPI 3.0 can't
express.
