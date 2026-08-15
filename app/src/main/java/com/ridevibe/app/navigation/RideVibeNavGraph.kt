package com.ridevibe.app.navigation

import android.net.Uri
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.navArgument
import com.ridevibe.app.ui.bookings.BookingsScreen
import com.ridevibe.app.ui.chat.ChatScreen
import com.ridevibe.app.ui.chat.SupportScreen
import com.ridevibe.app.ui.components.BottomTab
import com.ridevibe.app.ui.components.RideVibeBottomNav
import com.ridevibe.app.ui.itinerary.ItineraryScreen
import com.ridevibe.app.ui.profile.ProfileScreen
import com.ridevibe.app.ui.wallet.WalletScreen
import com.ridevibe.app.ui.welcome.WelcomeScreen
import com.ridevibe.core.domain.model.BusClass
import com.ridevibe.core.domain.model.RideKind
import com.ridevibe.feature.checkout.ui.CheckoutScreen
import com.ridevibe.feature.search.ui.ExploreScreen
import com.ridevibe.feature.search.ui.HomeScreen
import com.ridevibe.feature.search.ui.ResultsScreen
import com.ridevibe.feature.seatmap.ui.SeatMapScreen
import com.ridevibe.feature.ticket.ui.TicketScreen

private object Routes {
    const val WELCOME = "welcome"
    const val HOME = "home"
    const val PROFILE = "profile"
    const val BOOKINGS = "bookings"
    const val ITINERARY = "itinerary"
    const val WALLET = "wallet"

    // Support: the Chat tab lands on topic triage; the live thread is a
    // separate destination that carries the chosen topic + booking context.
    const val CHAT = "chat"
    const val CHAT_THREAD = "chat/thread?topic={topic}&booking={booking}"
    const val EXPLORE = "explore/{query}?date={date}&returnDate={returnDate}"
    const val RESULTS = "results/{origin}/{destination}/{dateMillis}/{busClass}/{adults}/{children}/{infants}/{forSelf}/{leg}"
    const val SEAT_MAP = "trips/{tripId}/seatmap/{seatCount}/{infants}/{forSelf}/{leg}"
    const val CHECKOUT = "trips/{tripId}/checkout/{seats}/{infants}/{forSelf}"
    const val TICKET = "tickets/{ticketIds}"

    fun results(
        origin: String,
        destination: String,
        dateMillis: Long,
        busClass: BusClass?,
        adults: Int,
        children: Int,
        infants: Int,
        forSelf: Boolean,
        leg: String,
    ) = "results/${Uri.encode(origin)}/${Uri.encode(destination)}/$dateMillis/" +
        "${busClass?.name ?: "ANY"}/$adults/$children/$infants/$forSelf/$leg"

    fun seatMap(tripId: String, seatCount: Int, infants: Int, forSelf: Boolean, leg: String) =
        "trips/${Uri.encode(tripId)}/seatmap/$seatCount/$infants/$forSelf/$leg"

    fun checkout(tripId: String, seatIdsCsv: String, infants: Int, forSelf: Boolean) =
        "trips/${Uri.encode(tripId)}/checkout/${Uri.encode(seatIdsCsv)}/$infants/$forSelf"

    fun ticket(ticketIds: String) = "tickets/${Uri.encode(ticketIds)}"

    fun chatThread(topic: String?, bookingLabel: String?) =
        "chat/thread?topic=${Uri.encode(topic.orEmpty())}&booking=${Uri.encode(bookingLabel.orEmpty())}"

    fun explore(query: String, dateMillis: Long, returnDateMillis: Long?) =
        "explore/${Uri.encode(query)}?date=$dateMillis&returnDate=${returnDateMillis ?: 0L}"
}

