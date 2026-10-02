package com.ridevibe.app.auth

/** How the passenger got past the Welcome screen. */
enum class SignInProvider { GOOGLE, FACEBOOK, GUEST }

/**
 * The passenger identity the app shows (Profile header, greeting). Device-local:
 * see [PassengerSessionStore] for why nothing here reaches the backend yet.
 */
data class PassengerSession(
    val provider: SignInProvider,
    val displayName: String? = null,
    val email: String? = null,
    val photoUrl: String? = null,
    val idToken: String? = null,
) {
    val isGuest: Boolean get() = provider == SignInProvider.GUEST

    /** "Juan Dela Cruz" becomes "JD"; falls back to the email's first letter, then "?" for the avatar. */
    val initials: String
        get() {
            val fromName = displayName.orEmpty()
                .split(' ')
                .filter { it.isNotBlank() }
                .take(2)
                .joinToString("") { it.first().uppercaseChar().toString() }
            if (fromName.isNotEmpty()) return fromName
            return email?.firstOrNull()?.uppercaseChar()?.toString() ?: "?"
        }
}
