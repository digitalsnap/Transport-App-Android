# Seed corpus

Generated from the app's mock data layer. **Do not hand-edit** — regenerate:

```bash
./gradlew :core-network:exportSeedData
```

Source of truth is `core-network/src/main/java/com/ridevibe/core/network/mock/`
(`MockDatabase.kt` for curated data, `XlsxSeedData.kt` for the workbook import).
The exporter is a unit test, so the JSON always matches the Kotlin it came from;
if the mock changes and this isn't regenerated, the diff shows up in review.

## Contents

| File | Records |
|---|---|
| `terminals.json` | 45 hubs — 17 Metro Manila & Luzon, 14 Visayas, 14 Mindanao |
| `routes.json` | 388 directed routes (288 land, 100 sea), 692 services, 72 operators |
| `journeys.json` | 4 curated multi-leg journeys, 7 legs |

## Notes for whoever builds the backend schema

**Routes are exported in both directions.** The mock registers `A→B` and `B→A`
as separate entries with identical service lists. A backend may collapse
symmetric pairs into one row with a bidirectional flag — verify symmetry across
the whole file before doing so, since the merge step in `MockDatabase.mergeRoutes`
can add generated services to one direction.

**`rating: 4.2` is a placeholder** wherever a service came from the workbook
import — the spreadsheet carries no ratings. Don't seed these as real operator
ratings; treat them as null.

**Fares are indicative published fares** researched from operator sites and
aggregators (Victory Liner, Genesis/JoyBus, DLTB, JAM, Partas, Five Star, Solid
North, Isarog) in July 2026. They are a starting point for a fare table, not
authoritative pricing. Real fares must come from the operators.

**`departureHours` is an hour-of-day list, not a schedule.** The mock synthesises
concrete departures from it per requested date. A real schedule needs days-of-week,
effective dates, and per-service exceptions.

**Terminal records have no coordinates.** Roadmap Phase 2 (boarding points, live
tracking) needs lat/lng — that has to be sourced separately.

**Nothing here covers vehicles or seat layouts.** The mock generates seat maps
procedurally at runtime rather than seeding them, so 2x2 / 2x1 layouts must be
modelled fresh backend-side.
