package com.ridevibe.core.network.mock

import com.ridevibe.core.domain.model.BusClass
import com.ridevibe.core.domain.model.CoPassenger
import com.ridevibe.core.domain.model.Itinerary
import com.ridevibe.core.domain.model.Journey
import com.ridevibe.core.domain.model.JourneyLeg
import com.ridevibe.core.domain.model.LocationKind
import com.ridevibe.core.domain.model.Passenger
import com.ridevibe.core.domain.model.PaymentMethod
import com.ridevibe.core.domain.model.PaymentStatus
import com.ridevibe.core.domain.model.RideKind
import com.ridevibe.core.domain.model.Seat
import com.ridevibe.core.domain.model.SeatStatus
import com.ridevibe.core.domain.model.SeatStatusEvent
import com.ridevibe.core.domain.model.SupportMessage
import com.ridevibe.core.domain.model.TerminalLocation
import com.ridevibe.core.domain.model.Ticket
import com.ridevibe.core.domain.model.Trip
import com.ridevibe.core.domain.model.UserProfile
import com.ridevibe.core.domain.model.Vehicle
import com.ridevibe.core.domain.model.WalletTransaction
import kotlinx.coroutines.flow.MutableSharedFlow
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import kotlin.random.Random
import javax.inject.Inject
import javax.inject.Singleton

// ═════════════════════════════════════════════════════════════════════════════
// MOCK DATA LAYER — DELETE BEFORE GOING LIVE
//
// In-memory stand-in for the real CRS backend. To go live: delete this package
// and set USE_MOCK_DATA = false in di/RepositoryModule.kt.
//
// Fares are indicative published fares researched July 2026 from operator
// sites/aggregators (Victory Liner, Genesis/JoyBus, DLTB, JAM, Partas,
// Five Star, Solid North, Isarog). Real pricing must come from the CRS.
// ═════════════════════════════════════════════════════════════════════════════

@Singleton
class MockDatabase @Inject constructor() {

    // ── Terminals & routes ──────────────────────────────────────────────────

    // Hub registry: major integrated bus terminals, regional gateways, and
    // principal passenger seaports across the archipelago. Names that also
    // appear in routeServices (Cubao, Pasay, PITX, Batangas Port, Naga) must
    // keep those exact strings so hub picks resolve to seeded corridors.
    private val hubs: List<TerminalLocation> = buildList {
        val luzon = "Metro Manila & Luzon"
        val visayas = "Visayas"
        val mindanao = "Mindanao"

        fun busTerminal(name: String, region: String, description: String, central: Boolean = false) =
            add(TerminalLocation(name, central, LocationKind.BUS_TERMINAL, region, description))

        fun seaport(name: String, region: String, description: String) =
            add(TerminalLocation(name, false, LocationKind.SEAPORT, region, description))

        // Metro Manila & Luzon — integrated bus terminals
        busTerminal("PITX", luzon, "Parañaque Integrated Terminal Exchange — south to Cavite, Batangas, Bicol, Visayas, Mindanao", central = true)
        busTerminal("Cubao", luzon, "EDSA-Cubao terminal complex (Victory Liner, Five Star, Genesis) — Northern & Central Luzon", central = true)
        busTerminal("Pasay", luzon, "EDSA/Tramo terminals — long-haul south to Bicol and the Visayas", central = true)
        busTerminal("Avenida", luzon, "Sampaloc, Manila — Central Luzon routes (Pampanga, Pangasinan)")
        busTerminal("NLET Bocaue", luzon, "North Luzon Express Terminal — Northern Luzon transfer hub decongesting Metro Manila")
        busTerminal("Valenzuela Gateway", luzon, "VGBC — Pampanga, Bulacan, Tarlac, and Northern Luzon")
        busTerminal("Santa Rosa SRIT", luzon, "Santa Rosa Integrated Transport Terminal, Laguna")
        busTerminal("Alabang VTX", luzon, "Vista Terminal Exchange, Muntinlupa")
        busTerminal("Naga", luzon, "Naga City Terminal, Camarines Sur — Bicol regional hub")
        busTerminal("Legazpi", luzon, "Legazpi Terminal, Albay — Bicol regional hub")

        // Metro Manila & Luzon — ferry & fastcraft ports
        seaport("Manila North Harbor", luzon, "Busiest passenger seaport — long-haul inter-island ferries to Visayas and Mindanao")
        seaport("Batangas Port", luzon, "Gateway to Mindoro (Puerto Galera), Boracay via Caticlan, Romblon, and Panay")
        seaport("Matnog Port", luzon, "Sorsogon RoRo jump-off connecting mainland Luzon to Samar and the Visayas")
        seaport("Puerto Princesa Port", luzon, "Palawan ferries to Manila and regional island hopping")
        seaport("Coron Port", luzon, "Palawan island-hopping and Manila ferry connections")
        seaport("El Nido Port", luzon, "Palawan island-hopping and regional ferry connections")
        seaport("Lucena Port", luzon, "Quezon — connections to Marinduque and Romblon")

        // Visayas — regional bus terminals
        busTerminal("Cebu South Bus Terminal", visayas, "Southern Cebu municipalities and RoRo connections to Negros")
        busTerminal("Cebu North Bus Terminal", visayas, "Northern Cebu incl. bus-to-ferry links for Bantayan and Malapascua")
        busTerminal("Bacolod Ceres Terminal", visayas, "Vallacar Transit hub — Negros island routes")
        busTerminal("Iloilo Ceres Terminal", visayas, "Vallacar Transit hub — Panay island routes")
        busTerminal("Dumaguete Ceres Terminal", visayas, "Vallacar Transit hub — Negros Oriental routes")
        busTerminal("Tacloban Terminal", visayas, "Eastern Visayas hub — Samar, Southern Leyte, northbound to Luzon")

        // Visayas — ferry & fastcraft ports
        seaport("Cebu Port", visayas, "Piers 1, 3, 4 — fastcraft and RoRo to Bohol, Negros, Leyte, Mindanao, Manila")
        seaport("Tagbilaran Port", visayas, "Bohol fastcraft hub — Cebu, Siquijor, Dumaguete")
        seaport("Bacolod Port", visayas, "BREDCO — fastcrafts across the Guimaras Strait to Iloilo; ferries to Manila")
        seaport("Iloilo Port", visayas, "Lapuz fastcraft terminal — Bacolod, Guimaras, Manila")
        seaport("Dumaguete Port", visayas, "Fastcrafts to Siquijor, Bohol, and Dapitan/Dipolog in Mindanao")
        seaport("Ormoc Port", visayas, "Fastest maritime link between Leyte and Cebu via fastcraft")
        seaport("Liloan Port", visayas, "Southern Leyte RoRo gateway toward Mindanao (Surigao)")
        seaport("San Ricardo Port", visayas, "Southern Leyte RoRo gateway toward Mindanao (Surigao)")

        // Mindanao — regional bus terminals
        busTerminal("Davao DCOTT", mindanao, "Largest bus terminal in Mindanao — island-wide and long-haul to Metro Manila")
        busTerminal("Cagayan de Oro Agora", mindanao, "Northern Mindanao hub — Iligan, Butuan, Bukidnon")
        busTerminal("Butuan Terminal", mindanao, "Caraga hub — Surigao, Davao, Cagayan de Oro")
        busTerminal("Zamboanga Terminal", mindanao, "Zamboanga Peninsula — Dipolog, Pagadian, Cotabato")
        busTerminal("General Santos Bulaong", mindanao, "Main transport gateway for SOCCSKSARGEN")

        // Mindanao — ferry & fastcraft ports
        seaport("Cagayan de Oro Port", mindanao, "Macabalan — busiest Northern Mindanao passenger port; Cebu, Bohol, Manila")
        seaport("Surigao Port", mindanao, "Surigao City boulevard port — boats to Siargao (Dapa) and Dinagat")
        seaport("Dapa Port (Siargao)", mindanao, "Siargao's main port — fastcrafts and ferries from Surigao and Cebu")
        seaport("Zamboanga Port", mindanao, "Gateway for ferries to Basilan, Sulu, and Tawi-Tawi")
        seaport("Lipata Port", mindanao, "Surigao City — main RoRo entry from the Visayas and Luzon")
        seaport("Nasipit Port", mindanao, "Agusan del Norte — Butuan/Caraga ferries to Cebu and Bohol")
        seaport("Dapitan Port", mindanao, "Pulauan — Zamboanga Peninsula links to Dumaguete and Cebu")
        seaport("Ozamiz Port", mindanao, "RoRo across Panguil Bay (Misamis Occidental)")
        seaport("Mukas Port", mindanao, "RoRo across Panguil Bay (Lanao del Norte)")
    }

