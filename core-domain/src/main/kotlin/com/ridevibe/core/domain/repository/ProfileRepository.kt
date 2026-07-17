package com.ridevibe.core.domain.repository

import com.ridevibe.core.domain.model.UserProfile
import com.ridevibe.core.domain.model.Vehicle

interface ProfileRepository {
    suspend fun getProfile(): UserProfile
    suspend fun saveProfile(profile: UserProfile): Result<Unit>
    suspend fun getVehicles(): List<Vehicle>
    suspend fun addVehicle(plateNumber: String, ltoCertificateUri: String): Result<Vehicle>
    suspend fun removeVehicle(vehicleId: String): Result<Unit>
}
