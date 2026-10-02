package com.ridevibe.feature.checkout.viewmodel

/**
 * Field rules for the passenger forms. Pure functions so the view model (not
 * the composables) decides what blocks "Pay". Inputs are trimmed before
 * checking — a trailing space is not a reason to refuse a booking.
 */
object CheckoutValidation {
    private const val NAME_MIN = 2
    private const val NAME_MAX = 60

    /** Letters (any script, so ñ and accented names pass), spaces, hyphens and apostrophes. */
    private val nameRegex = Regex("^[\\p{L}' -]+$")

    /** Philippine mobile numbers as riders type them: `09XXXXXXXXX` or `+639XXXXXXXXX`. */
    private val mobileRegex = Regex("^(09\\d{9}|\\+639\\d{9})$")

    private val emailRegex = Regex("^[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}$")

    /**
     * A candidate ID number in OCR output: a run of digits and dashes at least
     * six characters long that starts and ends with a digit, so a bare
     * "------" underline never qualifies. Government IDs (OSCA, PWD, school)
     * are all numeric with optional dash groups, which is what this catches.
     */
    private val idNumberRegex = Regex("\\d[\\d-]{4,}\\d")

    fun nameError(raw: String, label: String = "Name"): String? {
        val value = raw.trim()
        return when {
            value.isEmpty() -> "$label is required"
            value.length < NAME_MIN -> "$label must be at least $NAME_MIN letters"
            value.length > NAME_MAX -> "$label must be $NAME_MAX characters or fewer"
            !nameRegex.matches(value) -> "Letters, spaces, hyphens and apostrophes only"
            else -> null
        }
    }

    fun mobileError(raw: String, required: Boolean): String? {
        val value = raw.trim().replace(" ", "")
        return when {
            value.isEmpty() -> if (required) "Mobile number is required" else null
            !mobileRegex.matches(value) -> "Use 09XXXXXXXXX or +639XXXXXXXXX"
            else -> null
        }
    }

    fun emailError(raw: String, required: Boolean): String? {
        val value = raw.trim()
        return when {
            value.isEmpty() -> if (required) "Email is required" else null
            !emailRegex.matches(value) -> "Enter a valid email address"
            else -> null
        }
    }

    /** The longest digit/dash run in [ocrText] that looks like an ID number, or null. */
    fun extractIdNumber(ocrText: String): String? =
        idNumberRegex.findAll(ocrText)
            .map { it.value }
            .filter { it.length >= 6 }
            .maxByOrNull { it.length }
}