    private data class RouteService(
        val operatorName: String,
        val busClass: BusClass,
        val farePhp: Double,
        val rating: Double,
        val departureHours: List<Int>,
        val durationMinutes: Long,
        val kind: RideKind = RideKind.BUS,
    )

    /** (origin, destination) → services. Reverse directions are auto-registered. */
    private val routeServices: Map<Pair<String, String>, List<RouteService>> = buildMap {
        fun route(origin: String, destination: String, vararg services: RouteService) {
            put(origin to destination, services.toList())
            put(destination to origin, services.toList()) // buses run both ways
        }

        route(
            "Cubao", "Baguio",
            RouteService("Victory Liner", BusClass.ORDINARY, 485.0, 4.5, listOf(5, 9, 13, 21), 360),
            RouteService("Genesis Transport", BusClass.DELUXE, 795.0, 4.6, listOf(6, 10, 14, 22), 330),
            RouteService("JoyBus Premiere", BusClass.LUXURY, 1065.0, 4.8, listOf(7, 11, 23), 285),
        )
        route(
            "Pasay", "Baguio",
            RouteService("Victory Liner", BusClass.ORDINARY, 485.0, 4.5, listOf(6, 10, 22), 375),
            RouteService("JoyBus Executive", BusClass.DELUXE, 1005.0, 4.7, listOf(8, 12, 23), 300),
            RouteService("Victory Liner Royal Class", BusClass.LUXURY, 1546.0, 4.9, listOf(9, 23), 285),
        )
        route(
            "PITX", "Baguio",
            RouteService("Solid North Transit", BusClass.DELUXE, 950.0, 4.6, listOf(7, 13, 21), 315),
            RouteService("Solid North Luxury P2P", BusClass.LUXURY, 1250.0, 4.8, listOf(8, 22), 285),
        )
        route(
            "PITX", "Batangas Port",
            RouteService("DLTB Co.", BusClass.ORDINARY, 250.0, 4.3, listOf(5, 7, 9, 11, 13, 15, 17), 150),
            RouteService("JAM Liner", BusClass.ORDINARY, 230.0, 4.2, listOf(6, 8, 10, 12, 14, 16, 18), 150),
        )
        route(
            "Pasay", "Batangas Port",
            RouteService("DLTB Co.", BusClass.ORDINARY, 230.0, 4.3, listOf(5, 8, 11, 14, 17), 165),
            RouteService("JAM Liner", BusClass.ORDINARY, 230.0, 4.2, listOf(6, 9, 12, 15, 18), 165),
        )
        route(
            "Cubao", "Olongapo",
            RouteService("Victory Liner", BusClass.ORDINARY, 280.0, 4.4, listOf(5, 7, 9, 12, 15, 18), 210),
            RouteService("Victory Liner Deluxe", BusClass.DELUXE, 350.0, 4.5, listOf(8, 13, 17), 195),
        )
        route(
            "PITX", "Naga",
            RouteService("DLTB Co.", BusClass.ORDINARY, 850.0, 4.3, listOf(7, 17, 20), 540),
            RouteService("Isarog Elite", BusClass.DELUXE, 1150.0, 4.6, listOf(18, 21), 510),
            RouteService("Isarog Sleeper", BusClass.LUXURY, 1850.0, 4.8, listOf(20, 22), 480),
        )
        route(
            "Cubao", "Naga",
            RouteService("DLTB Co.", BusClass.ORDINARY, 850.0, 4.3, listOf(6, 16, 19), 540),
            RouteService("DLTB Lazyboy", BusClass.LUXURY, 1600.0, 4.7, listOf(20, 22), 495),
        )
        route(
            "Cubao", "Dagupan",
            RouteService("Five Star", BusClass.ORDINARY, 585.0, 4.4, listOf(5, 8, 11, 14, 17, 20), 270),
            RouteService("Five Star Deluxe", BusClass.DELUXE, 700.0, 4.6, listOf(7, 13, 19), 255),
        )
        route(
            "Cubao", "Vigan",
            RouteService("Partas", BusClass.ORDINARY, 950.0, 4.4, listOf(7, 20, 22), 480),
            RouteService("Partas Deluxe", BusClass.DELUXE, 1100.0, 4.6, listOf(21, 23), 450),
        )
        route(
            "Cubao", "Laoag",
            RouteService("Partas", BusClass.ORDINARY, 1150.0, 4.4, listOf(19, 21), 570),
            RouteService("Fariñas First Class", BusClass.LUXURY, 1450.0, 4.7, listOf(20, 22), 540),
        )
        route(
            "Cubao", "Tuguegarao",
            RouteService("Victory Liner", BusClass.ORDINARY, 1300.0, 4.4, listOf(18, 20), 600),
            RouteService("Five Star", BusClass.DELUXE, 1250.0, 4.5, listOf(19, 21), 585),
        )

        // ── Bicol & South Luzon hubs ────────────────────────────────────────
        route(
            "PITX", "Legazpi",
            RouteService("DLTB Co.", BusClass.ORDINARY, 1100.0, 4.3, listOf(17, 19), 720),
            RouteService("Isarog Elite", BusClass.DELUXE, 1400.0, 4.6, listOf(18, 20), 660),
        )
        route(
            "Cubao", "Legazpi",
            RouteService("Peñafrancia Tours", BusClass.ORDINARY, 1050.0, 4.2, listOf(17, 19), 720),
        )
        route(
            "Alabang VTX", "Naga",
            RouteService("DLTB Co.", BusClass.ORDINARY, 900.0, 4.3, listOf(18, 21), 560),
        )

        // ── Metro Manila feeder terminals ───────────────────────────────────
        route(
            "Avenida", "Dagupan",
            RouteService("Five Star", BusClass.ORDINARY, 585.0, 4.4, listOf(6, 9, 12, 15, 18), 270),
        )
        route(
            "NLET Bocaue", "Baguio",
            RouteService("Victory Liner", BusClass.ORDINARY, 450.0, 4.4, listOf(7, 11, 15, 19), 270),
        )
        route(
            "Valenzuela Gateway", "Dagupan",
            RouteService("Five Star", BusClass.ORDINARY, 550.0, 4.3, listOf(6, 10, 14, 18), 255),
        )
        route(
            "Santa Rosa SRIT", "Cubao",
            RouteService("HM Transport P2P", BusClass.ORDINARY, 120.0, 4.1, listOf(5, 7, 9, 11, 13, 15, 17), 90),
        )

        // ── Through-buses on the RoRo nautical highways ─────────────────────
        // One ticket: the bus boards the ferries with its passengers, so no
        // separate RoRo tickets or transfers are needed.
        route(
            "Pasay", "Surigao",
            RouteService("Philtranco", BusClass.ORDINARY, 2600.0, 4.3, listOf(13), 1800),
            RouteService("Philtranco Deluxe", BusClass.DELUXE, 2950.0, 4.4, listOf(15), 1740),
        )
        route(
            "PITX", "Caticlan",
            RouteService("Philtranco", BusClass.ORDINARY, 1300.0, 4.2, listOf(15, 17), 720),
        )

        // ── Tourist overland chain: Bicol → Samar → Leyte → Surigao ─────────
        route(
            "Legazpi", "Matnog Port",
            RouteService("DLTB Co.", BusClass.ORDINARY, 250.0, 4.1, listOf(4, 7, 10, 13, 16), 210),
        )
        route(
            "Allen", "Tacloban Terminal",
            RouteService("Grand Tours", BusClass.ORDINARY, 350.0, 4.1, listOf(4, 7, 10, 13, 16), 240),
        )
        route(
            "Tacloban Terminal", "Liloan Port",
            RouteService("Duptours Shuttle", BusClass.ORDINARY, 300.0, 4.1, listOf(5, 8, 11, 14), 210),
        )

        // ── Eastern Visayas (Tacloban hub) ──────────────────────────────────
        route(
            "Tacloban Terminal", "Ormoc Port",
            RouteService("Duptours Shuttle", BusClass.ORDINARY, 180.0, 4.2, listOf(5, 7, 9, 11, 13, 15, 17), 120),
        )
        route(
            "Tacloban Terminal", "Catbalogan",
            RouteService("Grand Tours", BusClass.ORDINARY, 250.0, 4.1, listOf(6, 9, 12, 15), 150),
        )
        route(
            "Tacloban Terminal", "Naval",
            RouteService("Eagle Star Bus", BusClass.ORDINARY, 220.0, 4.0, listOf(6, 12), 180),
        )
        route(
            "Pasay", "Tacloban Terminal",
            RouteService("Philtranco", BusClass.ORDINARY, 2150.0, 4.2, listOf(14), 1560),
            RouteService("DLTB Co.", BusClass.DELUXE, 2400.0, 4.3, listOf(15), 1500),
        )

        // ── Cebu terminals ──────────────────────────────────────────────────
        route(
            "Cebu South Bus Terminal", "Oslob",
            RouteService("Ceres Liner", BusClass.ORDINARY, 210.0, 4.3, listOf(4, 6, 8, 10, 12, 14, 16), 210),
        )
        route(
            "Cebu South Bus Terminal", "Moalboal",
            RouteService("Ceres Liner", BusClass.ORDINARY, 180.0, 4.2, listOf(5, 8, 11, 14, 17), 180),
        )
        route(
            "Cebu South Bus Terminal", "Bato",
            RouteService("Ceres Liner", BusClass.ORDINARY, 230.0, 4.2, listOf(5, 9, 13, 17), 240),
        )
        route(
            "Cebu North Bus Terminal", "Hagnaya",
            RouteService("Ceres Liner", BusClass.ORDINARY, 220.0, 4.2, listOf(5, 8, 11, 14), 180),
        )
        route(
            "Cebu North Bus Terminal", "Maya",
            RouteService("Ceres Liner", BusClass.ORDINARY, 230.0, 4.1, listOf(4, 7, 10, 13, 16), 240),
        )

        // ── Negros & Panay (Ceres hubs) ─────────────────────────────────────
        route(
            "Bacolod Ceres Terminal", "Dumaguete Ceres Terminal",
            RouteService("Ceres Liner", BusClass.ORDINARY, 450.0, 4.3, listOf(4, 7, 10, 13, 16), 360),
            RouteService("Ceres Liner Aircon", BusClass.DELUXE, 550.0, 4.4, listOf(6, 12), 330),
        )
        route(
            "Bacolod Ceres Terminal", "San Carlos",
            RouteService("Ceres Liner", BusClass.ORDINARY, 180.0, 4.1, listOf(5, 8, 11, 14, 17), 150),
        )
        route(
            "Iloilo Ceres Terminal", "Caticlan",
            RouteService("Ceres Liner", BusClass.ORDINARY, 450.0, 4.3, listOf(3, 5, 7, 9, 11, 13), 360),
        )
        route(
            "Iloilo Ceres Terminal", "Kalibo",
            RouteService("Ceres Liner", BusClass.ORDINARY, 400.0, 4.2, listOf(4, 8, 12, 16), 300),
        )
        route(
            "Iloilo Ceres Terminal", "Roxas",
            RouteService("Ceres Liner", BusClass.ORDINARY, 350.0, 4.2, listOf(5, 9, 13, 17), 270),
        )

        // ── Mindanao hubs ───────────────────────────────────────────────────
        route(
            "Davao DCOTT", "Cagayan de Oro Agora",
            RouteService("Rural Transit", BusClass.ORDINARY, 600.0, 4.2, listOf(4, 6, 8, 10, 12, 14), 360),
            RouteService("Super 5 Transport", BusClass.DELUXE, 750.0, 4.3, listOf(7, 11, 15), 330),
        )
        route(
            "Davao DCOTT", "General Santos Bulaong",
            RouteService("Yellow Bus Line", BusClass.ORDINARY, 350.0, 4.3, listOf(5, 7, 9, 11, 13, 15, 17), 180),
        )
        route(
            "Davao DCOTT", "Butuan Terminal",
            RouteService("Bachelor Express", BusClass.ORDINARY, 550.0, 4.1, listOf(5, 8, 11, 14, 17), 300),
        )
        route(
            "Cagayan de Oro Agora", "Iligan",
            RouteService("Rural Transit", BusClass.ORDINARY, 180.0, 4.1, listOf(5, 7, 9, 11, 13, 15, 17), 120),
        )
        route(
            "Cagayan de Oro Agora", "Butuan Terminal",
            RouteService("Bachelor Express", BusClass.ORDINARY, 400.0, 4.1, listOf(5, 8, 11, 14, 17), 240),
        )
        route(
            "Cagayan de Oro Agora", "Malaybalay",
            RouteService("Rural Transit", BusClass.ORDINARY, 250.0, 4.2, listOf(6, 9, 12, 15), 150),
        )
        route(
            "Butuan Terminal", "Surigao",
            RouteService("Bachelor Express", BusClass.ORDINARY, 300.0, 4.1, listOf(4, 7, 10, 13, 16), 180),
        )
        route(
            "Zamboanga Terminal", "Pagadian",
            RouteService("Rural Transit", BusClass.ORDINARY, 450.0, 4.1, listOf(5, 9, 13), 300),
        )
        route(
            "Zamboanga Terminal", "Dipolog",
            RouteService("Rural Transit", BusClass.ORDINARY, 600.0, 4.0, listOf(5, 12), 420),
        )
        route(
            "General Santos Bulaong", "Koronadal",
            RouteService("Yellow Bus Line", BusClass.ORDINARY, 120.0, 4.2, listOf(5, 7, 9, 11, 13, 15, 17), 90),
        )
    }

