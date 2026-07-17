package com.ridevibe.core.network.api

import com.ridevibe.core.network.dto.BookingRequestDto
import com.ridevibe.core.network.dto.SeatDto
import com.ridevibe.core.network.dto.TicketDto
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Path

interface CrsApiService {

    @GET("v1/trips/{tripId}/seatmap")
    suspend fun getSeatMap(@Path("tripId") tripId: String): List<SeatDto>

    @POST("v1/trips/{tripId}/seats/{seatId}/lock")
    suspend fun lockSeat(@Path("tripId") tripId: String, @Path("seatId") seatId: String)

    @POST("v1/trips/{tripId}/seats/{seatId}/release")
    suspend fun releaseSeat(@Path("tripId") tripId: String, @Path("seatId") seatId: String)

    @POST("v1/trips/{tripId}/seats/{seatId}/book")
    suspend fun confirmBooking(
        @Path("tripId") tripId: String,
        @Path("seatId") seatId: String,
        @Body request: BookingRequestDto,
    ): TicketDto
}
