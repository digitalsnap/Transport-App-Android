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
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.navArgument
import com.ridevibe.app.ui.bookings.BookingsScreen
import com.ridevibe.app.ui.components.BottomTab
import com.ridevibe.app.ui.components.RideVibeBottomNav
import com.ridevibe.app.ui.profile.ProfileScreen
import com.ridevibe.app.ui.welcome.WelcomeScreen
import com.ridevibe.core.domain.model.BusClass
import com.ridevibe.feature.checkout.ui.CheckoutScreen
import com.ridevibe.feature.search.ui.HomeScreen
import com.ridevibe.feature.search.ui.ResultsScreen
import com.ridevibe.feature.seatmap.ui.SeatMapScreen
import com.ridevibe.feature.ticket.ui.TicketScreen

private object Routes {
    const val WELCOME = "welcome"
    const val HOME = "home"
    const val PROFILE = "profile"
    const val BOOKINGS = "bookings"
    const val RESULTS = "results/{origin}/{destination}/{dateMillis}/{busClass}/{adults}/{children}/{infants}/{forSelf}"
    const val SEAT_MAP = "trips/{tripId}/seatmap/{seatCount}/{infants}/{forSelf}"
    const val CHECKOUT = "trips/{tripId}/checkout/{seats}/{infants}/{forSelf}"
    const val TICKET = "tickets/{ticketId}"

    fun results(
        origin: String,
        destination: String,
        dateMillis: Long,
        busClass: BusClass?,
        adults: Int,
        children: Int,
        infants: Int,
        forSelf: Boolean,
    ) = "results/${Uri.encode(origin)}/${Uri.encode(destination)}/$dateMillis/" +
        "${busClass?.name ?: "ANY"}/$adults/$children/$infants/$forSelf"

    fun seatMap(tripId: String, seatCount: Int, infants: Int, forSelf: Boolean) =
        "trips/${Uri.encode(tripId)}/seatmap/$seatCount/$infants/$forSelf"

    fun checkout(tripId: String, seatIdsCsv: String, infants: Int, forSelf: Boolean) =
        "trips/${Uri.encode(tripId)}/checkout/${Uri.encode(seatIdsCsv)}/$infants/$forSelf"

    fun ticket(ticketId: String) = "tickets/${Uri.encode(ticketId)}"
}

@Composable
fun RideVibeNavGraph(navController: NavHostController) {
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination?.route

    // One shared bottom nav for every screen past welcome, so tab switching
    // is always available. Flow screens (results → ticket) highlight no tab.
    val selectedTab = when (currentRoute) {
        Routes.HOME -> BottomTab.HOME
        Routes.BOOKINGS -> BottomTab.BOOKINGS
        Routes.PROFILE -> BottomTab.PROFILE
        else -> null
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
                    onBookingsClick = {
                        navController.navigate(Routes.BOOKINGS) {
                            popUpTo(Routes.HOME)
                            launchSingleTop = true
                        }
                    },
                    onProfileClick = {
                        navController.navigate(Routes.PROFILE) {
                            popUpTo(Routes.HOME)
                            launchSingleTop = true
                        }
                    },
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
                    navController.navigate(
                        Routes.results(origin, destination, dateMillis, busClass, adults, children, infants, forSelf),
                    )
                },
            )
        }

        composable(Routes.BOOKINGS) {
            BookingsScreen(
                onBack = { navController.popBackStack() },
                onOpenTicket = { ticketId -> navController.navigate(Routes.ticket(ticketId)) },
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
            ),
        ) { backStackEntry ->
            val args = backStackEntry.arguments
            val seatCount = ((args?.getString("adults")?.toIntOrNull() ?: 1) +
                (args?.getString("children")?.toIntOrNull() ?: 0)).coerceAtLeast(1)
            val infants = args?.getString("infants")?.toIntOrNull() ?: 0
            val forSelf = args?.getString("forSelf")?.toBooleanStrictOrNull() ?: true
            ResultsScreen(
                onBack = { navController.popBackStack() },
                onViewSeats = { tripId ->
                    navController.navigate(Routes.seatMap(tripId, seatCount, infants, forSelf))
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
            ),
        ) { backStackEntry ->
            val tripId = backStackEntry.arguments?.getString("tripId").orEmpty()
            val infants = backStackEntry.arguments?.getString("infants")?.toIntOrNull() ?: 0
            val forSelf = backStackEntry.arguments?.getString("forSelf")?.toBooleanStrictOrNull() ?: true
            SeatMapScreen(
                onBack = { navController.popBackStack() },
                onProceedToCheckout = { seatIdsCsv ->
                    navController.navigate(Routes.checkout(tripId, seatIdsCsv, infants, forSelf))
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
                onBookingConfirmed = { ticketId ->
                    navController.navigate(Routes.ticket(ticketId)) {
                        popUpTo(Routes.HOME)
                    }
                },
            )
        }

        composable(
            route = Routes.TICKET,
            arguments = listOf(navArgument("ticketId") { type = NavType.StringType }),
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