    /**
     * (port, port) → ferry/fastcraft sailings. Reverse directions are
     * auto-registered. Indicative fares researched July 2026 (2GO, Montenegro,
     * Starlite, FastCat, OceanJet, SuperCat, Weesam, Cokaliong).
     */
    private val seaRouteServices: Map<Pair<String, String>, List<RouteService>> = buildMap {
        fun sea(portA: String, portB: String, vararg services: RouteService) {
            put(portA to portB, services.toList())
            put(portB to portA, services.toList()) // vessels sail both ways
        }

        sea(
            "Batangas Port", "Calapan",
            RouteService("Montenegro Lines", BusClass.ORDINARY, 400.0, 4.2, listOf(2, 6, 10, 14, 18, 22), 150, RideKind.FERRY),
            RouteService("FastCat", BusClass.ORDINARY, 420.0, 4.3, listOf(4, 8, 12, 16, 20), 135, RideKind.FERRY),
            RouteService("OceanJet", BusClass.DELUXE, 700.0, 4.5, listOf(7, 11, 15), 75, RideKind.FASTCRAFT),
        )
        sea(
            "Batangas Port", "Puerto Galera",
            RouteService("Father & Son Lines", BusClass.ORDINARY, 500.0, 4.1, listOf(7, 9, 11, 13, 15), 90, RideKind.FASTCRAFT),
            RouteService("Minolo Shipping", BusClass.ORDINARY, 480.0, 4.0, listOf(8, 10, 12, 14, 16), 95, RideKind.FASTCRAFT),
        )
        sea(
            "Batangas Port", "Caticlan",
            RouteService("2GO Travel", BusClass.DELUXE, 1550.0, 4.4, listOf(19, 21), 540, RideKind.FERRY),
            RouteService("Starlite Ferries", BusClass.ORDINARY, 1200.0, 4.2, listOf(17, 20), 600, RideKind.FERRY),
        )
        sea(
            "Batangas Port", "Romblon",
            RouteService("Montenegro Lines", BusClass.ORDINARY, 950.0, 4.1, listOf(17), 480, RideKind.FERRY),
        )
        sea(
            "Manila North Harbor", "Cebu Port",
            RouteService("2GO Travel", BusClass.DELUXE, 2800.0, 4.5, listOf(13, 21), 1320, RideKind.FERRY),
        )
        sea(
            "Manila North Harbor", "Coron Port",
            RouteService("2GO Travel", BusClass.DELUXE, 2300.0, 4.4, listOf(15), 900, RideKind.FERRY),
        )
        sea(
            "Matnog Port", "Allen",
            RouteService("Santa Clara Shipping", BusClass.ORDINARY, 250.0, 4.0, listOf(0, 4, 8, 12, 16, 20), 90, RideKind.FERRY),
        )
        sea(
            "Lucena Port", "Marinduque",
            RouteService("Starhorse Lines", BusClass.ORDINARY, 420.0, 4.0, listOf(4, 8, 12, 16), 180, RideKind.FERRY),
        )
        sea(
            "Cebu Port", "Tagbilaran Port",
            RouteService("OceanJet", BusClass.DELUXE, 800.0, 4.6, listOf(6, 8, 10, 12, 14, 16, 18), 120, RideKind.FASTCRAFT),
            RouteService("SuperCat", BusClass.DELUXE, 750.0, 4.5, listOf(7, 11, 15, 17), 110, RideKind.FASTCRAFT),
            RouteService("Lite Ferries", BusClass.ORDINARY, 450.0, 4.1, listOf(9, 21), 240, RideKind.FERRY),
        )
        sea(
            "Cebu Port", "Ormoc Port",
            RouteService("OceanJet", BusClass.DELUXE, 1050.0, 4.5, listOf(5, 10, 15), 165, RideKind.FASTCRAFT),
            RouteService("SuperCat", BusClass.DELUXE, 1000.0, 4.4, listOf(8, 13, 17), 150, RideKind.FASTCRAFT),
        )
        sea(
            "Cebu Port", "Cagayan de Oro Port",
            RouteService("Trans-Asia Shipping", BusClass.ORDINARY, 1100.0, 4.2, listOf(19), 600, RideKind.FERRY),
        )
        sea(
            "Bacolod Port", "Iloilo Port",
            RouteService("FastCat", BusClass.ORDINARY, 500.0, 4.3, listOf(5, 9, 13, 17), 90, RideKind.FERRY),
            RouteService("OceanJet", BusClass.DELUXE, 650.0, 4.5, listOf(7, 10, 13, 16), 60, RideKind.FASTCRAFT),
            RouteService("Weesam Express", BusClass.ORDINARY, 550.0, 4.2, listOf(6, 11, 15), 65, RideKind.FASTCRAFT),
        )
        sea(
            "Dumaguete Port", "Tagbilaran Port",
            RouteService("OceanJet", BusClass.DELUXE, 900.0, 4.5, listOf(9, 15), 120, RideKind.FASTCRAFT),
        )
        sea(
            "Dumaguete Port", "Dapitan Port",
            RouteService("Aleson Shipping", BusClass.ORDINARY, 600.0, 4.0, listOf(6, 14), 210, RideKind.FERRY),
        )
        sea(
            "San Ricardo Port", "Lipata Port",
            RouteService("Montenegro Lines", BusClass.ORDINARY, 350.0, 4.0, listOf(1, 5, 9, 13, 17, 21), 150, RideKind.FERRY),
        )
        sea(
            "Ozamiz Port", "Mukas Port",
            RouteService("Daima Shipping", BusClass.ORDINARY, 120.0, 3.9, listOf(5, 7, 9, 11, 13, 15, 17), 60, RideKind.FERRY),
        )
        sea(
            "Zamboanga Port", "Basilan",
            RouteService("Weesam Express", BusClass.ORDINARY, 380.0, 4.1, listOf(6, 9, 13, 16), 75, RideKind.FASTCRAFT),
            RouteService("Aleson Shipping", BusClass.ORDINARY, 260.0, 4.0, listOf(7, 12, 17), 105, RideKind.FERRY),
        )
        sea(
            "Nasipit Port", "Cebu Port",
            RouteService("Cokaliong Shipping", BusClass.ORDINARY, 1050.0, 4.1, listOf(19), 600, RideKind.FERRY),
        )
        sea(
            "Liloan Port", "Lipata Port",
            RouteService("Montenegro Lines", BusClass.ORDINARY, 350.0, 4.0, listOf(3, 9, 15, 21), 150, RideKind.FERRY),
        )
        sea(
            "Puerto Princesa Port", "Coron Port",
            RouteService("2GO Travel", BusClass.DELUXE, 1400.0, 4.3, listOf(17), 660, RideKind.FERRY),
        )
        sea(
            "Coron Port", "El Nido Port",
            RouteService("Jomalia Shipping", BusClass.DELUXE, 1760.0, 4.2, listOf(6, 12), 240, RideKind.FASTCRAFT),
            RouteService("Montenegro Lines", BusClass.ORDINARY, 1100.0, 4.0, listOf(8), 420, RideKind.FERRY),
        )
        sea(
            "Ormoc Port", "Camotes",
            RouteService("Jomalia Shipping", BusClass.ORDINARY, 500.0, 4.0, listOf(7, 13), 120, RideKind.FASTCRAFT),
        )
        sea(
            "Surigao Port", "Dapa Port (Siargao)",
            RouteService("Evaristo & Sons", BusClass.ORDINARY, 350.0, 4.1, listOf(6, 11, 15), 150, RideKind.FASTCRAFT),
            RouteService("Montenegro Lines", BusClass.ORDINARY, 300.0, 4.0, listOf(5, 12), 240, RideKind.FERRY),
        )
        sea(
            "Cebu Port", "Dapa Port (Siargao)",
            RouteService("Cokaliong Shipping", BusClass.ORDINARY, 1100.0, 4.1, listOf(19), 600, RideKind.FERRY),
        )
    }

