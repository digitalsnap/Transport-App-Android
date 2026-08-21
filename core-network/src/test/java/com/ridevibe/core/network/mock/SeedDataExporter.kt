package com.ridevibe.core.network.mock

import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Exports the mock corpus — terminals, route services, and curated journeys — to
 * JSON for the CRS backend to seed from. Run it with:
 *
 *     ./gradlew :core-network:exportSeedData
 *
 * which writes to docs/seed/. This is a test rather than a script so the export
 * always reflects the Kotlin source it was generated from: if the mock data
 * changes and the JSON isn't regenerated, the diff shows up in review.
 *
 * Output goes to the directory named by the `ridevibe.seedOutDir` system
 * property (set in core-network/build.gradle.kts), never straight into the
 * source tree.
 */
class SeedDataExporter {

    @Serializable
    private data class TerminalJson(
        val name: String,
        val isCentralTerminal: Boolean,
        val kind: String,
        val region: String,
        val description: String,
    )

    @Serializable
    private data class ServiceJson(
        val operatorName: String,
        val busClass: String,
        val farePhp: Double,
        val rating: Double,
        val departureHours: List<Int>,
        val durationMinutes: Long,
        val rideKind: String,
    )

    @Serializable
    private data class RouteJson(
        val origin: String,
        val destination: String,
        val mode: String,
        val services: List<ServiceJson>,
    )

    @Serializable
    private data class LegJson(
        val kind: String,
        val from: String,
        val to: String,
        val durationMinutes: Long,
        val indicativeFarePhp: Double,
        val note: String? = null,
    )

    @Serializable
    private data class JourneyJson(
        val title: String,
        val from: String,
        val to: String,
        val matchKeywords: List<String>,
        val legs: List<LegJson>,
    )

    private val json = Json { prettyPrint = true; encodeDefaults = true }

    @Test
    fun exportSeedCorpus() {
        val outDir = File(
            System.getProperty("ridevibe.seedOutDir")
                ?: error("ridevibe.seedOutDir not set — run via ./gradlew :core-network:exportSeedData")
        )
        outDir.mkdirs()

        val db = MockDatabase()

        // ── Terminals ───────────────────────────────────────────────────────
        val terminals = db.hubs
            .map {
                TerminalJson(
                    name = it.name,
                    isCentralTerminal = it.isCentralTerminal,
                    kind = it.kind.name,
                    region = it.region,
                    description = it.description,
                )
            }
            .sortedWith(compareBy({ it.region }, { it.name }))
        write(outDir, "terminals.json", terminals)

        // ── Routes ──────────────────────────────────────────────────────────
        // Exported faithfully, both directions, because that is how the mock
        // registers them. A backend may collapse symmetric pairs into one row
        // with a bidirectional flag — verify symmetry before doing so.
        fun routesOf(
            source: Map<Pair<String, String>, List<MockDatabase.RouteService>>,
            mode: String,
        ) = source.map { (pair, services) ->
            RouteJson(
                origin = pair.first,
                destination = pair.second,
                mode = mode,
                services = services
                    .map {
                        ServiceJson(
                            operatorName = it.operatorName,
                            busClass = it.busClass.name,
                            farePhp = it.farePhp,
                            rating = it.rating,
                            departureHours = it.departureHours,
                            durationMinutes = it.durationMinutes,
                            rideKind = it.kind.name,
                        )
                    }
                    .sortedWith(compareBy({ it.operatorName }, { it.busClass }, { it.farePhp })),
            )
        }

        val routes = (routesOf(db.allBusRoutes, "LAND") + routesOf(db.allSeaRoutes, "SEA"))
            .sortedWith(compareBy({ it.mode }, { it.origin }, { it.destination }))
        write(outDir, "routes.json", routes)

        // ── Curated journeys ────────────────────────────────────────────────
        val journeys = db.journeySeeds
            .map { (keywords, journey) ->
                JourneyJson(
                    title = journey.title,
                    from = journey.from,
                    to = journey.to,
                    matchKeywords = keywords,
                    legs = journey.legs.map {
                        LegJson(
                            kind = it.kind.name,
                            from = it.from,
                            to = it.to,
                            durationMinutes = it.durationMinutes,
                            indicativeFarePhp = it.indicativeFarePhp,
                            note = it.note,
                        )
                    },
                )
            }
            .sortedWith(compareBy({ it.to }, { it.title }))
        write(outDir, "journeys.json", journeys)

        // Guard against an export that silently produces nothing.
        assertTrue("no terminals exported", terminals.isNotEmpty())
        assertTrue("no routes exported", routes.isNotEmpty())
        assertTrue("no journeys exported", journeys.isNotEmpty())

        println(
            "Seed export -> ${outDir.absolutePath}\n" +
                "  terminals.json  ${terminals.size} hubs\n" +
                "  routes.json     ${routes.size} directed routes, " +
                "${routes.sumOf { it.services.size }} services\n" +
                "  journeys.json   ${journeys.size} curated journeys"
        )
    }

    private inline fun <reified T> write(dir: File, name: String, value: T) {
        File(dir, name).writeText(json.encodeToString(value) + "\n")
    }
}
