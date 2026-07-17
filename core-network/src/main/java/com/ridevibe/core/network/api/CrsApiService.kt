package com.ridevibe.core.network.api

import com.ridevibe.core.network.dto.BookingRequestDto
import com.ridevibe.core.network.dto.LocationDto
import com.ridevibe.core.network.dto.SeatDto
import com.ridevibe.core.network.dto.TicketDto
import com.ridevibe.core.network.dto.TripDto
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Path
import retrofit2.http.Query

interface CrsApiService {

    @GET("v1/locations")
    suspend fun getLocations(): List<LocationDto>

    @GET("v1/bus-classes")
    suspend fun getBusClasses(): List<String>

    @GET("v1/trips")
    suspend fun searchTrips(
        @Query("origin") origin: String,
        @Query("destination") destination: String,
        @Query("departureDate") departureDateEpochMillis: Long,
        @Query("busClass") busClass: String? = null,
    ): List<TripDto>

    @GET("v1/trips/{tripId}")
    suspend fun getTrip(@Path("tripId") tripId: String): TripDto

    @GET("v1/tickets/{ticketId}")
    suspend fun getTicket(@Path("ticketId") ticketId: String): TicketDto

    @GET("v1/bookings")
    suspend fun getMyBookings(): List<TicketDto>

    @GET("v1/trips/{tripId}/seatmap")
    suspend fun getSeatMap(@Path("tripId") tripId: String): List<SeatDto>

    @POST("v1/trips/{tripId}/seats/{seatId}/lock")
    suspend fun lockSeat(@Path("tripId") tripId: String, @Path("seatId") seatId: String)

    @POST("v1/trips/{tripId}/seats/{seatId}/release")
    suspend fun releaseSeat(@Path("tripId") tripId: String, @Path("seatId") seatId: String)

    @POST("v1/trips/{tripId}/book")
    suspend fun confirmBooking(
        @Path("tripId") tripId: String,
        @Body request: BookingRequestDto,
    ): TicketDto
}