    // ── Curated tourist journeys ────────────────────────────────────────────
    // Multi-leg routes travellers ask for but can't be expected to assemble
    // themselves. Keywords match the search query (destination-side).
    private val journeySeeds: List<Pair<List<String>, Journey>> = listOf(
        // Direct options lead: through-buses ride the RoRo ferries with their
        // passengers on a single ticket — no DIY leg-by-leg chains.
        listOf("siargao", "dapa", "surigao") to Journey(
            title = "Direct through-bus + island hop",
            from = "Manila (NAIA)",
            to = "Siargao (Dapa)",
            legs = listOf(
                JourneyLeg(
                    RideKind.BUS, "Pasay", "Surigao", 1800, 2600.0,
                    note = "One ticket — the bus rides the RoRo ferries with you " +
                        "(Matnog–Allen and Liloan–Lipata), meal stops included. " +
                        "From NAIA, Grab/taxi to the Pasay terminals (~30 min)",
                ),
                JourneyLeg(
                    RideKind.FASTCRAFT, "Surigao Port", "Dapa Port (Siargao)", 150, 350.0,
                    note = "Tricycle from the bus terminal to Surigao boulevard port (~10 min)",
                ),
            ),
        ),
        listOf("siargao", "dapa") to Journey(
            title = "Sail via Cebu",
            from = "Manila (NAIA)",
            to = "Siargao (Dapa)",
            legs = listOf(
                JourneyLeg(
                    RideKind.FERRY, "Manila North Harbor", "Cebu Port", 1320, 2800.0,
                    note = "2GO sails from Pier 4, North Harbor — taxi from NAIA ~45 min",
                ),
                JourneyLeg(
                    RideKind.FERRY, "Cebu Port", "Dapa Port (Siargao)", 600, 1100.0,
                    note = "Overnight sailing — arrives Dapa early morning",
                ),
            ),
        ),
        listOf("boracay", "caticlan") to Journey(
            title = "Direct through-bus via the Nautical Highway",
            from = "Manila (NAIA)",
            to = "Boracay (Caticlan)",
            legs = listOf(
                JourneyLeg(
                    RideKind.BUS, "PITX", "Caticlan", 720, 1300.0,
                    note = "One ticket — RoRo crossings via Mindoro included. From NAIA, " +
                        "Grab/taxi to PITX (~20 min); short boat transfer to Boracay at Caticlan jetty",
                ),
            ),
        ),
        listOf("boracay", "caticlan") to Journey(
            title = "Overnight ferry via Batangas",
            from = "Manila (NAIA)",
            to = "Boracay (Caticlan)",
            legs = listOf(
                JourneyLeg(
                    RideKind.BUS, "PITX", "Batangas Port", 150, 250.0,
                    note = "From NAIA, take a Grab/taxi to PITX (~20 min)",
                ),
                JourneyLeg(
                    RideKind.FERRY, "Batangas Port", "Caticlan", 540, 1550.0,
                    note = "From Caticlan jetty, boat transfer to Boracay island (~15 min)",
                ),
            ),
        ),
    )

