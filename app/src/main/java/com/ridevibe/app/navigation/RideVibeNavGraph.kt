package com.ridevibe.app.navigation

import android.app.Activity
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.navArgument
import androidx.navigation.navDeepLink
import com.ridevibe.app.auth.PassengerSessionViewModel
import com.ridevibe.app.ui.bookings.BookingsScreen
import com.ridevibe.app.ui.chat.ChatScreen
import com.ridevibe.app.ui.chat.SupportScreen
import com.ridevibe.app.ui.components.BottomTab
import com.ridevibe.app.ui.components.RideVibeBottomNav
import com.ridevibe.app.ui.itinerary.ItineraryScreen
import com.ridevibe.app.ui.profile.ProfileScreen
import com.ridevibe.app.ui.theme.charcoalTopBarColors
import com.ridevibe.app.ui.wallet.WalletScreen
import com.ridevibe.app.ui.welcome.WelcomeScreen
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.android.gms.common.api.ApiException
import com.ridevibe.core.domain.model.BusClass
import com.ridevibe.core.domain.model.RideKind
import com.ridevibe.core.domain.session.CartLeg
import com.ridevibe.feature.admin.ui.StaffConsoleRoot
import com.ridevibe.feature.checkout.ui.CheckoutScreen
import com.ridevibe.feature.search.ui.ExploreScreen
import com.ridevibe.feature.search.ui.HomeScreen
import com.ridevibe.feature.search.ui.ResultsScreen
import com.ridevibe.feature.seatmap.ui.SeatMapScreen
import com.ridevibe.feature.ticket.ui.TicketScreen

private object Routes {
    const val WELCOME = "welcome"
    const val PROFILE = "profile"

    // Tab roots: the route string lives on BottomTab so the bar and the graph
    // can never disagree about which destination a tab owns.
    val HOME = BottomTab.HOME.route
    val BOOKINGS = BottomTab.BOOKINGS.route
    val ITINERARY = BottomTab.ITINERARY.route
    val WALLET = BottomTab.WALLET.route

    // Staff console (admin + partner dashboards consolidated into the app). Has its
    // own tabs, so the passenger bottom nav is hidden while it is showing.
    const val STAFF = "staff"

    // Support: the Chat tab lands on topic triage; the live thread is a
    // separate destination that carries the chosen topic + booking context.
    val CHAT = BottomTab.CHAT.route
    const val CHAT_THREAD = "chat/thread?topic={topic}&booking={booking}"
    const val EXPLORE = "explore/{query}?date={date}&returnDate={returnDate}"
    const val RESULTS = "results/{origin}/{destination}/{dateMillis}/{busClass}/{adults}/{children}/{infants}/{forSelf}/{leg}?rideKind={rideKind}"
    const val SEAT_MAP = "trips/{tripId}/seatmap/{seatCount}/{infants}/{forSelf}/{leg}"
    const val CHECKOUT = "trips/{tripId}/checkout/{seats}/{infants}/{forSelf}"
    const val TICKET = "tickets/{ticketIds}"

    // Deep links (see the manifest's ridevibe:// intent filter).
    const val DEEP_LINK_TICKET = "ridevibe://ticket/{ticketIds}"
    const val DEEP_LINK_BOOKINGS = "ridevibe://booking"

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
        // Null = every kind; Results filters client-side because /v1/trips has no kind param.
        rideKind: RideKind? = null,
    ) = "results/${Uri.encode(origin)}/${Uri.encode(destination)}/$dateMillis/" +
        "${busClass?.name ?: "ANY"}/$adults/$children/$infants/$forSelf/$leg" +
        (rideKind?.let { "?rideKind=${it.name}" } ?: "")

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

/** The return-leg search: origin and destination swapped, same party, same class. */
private fun returnLegResults(cart: com.ridevibe.core.domain.session.BookingCart): String = Routes.results(
    origin = cart.destination,
    destination = cart.origin,
    dateMillis = cart.returnDateMillis ?: cart.departDateMillis,
    busClass = cart.busClass,
    adults = cart.adults,
    children = cart.children,
    infants = cart.infants,
    forSelf = cart.forSelf,
    leg = "RET",
    rideKind = cart.rideKind,
)

