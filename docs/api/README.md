# CRS API spec

`openapi.yaml` is the contract between the Android client and the backend that
doesn't exist yet. It was derived from `CrsApiService.kt` and the DTOs in
`core-network`, and verified to cover all 12 client endpoints exactly — no
extras, nothing missing.

## Viewing it

```bash
npx --yes @redocly/cli preview-docs docs/api/openapi.yaml
```

## Validating it

```bash
npx --yes @redocly/cli lint docs/api/openapi.yaml
```

## Keeping it honest

The spec is only useful if it can't silently drift from the client. Two things
worth adding before backend work starts:

1. **A contract test in the app's CI** that parses this file and asserts every
   `@GET`/`@POST` path in `CrsApiService` appears in it, and vice versa. The
   check has already been run manually once; automating it is the point.
2. **Generate, don't hand-write.** Once the backend owns the spec, generate the
   Retrofit interface from it rather than maintaining both by hand.

## Read this before building the backend

`x-open-questions` at the bottom of the spec lists 9 unresolved issues, 2 of
them blockers (authentication, and the four unspecified domains). They are
decisions, not tasks — settle them before writing endpoints, because several
change the shape of the schema.

`x-websocket` documents the seat-inventory channel, which OpenAPI 3.0 can't
express. Note the path drift against the roadmap flagged there.
