package com.ridevibe.app.ui.profile

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ridevibe.core.domain.format.PhTime
import com.ridevibe.core.domain.model.UserProfile
import com.ridevibe.core.domain.model.Vehicle
import com.ridevibe.core.domain.repository.ProfileRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.Calendar
import java.util.Locale
import javax.inject.Inject

/** Per-field validation messages; null means the field is acceptable. */
data class ProfileFieldErrors(
    val email: String? = null,
    val mobileNumber: String? = null,
    val birthDate: String? = null,
    val emergencyContactNumber: String? = null,
) {
    val hasErrors: Boolean
        get() = listOf(email, mobileNumber, birthDate, emergencyContactNumber).any { it != null }
}

data class ProfileUiState(
    val profile: UserProfile = UserProfile(),
    val vehicles: List<Vehicle> = emptyList(),
    val isLoading: Boolean = true,
    /** Load failure; the screen offers a retry. */
    val error: String? = null,
    val isSaving: Boolean = false,
    /** Shown once in a Snackbar, then cleared via [ProfileViewModel.onSavedMessageShown]. */
    val savedMessage: String? = null,
    val fieldErrors: ProfileFieldErrors = ProfileFieldErrors(),
    /** True when the phone number came from the device contacts picker. */
    val phoneFromContacts: Boolean = false,
    // "Add a Vehicle" form
    val newPlateNumber: String = "",
    val newCertificateUri: String? = null,
    /** The picked document's display name, so the rider sees which file is attached. */
    val newCertificateName: String? = null,
    val vehicleError: String? = null,
) {
    val canSaveProfile: Boolean get() = profile.firstName.isNotBlank() && !isSaving && !isLoading
    val canAddVehicle: Boolean get() = newPlateNumber.isNotBlank() && newCertificateUri != null
}

