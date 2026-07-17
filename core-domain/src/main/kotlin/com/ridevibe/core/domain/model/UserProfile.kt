package com.ridevibe.core.domain.model

data class UserProfile(
    val fullName: String = "",
    val email: String = "",
    val mobileNumber: String = "",
    val birthDate: String = "", // ISO yyyy-MM-dd; validated server-side once live
    val address: String = "",
    val emergencyContactName: String = "",
    val emergencyContactNumber: String = "",
)

/**
 * A customer-registered vehicle. Currently informational; will back
 * Roll-on/Roll-off (RORO) ferry bookings when that vertical launches.
 */
data class Vehicle(
    val id: String,
    val plateNumber: String,
    /** Local URI of the uploaded LTO Certificate of Registration photo/scan. */
    val ltoCertificateUri: String,
)
