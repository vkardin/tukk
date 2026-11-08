package io.github.vkardin.tukk

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import io.github.vkardin.tukk.core.data.TukkRepository
import io.github.vkardin.tukk.core.model.AppSettings
import io.github.vkardin.tukk.service.GestureController
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class MainViewModel @Inject constructor(
    private val repository: TukkRepository,
    private val controller: GestureController,
) : ViewModel() {
    val settings: StateFlow<AppSettings> = repository.settings

    suspend fun loadSettings(): AppSettings = repository.loadSettings()

    fun setServiceEnabled(enabled: Boolean) {
        viewModelScope.launch { if (enabled) controller.enable() else controller.disable() }
    }

    fun finishOnboarding() {
        viewModelScope.launch { repository.updateSettings { it.copy(onboarded = true) } }
    }

    fun markLocaleInitialized() {
        viewModelScope.launch { repository.updateSettings { it.copy(localeInitialized = true) } }
    }
}