@Composable
fun RideVibeNavGraph(navController: NavHostController) {
    val cart = hiltViewModel<CartViewModel>().cart
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination?.route

    // One shared bottom nav for every screen past welcome, so tab switching
    // is always available. Flow screens (results → ticket) highlight no tab.
    val selectedTab = when (currentRoute) {
        Routes.HOME -> BottomTab.HOME
        Routes.BOOKINGS -> BottomTab.BOOKINGS
        Routes.ITINERARY -> BottomTab.ITINERARY
        Routes.WALLET -> BottomTab.WALLET
        Routes.CHAT, Routes.CHAT_THREAD -> BottomTab.CHAT
        else -> null
    }

    fun navigateToTab(route: String) {
        navController.navigate(route) {
            popUpTo(Routes.HOME) { inclusive = route == Routes.HOME }
            launchSingleTop = true
        }
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        bottomBar = {
            if (currentRoute != Routes.WELCOME) {
                RideVibeBottomNav(
                    selectedTab = selectedTab,
                    onHomeClick = {
                        navController.navigate(Routes.HOME) {
                            popUpTo(Routes.HOME) { inclusive = false }
                            launchSingleTop = true
                        }
                    },
                    onBookingsClick = { navigateToTab(Routes.BOOKINGS) },
                    onItineraryClick = { navigateToTab(Routes.ITINERARY) },
                    onWalletClick = { navigateToTab(Routes.WALLET) },
                    onChatClick = { navigateToTab(Routes.CHAT) },
                )
            }
        },
    ) { padding ->
        NavHost(
            navController = navController,
            startDestination = Routes.WELCOME,
            modifier = Modifier.padding(padding),
        ) {

        composable(Routes.WELCOME) {
            WelcomeScreen(
                onGetStarted = {
                    navController.navigate(Routes.HOME) {
                        popUpTo(Routes.WELCOME) { inclusive = true }
                    }
                },
            )
        }

        composable(Routes.HOME) {
            HomeScreen(
                onSearch = { origin, destination, dateMillis, busClass, adults, children, infants, forSelf ->
                    val leg = if (cart.isRoundTrip) "OUT" else "ONE"
                    navController.navigate(
                        Routes.results(origin, destination, dateMillis, busClass, adults, children, infants, forSelf, leg),
                    )
                },
                onProfileClick = { navController.navigate(Routes.PROFILE) },
                onExplore = { query, dateMillis, returnDateMillis ->
                    navController.navigate(Routes.explore(query, dateMillis, returnDateMillis))
                },
            )
        }

        composable(
            route = Routes.EXPLORE,
            arguments = listOf(
                navArgument("query") { type = NavType.StringType },
                navArgument("date") { type = NavType.StringType; defaultValue = "0" },
                navArgument("returnDate") { type = NavType.StringType; defaultValue = "0" },
            ),
        ) {
            ExploreScreen(
                onBack = { navController.popBackStack() },
                // Journey legs open live trips for that segment on the chosen date.
                onLegSelected = { from, to, dateMillis ->
                    navController.navigate(
                        Routes.results(
                            origin = from,
                            destination = to,
                            dateMillis = dateMillis,
                            busClass = null,
                            adults = 1,
                            children = 0,
                            infants = 0,
                            forSelf = true,
                            leg = "ONE",
                        ),
                    )
                },
                // Single-seat quick booking; passenger counts come from the trip sheet flow.
                onTripSelected = { trip ->
                    if (trip.rideKind != RideKind.BUS) {
                        // Sea services sell passage, not chosen seats (fastcraft
                        // seating is assigned at the port) — straight to checkout.
                        navController.navigate(Routes.checkout(trip.id, "P1", infants = 0, forSelf = true))
                    } else {
                        navController.navigate(Routes.seatMap(trip.id, seatCount = 1, infants = 0, forSelf = true, leg = "ONE"))
                    }
                },
            )
        }

        composable(Routes.ITINERARY) {
            ItineraryScreen(
                onBack = { navController.popBackStack() },
                onProfileClick = { navController.navigate(Routes.PROFILE) },
                onFindLeg = { from, to, dateMillis ->
                    navController.navigate(
                        Routes.results(
                            origin = from,
                            destination = to,
                            dateMillis = dateMillis,
                            busClass = null,
                            adults = 1,
                            children = 0,
                            infants = 0,
                            forSelf = true,
                            leg = "ONE",
                        ),
                    )
                },
                onExploreDestination = { query ->
                    navController.navigate(Routes.explore(query, System.currentTimeMillis(), null))
                },
            )
        }

        composable(Routes.WALLET) {
            WalletScreen(
                onBack = { navController.popBackStack() },
                onProfileClick = { navController.navigate(Routes.PROFILE) },
            )
        }

        composable(Routes.CHAT) {
            SupportScreen(
                onBack = { navController.popBackStack() },
                onProfileClick = { navController.navigate(Routes.PROFILE) },
                onTopicSelected = { topic, bookingLabel ->
                    navController.navigate(Routes.chatThread(topic, bookingLabel))
                },
                onChatNow = { navController.navigate(Routes.chatThread(topic = null, bookingLabel = null)) },
            )
        }

        composable(
            route = Routes.CHAT_THREAD,
            arguments = listOf(
                navArgument("topic") { type = NavType.StringType; defaultValue = "" },
                navArgument("booking") { type = NavType.StringType; defaultValue = "" },
            ),
        ) {
            ChatScreen(
                onBack = { navController.popBackStack() },
                onProfileClick = { navController.navigate(Routes.PROFILE) },
            )
        }

        composable(Routes.BOOKINGS) {
            BookingsScreen(
                onBack = { navController.popBackStack() },
                onOpenTicket = { ticketId -> navController.navigate(Routes.ticket(ticketId)) },
                onProfileClick = { navController.navigate(Routes.PROFILE) },
            )
        }

        composable(Routes.PROFILE) {
            ProfileScreen(onBack = { navController.popBackStack() })
        }

        composable(
            route = Routes.RESULTS,
            arguments = listOf(
                navArgument("origin") { type = NavType.StringType },
                navArgument("destination") { type = NavType.StringType },
                navArgument("dateMillis") { type = NavType.StringType },
                navArgument("busClass") { type = NavType.StringType },
                navArgument("adults") { type = NavType.StringType },
                navArgument("children") { type = NavType.StringType },
                navArgument("infants") { type = NavType.StringType },
                navArgument("forSelf") { type = NavType.StringType },
                navArgument("leg") { type = NavType.StringType },
            ),
        ) { backStackEntry ->
            val args = backStackEntry.arguments
            val seatCount = ((args?.getString("adults")?.toIntOrNull() ?: 1) +
                (args?.getString("children")?.toIntOrNull() ?: 0)).coerceAtLeast(1)
            val infants = args?.getString("infants")?.toIntOrNull() ?: 0
            val forSelf = args?.getString("forSelf")?.toBooleanStrictOrNull() ?: true
            val leg = args?.getString("leg") ?: "ONE"
            ResultsScreen(
                onBack = { navController.popBackStack() },
                onTripSelected = { trip ->
                    if (trip.rideKind != RideKind.BUS) {
                        // Open passage (ferries and fastcrafts): auto-assign one space
                        // per passenger and skip the seat map, mirroring its round-trip
                        // cart handling. Fastcraft seats are assigned at the port.
                        val spaces = (1..seatCount).joinToString(",") { "P$it" }
                        when {
                            leg == "OUT" && cart.isRoundTrip -> {
                                cart.outboundTripId = trip.id
                                cart.outboundSeatIds = spaces.split(",")
                                navController.navigate(
                                    Routes.results(
                                        origin = cart.destination,
                                        destination = cart.origin,
                                        dateMillis = cart.returnDateMillis ?: cart.departDateMillis,
                                        busClass = cart.busClass,
                                        adults = cart.adults,
                                        children = cart.children,
                                        infants = cart.infants,
                                        forSelf = cart.forSelf,
                                        leg = "RET",
                                    ),
                                )
                            }

                            leg == "RET" -> {
                                cart.returnTripId = trip.id
                                cart.returnSeatIds = spaces.split(",")
                                val outTripId = cart.outboundTripId ?: trip.id
                                val outSeats = cart.outboundSeatIds.joinToString(",").ifBlank { spaces }
                                navController.navigate(Routes.checkout(outTripId, outSeats, infants, forSelf))
                            }

                            else -> navController.navigate(Routes.checkout(trip.id, spaces, infants, forSelf))
                        }
                    } else {
                        navController.navigate(Routes.seatMap(trip.id, seatCount, infants, forSelf, leg))
                    }
                },
            )
        }

        composable(
            route = Routes.SEAT_MAP,
            arguments = listOf(
                navArgument("tripId") { type = NavType.StringType },
                navArgument("seatCount") { type = NavType.StringType },
                navArgument("infants") { type = NavType.StringType },
                navArgument("forSelf") { type = NavType.StringType },
                navArgument("leg") { type = NavType.StringType },
            ),
        ) { backStackEntry ->
            val tripId = backStackEntry.arguments?.getString("tripId").orEmpty()
            val infants = backStackEntry.arguments?.getString("infants")?.toIntOrNull() ?: 0
            val forSelf = backStackEntry.arguments?.getString("forSelf")?.toBooleanStrictOrNull() ?: true
            val leg = backStackEntry.arguments?.getString("leg") ?: "ONE"
            SeatMapScreen(
                onBack = { navController.popBackStack() },
                onProceedToCheckout = { seatIdsCsv ->
                    when {
                        // Round trip, outbound chosen: collect it, then search the return leg.
                        leg == "OUT" && cart.isRoundTrip -> {
                            cart.outboundTripId = tripId
                            cart.outboundSeatIds = seatIdsCsv.split(",")
                            navController.navigate(
                                Routes.results(
                                    origin = cart.destination,
                                    destination = cart.origin,
                                    dateMillis = cart.returnDateMillis ?: cart.departDateMillis,
                                    busClass = cart.busClass,
                                    adults = cart.adults,
                                    children = cart.children,
                                    infants = cart.infants,
                                    forSelf = cart.forSelf,
                                    leg = "RET",
                                ),
                            )
                        }

                        // Return leg chosen: both legs in the cart, one itemized checkout.
                        leg == "RET" -> {
                            cart.returnTripId = tripId
                            cart.returnSeatIds = seatIdsCsv.split(",")
                            val outTripId = cart.outboundTripId ?: tripId
                            val outSeats = cart.outboundSeatIds.joinToString(",").ifBlank { seatIdsCsv }
                            navController.navigate(Routes.checkout(outTripId, outSeats, infants, forSelf))
                        }

                        else -> navController.navigate(Routes.checkout(tripId, seatIdsCsv, infants, forSelf))
                    }
                },
            )
        }

        composable(
            route = Routes.CHECKOUT,
            arguments = listOf(
                navArgument("tripId") { type = NavType.StringType },
                navArgument("seats") { type = NavType.StringType },
                navArgument("infants") { type = NavType.StringType },
                navArgument("forSelf") { type = NavType.StringType },
            ),
        ) {
            CheckoutScreen(
                onClose = { navController.popBackStack() },
                onBookingConfirmed = { ticketIdsCsv ->
                    navController.navigate(Routes.ticket(ticketIdsCsv)) {
                        popUpTo(Routes.HOME)
                    }
                },
            )
        }

        composable(
            route = Routes.TICKET,
            arguments = listOf(navArgument("ticketIds") { type = NavType.StringType }),
        ) {
            TicketScreen(
                onBackToHome = {
                    navController.popBackStack(Routes.HOME, inclusive = false)
                },
            )
        }
        }
    }
}