    // ── Itineraries (saved journey plans) ───────────────────────────────────

    private val itineraries = LinkedHashMap<String, Itinerary>()

    fun itineraries(): List<Itinerary> = synchronized(itineraries) { itineraries.values.toList() }

    fun addItinerary(journey: Journey, startDateMillis: Long): Itinerary = synchronized(itineraries) {
        itineraries.values.firstOrNull {
            it.journey.title == journey.title && it.journey.to == journey.to
        }?.let { return it } // saving the same route twice keeps the existing plan
        val itinerary = Itinerary(
            id = "ITIN-${UUID.randomUUID().toString().take(8).uppercase()}",
            journey = journey,
            startDateMillis = startDateMillis,
        )
        itineraries[itinerary.id] = itinerary
        itinerary
    }

    fun setLegDone(itineraryId: String, legIndex: Int, done: Boolean) = synchronized(itineraries) {
        val current = itineraries[itineraryId] ?: return
        val updated = if (done) current.doneLegIndices + legIndex else current.doneLegIndices - legIndex
        itineraries[itineraryId] = current.copy(doneLegIndices = updated)
    }

    fun removeItinerary(itineraryId: String): Unit = synchronized(itineraries) {
        itineraries.remove(itineraryId)
    }

    fun journeys(rawQuery: String): List<Journey> {
        val query = normalize(rawQuery)
        if (query.isBlank()) return emptyList()
        return journeySeeds
            .filter { (keywords, _) -> keywords.any { query.contains(it) || it.contains(query) } }
            .map { it.second }
    }

