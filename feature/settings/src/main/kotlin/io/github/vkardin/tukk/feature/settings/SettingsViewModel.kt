package io.github.vkardin.tukk.feature.settings

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import io.github.vkardin.tukk.core.data.TukkRepository
import io.github.vkardin.tukk.core.model.AppSettings
import io.github.vkardin.tukk.service.AccessibilityConnection
import io.github.vkardin.tukk.service.MotionRecorder
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val repository: TukkRepository,
    accessibility: AccessibilityConnection,
) : ViewModel() {
    val settings: StateFlow<AppSettings> = repository.settings
    val accessibilityConnected: StateFlow<Boolean> = accessibility.connected
    val recording: StateFlow<Boolean> = MotionRecorder.active

    fun update(transform: (AppSettings) -> AppSettings) {
        viewModelScope.launch { repository.updateSettings(transform) }
    }

    fun toggleRecording(context: Context) = MotionRecorder.toggle(context)
}
