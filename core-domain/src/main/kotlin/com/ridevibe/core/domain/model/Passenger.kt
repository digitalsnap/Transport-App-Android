package com.ridevibe.core.domain.model

data class Passenger(
    val fullName: String,
    val type: PassengerType,
    val discountIdImagePath: String? = null,
)

enum class PassengerType(val discountRate: Double) {
    REGULAR(discountRate = 0.0),
    STUDENT(discountRate = 0.20),
    SENIOR_CITIZEN(discountRate = 0.20),
    PWD(discountRate = 0.20);

    val requiresIdCapture: Boolean
        get() = this != REGULAR
}