    // ── Workbook merge ──────────────────────────────────────────────────────
    // Curated seeds + corridors generated from the 2026 research workbook
    // (XlsxSeedData.kt). Generated fares take their place beside curated ones;
    // duplicates (same operator/class/fare) collapse. Both directions register.

    private fun mergeRoutes(
        curated: Map<Pair<String, String>, List<RouteService>>,
        generated: Map<Pair<String, String>, List<XlsxService>>,
    ): Map<Pair<String, String>, List<RouteService>> = buildMap {
        putAll(curated)
        generated.forEach { (key, services) ->
            val converted = services.map {
                RouteService(
                    operatorName = it.operatorName,
                    busClass = it.busClass,
                    farePhp = it.farePhp,
                    rating = 4.2, // workbook carries no ratings
                    departureHours = it.departureHours,
                    durationMinutes = it.durationMinutes,
                    kind = it.kind,
                )
            }
            listOf(key, key.second to key.first).forEach { direction ->
                val combined = (this[direction].orEmpty() + converted)
                    .distinctBy { Triple(it.operatorName, it.busClass, it.farePhp) }
                put(direction, combined)
            }
        }
    }

    private val allBusRoutes by lazy { mergeRoutes(routeServices, xlsxBusRoutes) }
    private val allSeaRoutes by lazy { mergeRoutes(seaRouteServices, xlsxSeaRoutes) }

    /** Fallback services so ANY searched route still returns demo inventory. */
    private val fallbackServices = listOf(
        RouteService("Genesis Transport", BusClass.LUXURY, 850.0, 4.8, listOf(6, 14), 285),
        RouteService("Victory Liner", BusClass.DELUXE, 620.0, 4.5, listOf(8, 16), 345),
        RouteService("Philtranco", BusClass.ORDINARY, 550.0, 4.2, listOf(9, 17), 420),
    )

    // ── "Tables" ────────────────────────────────────────────────────────────

    private val trips = ConcurrentHashMap<String, Trip>()
    private val seatMaps = ConcurrentHashMap<String, MutableList<Seat>>()
    private val tickets = ConcurrentHashMap<String, Ticket>()
    private var profile: UserProfile = UserProfile()
    private val vehicles = ConcurrentHashMap<String, Vehicle>()
    private val walletTransactions = mutableListOf<WalletTransaction>()
    private val supportMessages = mutableListOf<SupportMessage>()

    /** Live seat updates, mimicking the WebSocket `seat_status_changed` channel. */
    val seatEvents = MutableSharedFlow<SeatStatusEvent>(extraBufferCapacity = 32)

    init {
        seedPastBookings()
        seedWalletAndSupport()
    }

    /** Sample wallet credit + support greeting so both tabs have demo content. */
    private fun seedWalletAndSupport() {
        walletTransactions += WalletTransaction(
            id = "WT-WELCOME",
            title = "Welcome credit (sample)",
            amountPhp = 250.0,
            timestampEpochMillis = System.currentTimeMillis() - 40L * 86_400_000L,
        )
        supportMessages += SupportMessage(
            id = "SM-GREETING",
            text = "Hi! This is RideVibe support. How can we help you today?",
            fromUser = false,
            timestampEpochMillis = System.currentTimeMillis(),
        )
    }

