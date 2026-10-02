package com.ridevibe.feature.admin.auth

/**
 * The person backed out of the Google account chooser. The login screen
 * swallows it: closing a picker is not an error worth a red line.
 */
class GoogleSignInCancelled : Exception("Google sign-in cancelled")

/** Play Services (or the account) could not produce an ID token; [message] is ready to show on the login screen. */
class GoogleSignInFailed(message: String) : Exception(message) {
    companion object {
        fun noIdToken() = GoogleSignInFailed("Google returned no ID token")
    }
}

// Play Services status codes (CommonStatusCodes / GoogleSignInStatusCodes). Spelled out here because
// this module deliberately has no Play Services dependency: the host app runs the flow and hands
// back only the code.
private const val STATUS_CANCELED = 16
private const val STATUS_NETWORK_ERROR = 7
private const val STATUS_DEVELOPER_ERROR = 10
private const val STATUS_SIGN_IN_CANCELLED = 12501

/** Maps an `ApiException.statusCode` from the Google flow to the exception the login screen understands. */
fun googleSignInFailure(statusCode: Int): Exception = when (statusCode) {
    STATUS_CANCELED, STATUS_SIGN_IN_CANCELLED -> GoogleSignInCancelled()
    // The OAuth client (SHA-1 / package) is not registered for this build's signing key.
    STATUS_DEVELOPER_ERROR -> GoogleSignInFailed("Google sign-in is not configured for this build (SHA-1 / web client id)")
    STATUS_NETWORK_ERROR -> GoogleSignInFailed("You're offline")
    else -> GoogleSignInFailed("Google sign-in failed (code $statusCode)")
}
