package com.ridevibe.app.ui.profile

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ridevibe.core.domain.model.UserProfile
import com.ridevibe.core.domain.model.Vehicle
import com.ridevibe.core.domain.repository.ProfileRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class ProfileUiState(
    val profile: UserProfile = UserProfile(),
    val vehicles: List<Vehicle> = emptyList(),
    val isSaving: Boolean = false,
    val savedMessage: String? = null,
    // "Add a Vehicle" form
    val newPlateNumber: String = "",
    val newCertificateUri: String? = null,
    val vehicleError: String? = null,
) {
    val canSaveProfile: Boolean get() = profile.fullName.isNotBlank() && !isSaving
    val canAddVehicle: Boolean get() = newPlateNumber.isNotBlank() && newCertificateUri != null
}

// ProfileRepository is injected directly (no use-case layer): the operations
// are pure CRUD passthroughs with no domain logic to encapsulate yet.
@HiltViewModel
class ProfileViewModel @Inject constructor(
    private val profileRepository: ProfileRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(ProfileUiState())
    val uiState: StateFlow<ProfileUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            val profile = profileRepository.getProfile()
            val vehicles = profileRepository.getVehicles()
            _uiState.update { it.copy(profile = profile, vehicles = vehicles) }
        }
    }

    fun onProfileFieldChanged(transform: (UserProfile) -> UserProfile) {
        _uiState.update { it.copy(profile = transform(it.profile), savedMessage = null) }
    }

    fun saveProfile() {
        val state = _uiState.value
        if (!state.canSaveProfile) return
        viewModelScope.launch {
            _uiState.update { it.copy(isSaving = true) }
            profileRepository.saveProfile(state.profile)
                .onSuccess { _uiState.update { it.copy(isSaving = false, savedMessage = "Profile saved") } }
                .onFailure { throwable ->
                    _uiState.update { it.copy(isSaving = false, savedMessage = throwable.message ?: "Save failed") }
                }
        }
    }

    fun onNewPlateNumberChanged(value: String) {
        _uiState.update { it.copy(newPlateNumber = value, vehicleError = null) }
    }

    fun onCertificatePicked(uri: String) {
        _uiState.update { it.copy(newCertificateUri = uri, vehicleError = null) }
    }

    fun addVehicle() {
        val state = _uiState.value
        val certUri = state.newCertificateUri ?: return
        viewModelScope.launch {
            profileRepository.addVehicle(state.newPlateNumber, certUri)
                .onSuccess {
                    _uiState.update { it.copy(newPlateNumber = "", newCertificateUri = null) }
                    refreshVehicles()
                }
                .onFailure { throwable ->
                    _uiState.update { it.copy(vehicleError = throwable.message ?: "Could not add vehicle") }
                }
        }
    }

    fun removeVehicle(vehicleId: String) {
        viewModelScope.launch {
            profileRepository.removeVehicle(vehicleId)
            refreshVehicles()
        }
    }

    private suspend fun refreshVehicles() {
        _uiState.update { it.copy(vehicles = profileRepository.getVehicles()) }
    }
}
