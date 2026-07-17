package com.ridevibe.app.navigation

import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import com.ridevibe.feature.checkout.ui.CheckoutScreen
import com.ridevibe.feature.seatmap.ui.SeatMapScreen

private object Routes {
    const val SEARCH = "search"
    const val SEAT_MAP = "trips/{tripId}/seatmap"
    const val CHECKOUT = "trips/{tripId}/seats/{seatId}/checkout"
    const val TICKET = "tickets/{ticketId}"

    fun seatMap(tripId: String) = "trips/$tripId/seatmap"
    fun checkout(tripId: String, seatId: String) = "trips/$tripId/seats/$seatId/checkout"
    fun ticket(ticketId: String) = "tickets/$ticketId"
}

@Composable
fun RideVibeNavGraph(navController: NavHostController) {
    NavHost(navController = navController, startDestination = Routes.SEARCH) {

        composable(Routes.SEARCH) {
            // TODO: wire up feature-search's SearchScreen once route/date picker UI lands.
            Text("Search screen placeholder — feature-search:SearchScreen")
        }

        composable(
            route = Routes.SEAT_MAP,
            arguments = listOf(navArgument("tripId") { type = NavType.StringType }),
        ) { backStackEntry ->
            val tripId = backStackEntry.arguments?.getString("tripId").orEmpty()
            SeatMapScreen(
                onProceedToCheckout = { seatId -> navController.navigate(Routes.checkout(tripId, seatId)) },
            )
        }

        composable(
            route = Routes.CHECKOUT,
            arguments = listOf(
                navArgument("tripId") { type = NavType.StringType },
                navArgument("seatId") { type = NavType.StringType },
            ),
        ) {
            CheckoutScreen(
                onBookingConfirmed = { ticketId ->
                    navController.navigate(Routes.ticket(ticketId)) {
                        popUpTo(Routes.SEARCH)
                    }
                },
            )
        }

        composable(
            route = Routes.TICKET,
            arguments = listOf(navArgument("ticketId") { type = NavType.StringType }),
        ) {
            // TODO: load the confirmed Ticket by id and render feature-ticket's DigitalTicketCard.
            Text("Ticket screen placeholder — feature-ticket:DigitalTicketCard")
        }
    }
}