/** Destinations that show the passenger bottom nav: the five tab roots plus Profile. */
private val bottomBarRoutes: Set<String> = BottomTab.values().mapTo(mutableSetOf()) { it.route } + Routes.PROFILE

@Composable
fun RideVibeNavGraph(navController: NavHostController) {
    val cart = hiltViewModel<CartViewModel>().cart
    // Activity-scoped (obtained above the NavHost): Welcome writes the session, Profile reads it.
    val sessionViewModel = hiltViewModel<PassengerSessionViewModel>()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination?.route
    val snackbarHostState = remember { SnackbarHostState() }

    // Decided once per activity: a returning rider (or a remembered Google
    // account) lands on Home; everyone else sees Welcome first.
    val startDestination = remember { if (sessionViewModel.hasExistingSession()) Routes.HOME else Routes.WELCOME }

    // The cart persists across process death, so a missing outbound leg on the
    // return leg means the session really is gone: back to Home with a message.
    var sessionLost by remember { mutableStateOf(false) }
    LaunchedEffect(sessionLost) {
        if (sessionLost) {
            snackbarHostState.showSnackbar("Your booking session was lost, start again")
            sessionLost = false
        }
    }
    fun onBookingSessionLost() {
        cart.reset()
        navController.navigate(Routes.HOME) {
            popUpTo(Routes.HOME) { inclusive = true }
            launchSingleTop = true
        }
        sessionLost = true
    }

    val selectedTab = BottomTab.fromRoute(currentRoute)

    // popUpTo(HOME) rather than the graph's start destination: before sign-in
    // the start destination is Welcome, which is popped off once the rider is
    // in, so Home is the stable root every tab returns to. State is saved and
    // restored per tab so switching back keeps scroll position and form input.
    fun navigateToTab(tab: BottomTab) {
        navController.navigate(tab.route) {
            popUpTo(Routes.HOME) { saveState = true }
            launchSingleTop = true
            restoreState = true
        }
    }

    Scaffold(
        // Edge-to-edge: the root pads the status bar in the same charcoal as
        // every top app bar and custom header, so the chrome runs seamlessly
        // under the status icons whether or not a screen insets itself (Home's
        // search header does not). The bottom nav pads itself for the
        // navigation bar, and consumeWindowInsets below keeps nested Scaffolds
        // from insetting the same edges again.
        containerColor = charcoalTopBarColors().containerColor,
        snackbarHost = { SnackbarHost(snackbarHostState) },
        contentWindowInsets = WindowInsets.statusBars,
        bottomBar = {
            if (currentRoute in bottomBarRoutes) {
                RideVibeBottomNav(selectedTab = selectedTab, onTabClick = ::navigateToTab)
            }
        },
    ) { padding ->
        NavHost(
            navController = navController,
            startDestination = startDestination,
            modifier = Modifier.padding(padding).consumeWindowInsets(padding),
        ) {

        composable(Routes.WELCOME) {
            val toHome = {
                navController.navigate(Routes.HOME) {
                    popUpTo(Routes.WELCOME) { inclusive = true }
                }
            }
            WelcomeScreen(
                onGetStarted = toHome,
                onSignedIn = toHome,
                sessionViewModel = sessionViewModel,
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
                // Preferred path: carries the transport tile (bus / ferry / fastcraft)
                // so Results can filter by kind; the cart is primed by the view model.
                onSearchRequest = { req ->
                    val leg = if (req.isRoundTrip) "OUT" else "ONE"
                    navController.navigate(
                        Routes.results(
                            origin = req.origin,
                            destination = req.destination,
                            dateMillis = req.dateMillis,
                            busClass = req.busClass,
                            adults = req.adults,
                            children = req.children,
                            infants = req.infants,
                            forSelf = req.bookingForSelf,
                            leg = leg,
                            rideKind = req.rideKind,
                        ),
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
                // One-way pick: the Explore view model primes the cart with the
                // party from the header search, so counts come from there.
                onTripSelected = { trip ->
                    val seatCount = (cart.adults + cart.children).coerceAtLeast(1)
                    if (trip.rideKind.sellsPassage) {
                        // Sea services sell passage, not chosen seats (fastcraft
                        // seating is assigned at the port) — straight to checkout.
                        val spaces = (1..seatCount).joinToString(",") { "P$it" }
                        cart.setOutboundLeg(trip.id, spaces.split(","), farePhp = trip.farePhp, rideKind = trip.rideKind)
                        navController.navigate(Routes.checkout(trip.id, spaces, cart.infants, cart.forSelf))
                    } else {
                        navController.navigate(Routes.seatMap(trip.id, seatCount, cart.infants, cart.forSelf, leg = "ONE"))
                    }
                },
                // Round-trip explore: outbound pick loops back through Results for
                // the return leg exactly like the Home flow.
                onTripSelectedForLeg = { trip, leg ->
                    val seatCount = (cart.adults + cart.children).coerceAtLeast(1)
                    val legArg = when (leg) {
                        CartLeg.OUTBOUND -> "OUT"
                        CartLeg.RETURN -> "RET"
                        CartLeg.ONE_WAY -> "ONE"
                    }
                    if (trip.rideKind.sellsPassage) {
                        val spaces = (1..seatCount).map { "P$it" }
                        when (leg) {
                            CartLeg.OUTBOUND -> {
                                cart.setOutboundLeg(trip.id, spaces, farePhp = trip.farePhp, rideKind = trip.rideKind)
                                navController.navigate(returnLegResults(cart))
                            }
                            CartLeg.RETURN -> {
                                val outTripId = cart.outboundTripId
                                if (outTripId == null) {
                                    onBookingSessionLost()
                                } else {
                                    cart.setReturnLeg(trip.id, spaces, farePhp = trip.farePhp)
                                    navController.navigate(
                                        Routes.checkout(outTripId, cart.outboundSeatIds.joinToString(","), cart.infants, cart.forSelf),
                                    )
                                }
                            }
                            CartLeg.ONE_WAY -> {
                                cart.setOutboundLeg(trip.id, spaces, farePhp = trip.farePhp, rideKind = trip.rideKind)
                                navController.navigate(Routes.checkout(trip.id, spaces.joinToString(","), cart.infants, cart.forSelf))
                            }
                        }
                    } else {
                        navController.navigate(Routes.seatMap(trip.id, seatCount, cart.infants, cart.forSelf, legArg))
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

        composable(
            route = Routes.BOOKINGS,
            deepLinks = listOf(navDeepLink { uriPattern = Routes.DEEP_LINK_BOOKINGS }),
        ) {
            BookingsScreen(
                onBack = { navController.popBackStack() },
                onOpenTicket = { ticketId -> navController.navigate(Routes.ticket(ticketId)) },
                onProfileClick = { navController.navigate(Routes.PROFILE) },
            )
        }

        composable(Routes.PROFILE) {
            ProfileScreen(
                onBack = { navController.popBackStack() },
                onOpenStaffConsole = { navController.navigate(Routes.STAFF) },
                onSignIn = { navController.navigate(Routes.WELCOME) },
                // Signed out: nothing behind Welcome, so Back leaves the app rather than reopening Profile.
                onSignedOut = {
                    navController.navigate(Routes.WELCOME) {
                        popUpTo(navController.graph.id) { inclusive = true }
                    }
                },
                sessionViewModel = sessionViewModel,
            )
        }

        composable(Routes.STAFF) {
            val context = LocalContext.current
            // The console asks for a Google ID token minted against the backend's
            // web client id; Play Services is an app-module dependency, so the
            // launcher lives here and hands the token back through the callback.
            var pendingToken by remember { mutableStateOf<((String) -> Unit)?>(null) }
            val googleLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
                val onToken = pendingToken ?: return@rememberLauncherForActivityResult
                pendingToken = null
                if (result.resultCode != Activity.RESULT_OK) return@rememberLauncherForActivityResult
                val token = runCatching {
                    GoogleSignIn.getSignedInAccountFromIntent(result.data).getResult(ApiException::class.java)?.idToken
                }.getOrNull()
                if (token != null) onToken(token)
            }
            StaffConsoleRoot(
                onExit = { navController.popBackStack() },
                onGoogleSignIn = { clientId, onToken ->
                    pendingToken = onToken
                    val options = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
                        .requestIdToken(clientId)
                        .requestEmail()
                        .build()
                    val client = GoogleSignIn.getClient(context, options)
                    // Sign out first so the account chooser always appears for staff.
                    client.signOut().addOnCompleteListener { googleLauncher.launch(client.signInIntent) }
                },
            )
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
                navArgument("rideKind") { type = NavType.StringType; nullable = true; defaultValue = null },
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
                    if (trip.rideKind.sellsPassage) {
                        // Open passage (ferries and fastcrafts): auto-assign one space
                        // per passenger and skip the seat map, mirroring its round-trip
                        // cart handling. Fastcraft seats are assigned at the port.
                        // Nothing is held server-side, so there is no hold expiry.
                        val spaces = (1..seatCount).map { "P$it" }
                        when {
                            leg == "OUT" && cart.isRoundTrip -> {
                                cart.setOutboundLeg(trip.id, spaces, farePhp = trip.farePhp, rideKind = trip.rideKind)
                                navController.navigate(returnLegResults(cart))
                            }

                            leg == "RET" -> {
                                val outTripId = cart.outboundTripId
                                if (outTripId == null) {
                                    onBookingSessionLost()
                                } else {
                                    cart.setReturnLeg(trip.id, spaces, farePhp = trip.farePhp)
                                    val outSeats = cart.outboundSeatIds.joinToString(",")
                                    navController.navigate(Routes.checkout(outTripId, outSeats, infants, forSelf))
                                }
                            }

                            else -> {
                                cart.setOutboundLeg(trip.id, spaces, farePhp = trip.farePhp, rideKind = trip.rideKind)
                                navController.navigate(Routes.checkout(trip.id, spaces.joinToString(","), infants, forSelf))
                            }
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
                // Kept for API compatibility; onSeatsConfirmed below is what the screen calls.
                onProceedToCheckout = { seatIdsCsv ->
                    navController.navigate(Routes.checkout(tripId, seatIdsCsv, infants, forSelf))
                },
                // The selection carries the server hold expiry and fare so checkout can
                // run its own countdown and itemise both legs from the cart.
                onSeatsConfirmed = { sel ->
                    when {
                        // Round trip, outbound chosen: collect it, then search the return leg.
                        leg == "OUT" && cart.isRoundTrip -> {
                            cart.setOutboundLeg(sel.tripId, sel.seatIds, sel.holdExpiresAtEpochMillis, sel.farePhp, sel.rideKind)
                            navController.navigate(returnLegResults(cart))
                        }

                        // Return leg chosen: both legs in the cart, one itemized checkout.
                        leg == "RET" -> {
                            val outTripId = cart.outboundTripId
                            if (outTripId == null) {
                                onBookingSessionLost()
                            } else {
                                cart.setReturnLeg(sel.tripId, sel.seatIds, sel.holdExpiresAtEpochMillis, sel.farePhp)
                                val outSeats = cart.outboundSeatIds.joinToString(",")
                                navController.navigate(Routes.checkout(outTripId, outSeats, infants, forSelf))
                            }
                        }

                        else -> {
                            cart.setOutboundLeg(sel.tripId, sel.seatIds, sel.holdExpiresAtEpochMillis, sel.farePhp, sel.rideKind)
                            navController.navigate(Routes.checkout(sel.tripId, sel.seatIdsCsv, infants, forSelf))
                        }
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
                // CheckoutViewModel resets the BookingCart itself once the booking
                // is confirmed, so the nav layer only moves on to the ticket.
                onBookingConfirmed = { ticketIdsCsv ->
                    navController.navigate(Routes.ticket(ticketIdsCsv)) {
                        popUpTo(Routes.HOME)
                    }
                },
                // The hold lapsed mid-checkout: the seat map below re-checks and
                // lets the rider pick again (a lapsed return leg is dropped too).
                onHoldExpired = {
                    if (cart.isRoundTrip) cart.clearReturnLeg()
                    navController.popBackStack()
                },
            )
        }

        composable(
            route = Routes.TICKET,
            arguments = listOf(navArgument("ticketIds") { type = NavType.StringType }),
            deepLinks = listOf(navDeepLink { uriPattern = Routes.DEEP_LINK_TICKET }),
        ) {
            TicketScreen(
                onBackToHome = {
                    navController.popBackStack(Routes.HOME, inclusive = false)
                },
                onBookAgain = {
                    cart.reset()
                    navController.popBackStack(Routes.HOME, inclusive = false)
                },
            )
        }
        }
    }
}