    /** Two completed sample bookings so the history section has demo content. */
    private fun seedPastBookings() {
        val now = System.currentTimeMillis()

        val baguioTrip = Trip(
            id = "TRIP-PAST-BAGUIO",
            operatorName = "Victory Liner",
            origin = "Cubao",
            destination = "Baguio",
            departureEpochMillis = now - 14L * 86_400_000L,
            arrivalEpochMillis = now - 14L * 86_400_000L + 360 * 60_000L,
            busClass = BusClass.ORDINARY,
            farePhp = 485.0,
            availableSeatCount = 0,
            operatorRating = 4.5,
        )
        trips[baguioTrip.id] = baguioTrip
        tickets["RV-HIST0001"] = Ticket(
            id = "RV-HIST0001",
            trip = baguioTrip,
            seatLabels = listOf("8C"),
            primaryPassenger = Passenger(fullName = "Juan Dela Cruz", type = com.ridevibe.core.domain.model.PassengerType.REGULAR),
            paymentStatus = PaymentStatus.PAID,
            qrPayload = "RIDEVIBE|RV-HIST0001|${baguioTrip.id}|8C|Juan Dela Cruz(R)|PAID",
        )

        val batangasTrip = Trip(
            id = "TRIP-PAST-BATANGAS",
            operatorName = "JAM Liner",
            origin = "PITX",
            destination = "Batangas Port",
            departureEpochMillis = now - 32L * 86_400_000L,
            arrivalEpochMillis = now - 32L * 86_400_000L + 150 * 60_000L,
            busClass = BusClass.ORDINARY,
            farePhp = 230.0,
            availableSeatCount = 0,
            operatorRating = 4.2,
        )
        trips[batangasTrip.id] = batangasTrip
        tickets["RV-HIST0002"] = Ticket(
            id = "RV-HIST0002",
            trip = batangasTrip,
            seatLabels = listOf("3C", "3D"),
            primaryPassenger = Passenger(fullName = "Juan Dela Cruz", type = com.ridevibe.core.domain.model.PassengerType.REGULAR),
            coPassengers = listOf(CoPassenger(firstName = "Maria", lastName = "Dela Cruz")),
            paymentStatus = PaymentStatus.PAID,
            qrPayload = "RIDEVIBE|RV-HIST0002|${batangasTrip.id}|3C+3D|Juan Dela Cruz(R)+Maria Dela Cruz(R)|PAID",
        )
    }

    fun allTickets(): List<Ticket> =
        tickets.values.sortedByDescending { it.trip.departureEpochMillis }

    // ── Reference data ──────────────────────────────────────────────────────

    /** Hub registry first (central terminals up top), then route endpoints not already covered. */
    fun locations(): List<TerminalLocation> {
        val hubNames = hubs.map { it.name }.toSet()
        val routeCities = (allBusRoutes.keys + allSeaRoutes.keys)
            .flatMap { listOf(it.first, it.second) }
            .toSortedSet()
            .filterNot { it in hubNames }
            .map { name -> TerminalLocation(name = name, kind = LocationKind.CITY) }
        return hubs.sortedByDescending { it.isCentralTerminal } + routeCities
    }

    fun availableBusClasses(): List<BusClass> =
        routeServices.values.flatten().map { it.busClass }.distinct().sorted()

    // ── Trips ───────────────────────────────────────────────────────────────

    fun searchTrips(origin: String, destination: String, dateMillis: Long, busClass: BusClass?): List<Trip> {
        val key = normalize(origin) to normalize(destination)
        fun Map<Pair<String, String>, List<RouteService>>.lookup() = entries
            .firstOrNull { (route, _) -> normalize(route.first) == key.first && normalize(route.second) == key.second }
            ?.value
        // Land corridors first, then sea lanes; unknown pairs get demo inventory.
        val services = allBusRoutes.lookup() ?: allSeaRoutes.lookup() ?: fallbackServices

        return services
            .filter { busClass == null || it.busClass == busClass }
            .flatMap { service -> generateTrips(origin, destination, dateMillis, service) }
            .distinctBy { it.id }
            .sortedBy { it.departureEpochMillis }
    }

    /**
     * Cross-mode destination search: buses heading to matching places plus
     * ferry/fastcraft sailings departing matching ports on the chosen date.
     * Region queries ("Visayas", "Luzon", "Mindanao") expand to every hub
     * registered in that region. A non-null [returnDateMillis] also includes
     * the reverse directions on that date (round trip).
     */
    fun searchRelated(rawQuery: String, dateMillis: Long, returnDateMillis: Long? = null): List<Trip> {
        val query = normalize(rawQuery)
        if (query.isBlank()) return emptyList()

        val regionHubNames = hubs
            .filter { normalize(it.region).contains(query) }
            .map { normalize(it.name) }
            .toSet()

        fun matches(endpoint: String): Boolean {
            val name = normalize(endpoint)
            return name.contains(query) || name in regionHubNames
        }

        fun generateFor(
            date: Long,
            busEndpoint: (Pair<String, String>) -> String,
            seaEndpoint: (Pair<String, String>) -> String,
        ): List<Trip> {
            val bus = allBusRoutes.entries
                .filter { (route, _) -> matches(busEndpoint(route)) }
                .flatMap { (route, services) ->
                    services.flatMap { generateTrips(route.first, route.second, date, it) }
                }
            val sea = allSeaRoutes.entries
                .filter { (route, _) -> matches(seaEndpoint(route)) }
                .flatMap { (route, services) ->
                    services.flatMap { generateTrips(route.first, route.second, date, it) }
                }
            return bus + sea
        }

        // Outbound: buses toward the place; sailings departing the port
        // (reverse sea directions are registered, so origin-match covers both).
        val outbound = generateFor(dateMillis, busEndpoint = { it.second }, seaEndpoint = { it.first })
        // Return leg: reverse directions on the return date.
        val returnLeg = returnDateMillis?.let { date ->
            generateFor(date, busEndpoint = { it.first }, seaEndpoint = { it.second })
        }.orEmpty()

        return (outbound + returnLeg).distinctBy { it.id }.sortedBy { it.departureEpochMillis }
    }