// ProfileRepository is injected directly (no use-case layer): the operations
// are pure CRUD passthroughs; the only domain rules are the field formats below.
@HiltViewModel
class ProfileViewModel @Inject constructor(
    private val profileRepository: ProfileRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(ProfileUiState())
    val uiState: StateFlow<ProfileUiState> = _uiState.asStateFlow()

    init {
        load()
    }

    fun load() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null) }
            try {
                val profile = profileRepository.getProfile()
                val vehicles = profileRepository.getVehicles()
                _uiState.update { it.copy(profile = profile, vehicles = vehicles, isLoading = false) }
            } catch (e: Exception) {
                _uiState.update { it.copy(isLoading = false, error = e.message ?: "Could not load your profile") }
            }
        }
    }

    fun onProfileFieldChanged(transform: (UserProfile) -> UserProfile) {
        _uiState.update { it.copy(profile = transform(it.profile), fieldErrors = ProfileFieldErrors()) }
    }

    /** Manual phone edits clear the "from contacts" provenance badge. */
    fun onPhoneEdited(value: String) {
        _uiState.update {
            it.copy(
                profile = it.profile.copy(mobileNumber = value),
                phoneFromContacts = false,
                fieldErrors = it.fieldErrors.copy(mobileNumber = null),
            )
        }
    }

    /** The contacts picker supplied the phone number. */
    fun onPhonePickedFromContacts(number: String) {
        if (number.isBlank()) return
        _uiState.update {
            it.copy(
                profile = it.profile.copy(mobileNumber = number.trim()),
                phoneFromContacts = true,
                fieldErrors = it.fieldErrors.copy(mobileNumber = null),
            )
        }
    }

    fun saveProfile() {
        val state = _uiState.value
        if (!state.canSaveProfile) return
        val errors = validate(state.profile)
        if (errors.hasErrors) {
            _uiState.update { it.copy(fieldErrors = errors) }
            return
        }
        viewModelScope.launch {
            _uiState.update { it.copy(isSaving = true) }
            try {
                profileRepository.saveProfile(state.profile).getOrThrow()
                _uiState.update { it.copy(isSaving = false, savedMessage = "Profile saved") }
            } catch (e: Exception) {
                _uiState.update { it.copy(isSaving = false, savedMessage = e.message ?: "Save failed") }
            }
        }
    }

    fun onSavedMessageShown() = _uiState.update { it.copy(savedMessage = null) }

    fun onNewPlateNumberChanged(value: String) {
        _uiState.update { it.copy(newPlateNumber = value, vehicleError = null) }
    }

    /**
     * A document the rider picked as the LTO Certificate of Registration. Only
     * photos/scans and PDFs are accepted, capped at [MAX_CERTIFICATE_BYTES] so a
     * later upload to the CRS stays within a mobile-friendly size.
     */
    fun onCertificatePicked(uri: String, displayName: String?, mimeType: String?, sizeBytes: Long?) {
        val isImageOrPdf = mimeType != null && (mimeType.startsWith("image/") || mimeType == "application/pdf")
        val error = when {
            !isImageOrPdf -> "Attach a photo, scan or PDF of the certificate."
            sizeBytes != null && sizeBytes > MAX_CERTIFICATE_BYTES -> "That file is over 10 MB. Pick a smaller photo or PDF."
            else -> null
        }
        _uiState.update {
            if (error != null) {
                it.copy(vehicleError = error)
            } else {
                it.copy(newCertificateUri = uri, newCertificateName = displayName, vehicleError = null)
            }
        }
    }

    fun addVehicle() {
        val state = _uiState.value
        val certUri = state.newCertificateUri ?: return
        viewModelScope.launch {
            try {
                profileRepository.addVehicle(state.newPlateNumber.trim(), certUri).getOrThrow()
                _uiState.update { it.copy(newPlateNumber = "", newCertificateUri = null, newCertificateName = null) }
                refreshVehicles()
            } catch (e: Exception) {
                _uiState.update { it.copy(vehicleError = e.message ?: "Could not add vehicle") }
            }
        }
    }

    fun removeVehicle(vehicleId: String) {
        viewModelScope.launch {
            try {
                profileRepository.removeVehicle(vehicleId).getOrThrow()
                refreshVehicles()
            } catch (e: Exception) {
                _uiState.update { it.copy(vehicleError = e.message ?: "Could not remove vehicle") }
            }
        }
    }

    private suspend fun refreshVehicles() {
        try {
            _uiState.update { it.copy(vehicles = profileRepository.getVehicles()) }
        } catch (e: Exception) {
            _uiState.update { it.copy(vehicleError = e.message ?: "Could not refresh vehicles") }
        }
    }

    private fun validate(profile: UserProfile): ProfileFieldErrors = ProfileFieldErrors(
        email = profile.email.takeIf { it.isNotBlank() }?.let { email ->
            if (EMAIL_REGEX.matches(email.trim())) null else "Enter a valid email address."
        },
        mobileNumber = profile.mobileNumber.takeIf { it.isNotBlank() }?.let { number ->
            if (isPhMobile(number)) null else "Use a PH mobile number: 09XXXXXXXXX or +639XXXXXXXXX."
        },
        birthDate = profile.birthDate.takeIf { it.isNotBlank() }?.let { iso -> birthDateError(iso) },
        emergencyContactNumber = profile.emergencyContactNumber.takeIf { it.isNotBlank() }?.let { number ->
            if (isPhMobile(number) || isPhLandline(number)) null else "Enter a PH mobile or landline number."
        },
    )

    private fun birthDateError(iso: String): String? {
        if (!PhTime.isValidIso(iso) || !isRealDate(iso)) return "Enter a real date."
        val latest = PhTime.plusDays(PhTime.todayIso(), -1)
        return when {
            iso < EARLIEST_BIRTH_DATE -> "Birthdate must be after 1920."
            iso > latest -> "Birthdate must be in the past."
            else -> null
        }
    }

    /** `yyyy-MM-dd` names a day that exists (no 31 February). ISO strings compare chronologically as text. */
    private fun isRealDate(iso: String): Boolean {
        val (year, month, day) = iso.split("-").map { it.toInt() }
        return runCatching {
            Calendar.getInstance(PhTime.zone, Locale.US).apply {
                isLenient = false
                clear()
                set(year, month - 1, day)
                timeInMillis
            }
        }.isSuccess
    }

    private companion object {
        const val MAX_CERTIFICATE_BYTES = 10L * 1024 * 1024
        const val EARLIEST_BIRTH_DATE = "1920-01-01"
        val EMAIL_REGEX = Regex("""^[^@\s]+@[^@\s]+\.[^@\s]+$""")

        /** Globe/Smart/DITO mobile numbers: 09XXXXXXXXX or +639XXXXXXXXX; spaces and dashes tolerated. */
        val PH_MOBILE_REGEX = Regex("""^(09\d{9}|\+639\d{9})$""")

        /** Area code plus 7-8 digits (02-XXXXXXXX, 0xx-XXXXXXX). */
        val PH_LANDLINE_REGEX = Regex("""^0\d{8,10}$""")

        fun isPhMobile(raw: String) = PH_MOBILE_REGEX.matches(raw.filterNot { it == ' ' || it == '-' })

        fun isPhLandline(raw: String) = PH_LANDLINE_REGEX.matches(raw.filterNot { it == ' ' || it == '-' })
    }
}