    private fun generateTrips(
        origin: String,
        destination: String,
        dateMillis: Long,
        service: RouteService,
    ): List<Trip> {
        val dayStart = dateMillis - (dateMillis % 86_400_000L)
        return service.departureHours.map { hour ->
            val departure = dayStart + hour * 3_600_000L
            // Class and fare are part of the identity: one operator can run several
            // classes on the same corridor at the same hour (e.g. Semi/Super Deluxe).
            val id = "TRIP-${
                (origin + destination + service.operatorName + service.busClass.name +
                    service.farePhp + departure).hashCode().toUInt()
            }"
            val trip = Trip(
                id = id,
                operatorName = service.operatorName,
                origin = origin,
                destination = destination,
                departureEpochMillis = departure,
                arrivalEpochMillis = departure + service.durationMinutes * 60_000L,
                busClass = service.busClass,
                farePhp = service.farePhp,
                availableSeatCount = seatMap(id).count { it.status == SeatStatus.AVAILABLE },
                operatorRating = service.rating,
                rideKind = service.kind,
            )
            trips[id] = trip
            trip
        }
    }

    fun getTrip(tripId: String): Trip? = trips[tripId]

    private fun normalize(value: String) = value.trim().lowercase()

    // ── Seats ───────────────────────────────────────────────────────────────

    /** 10 rows × (A,B | aisle | C,D); occupancy seeded by tripId so it's stable. */
    fun seatMap(tripId: String): List<Seat> = seatMaps.getOrPut(tripId) {
        val random = Random(tripId.hashCode())
        val columnsToLetters = listOf(1 to "A", 2 to "B", 3 to "C", 4 to "D")
        (1..10).flatMap { row ->
            columnsToLetters.map { (column, letter) ->
                val roll = random.nextFloat()
                Seat(
                    id = "$row$letter",
                    label = "$row$letter",
                    row = row,
                    column = column,
                    status = when {
                        roll < 0.25f -> SeatStatus.OCCUPIED
                        roll < 0.30f -> SeatStatus.LOCKED
                        else -> SeatStatus.AVAILABLE
                    },
                    lockedByUserId = if (roll in 0.25f..0.30f) "other-passenger" else null,
                )
            }
        }.toMutableList()
    }

    fun updateSeat(tripId: String, seatId: String, status: SeatStatus, lockedBy: String?): Boolean {
        val seats = seatMaps[tripId] ?: return false
        // Synchronized: the simulated "other passenger" coroutine and UI
        // actions can mutate the same seat list from different dispatchers.
        synchronized(seats) {
            val index = seats.indexOfFirst { it.id == seatId }
            if (index == -1) return false
            seats[index] = seats[index].copy(status = status, lockedByUserId = lockedBy)
        }
        seatEvents.tryEmit(
            SeatStatusEvent(tripId = tripId, seatId = seatId, status = status, lockedByUserId = lockedBy),
        )
        return true
    }

    fun availableSeats(tripId: String): List<Seat> =
        seatMaps[tripId]?.filter { it.status == SeatStatus.AVAILABLE } ?: emptyList()

    // ── Tickets ─────────────────────────────────────────────────────────────

    fun createTicket(
        tripId: String,
        seatIds: List<String>,
        primaryPassenger: Passenger,
        coPassengers: List<CoPassenger>,
        infantCount: Int,
        paymentMethod: PaymentMethod,
    ): Ticket? {
        val trip = trips[tripId] ?: return null
        val seatLabels = seatIds.map { seatId ->
            seatMaps[tripId]?.firstOrNull { it.id == seatId }?.label ?: seatId
        }
        val ticketId = "RV-${UUID.randomUUID().toString().take(8).uppercase()}"
        val status = paymentMethod.paymentStatus

        // One QR carrying the whole manifest: seats, every passenger + fare type, status.
        fun typeTag(type: com.ridevibe.core.domain.model.PassengerType) = when (type) {
            com.ridevibe.core.domain.model.PassengerType.REGULAR -> "R"
            com.ridevibe.core.domain.model.PassengerType.STUDENT -> "ST"
            com.ridevibe.core.domain.model.PassengerType.SENIOR_CITIZEN -> "SR"
            com.ridevibe.core.domain.model.PassengerType.PWD -> "PWD"
        }
        val manifest = buildString {
            append("RIDEVIBE|").append(ticketId).append('|').append(tripId)
            append('|').append(seatLabels.joinToString("+"))
            append('|').append(primaryPassenger.fullName).append('(').append(typeTag(primaryPassenger.type)).append(')')
            coPassengers.forEach {
                append('+').append(it.firstName).append(' ').append(it.lastName)
                append('(').append(typeTag(it.type)).append(')')
            }
            if (infantCount > 0) append("|INF:").append(infantCount)
            append('|').append(status.name)
        }

        val ticket = Ticket(
            id = ticketId,
            trip = trip,
            seatLabels = seatLabels,
            primaryPassenger = primaryPassenger,
            coPassengers = coPassengers,
            infantCount = infantCount,
            paymentStatus = status,
            qrPayload = manifest,
            reservationExpiresAtEpochMillis = if (status == PaymentStatus.CASH_ON_BOARD) {
                System.currentTimeMillis() + 20 * 60_000L
            } else {
                null
            },
        )
        tickets[ticketId] = ticket
        seatIds.forEach { updateSeat(tripId, it, SeatStatus.OCCUPIED, lockedBy = null) }

        // Record the payment in the wallet history (per-passenger discounts applied).
        val discountTotal = trip.farePhp * primaryPassenger.type.discountRate +
            coPassengers.sumOf { trip.farePhp * it.type.discountRate }
        val paidPhp = trip.farePhp * seatLabels.size - discountTotal
        synchronized(walletTransactions) {
            walletTransactions += WalletTransaction(
                id = "WT-$ticketId",
                title = "Ticket $ticketId • ${trip.origin} → ${trip.destination}",
                amountPhp = -paidPhp,
                timestampEpochMillis = System.currentTimeMillis(),
            )
        }
        return ticket
    }

    fun getTicket(ticketId: String): Ticket? = tickets[ticketId]

    // ── Profile & vehicles ──────────────────────────────────────────────────

    fun getProfile(): UserProfile = profile

    fun saveProfile(updated: UserProfile) {
        profile = updated
    }

    fun getVehicles(): List<Vehicle> = vehicles.values.sortedBy { it.plateNumber }

    fun addVehicle(plateNumber: String, ltoCertificateUri: String): Vehicle {
        val vehicle = Vehicle(
            id = UUID.randomUUID().toString(),
            plateNumber = plateNumber.uppercase().trim(),
            ltoCertificateUri = ltoCertificateUri,
        )
        vehicles[vehicle.id] = vehicle
        return vehicle
    }

    fun removeVehicle(vehicleId: String) {
        vehicles.remove(vehicleId)
    }

    // ── Wallet & support ────────────────────────────────────────────────────

    fun walletTransactions(): List<WalletTransaction> = synchronized(walletTransactions) {
        walletTransactions.sortedByDescending { it.timestampEpochMillis }
    }

    fun walletBalancePhp(): Double = synchronized(walletTransactions) {
        walletTransactions.sumOf { it.amountPhp }
    }

    fun supportMessages(): List<SupportMessage> = synchronized(supportMessages) {
        supportMessages.toList()
    }

    fun addSupportMessage(message: SupportMessage) {
        synchronized(supportMessages) { supportMessages += message }
    }
}
