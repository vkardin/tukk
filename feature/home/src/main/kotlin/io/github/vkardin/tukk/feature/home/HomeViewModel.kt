package io.github.vkardin.tukk.feature.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import io.github.vkardin.tukk.actions.TorchController
import io.github.vkardin.tukk.core.data.TukkRepository
import io.github.vkardin.tukk.core.model.TriggerConfig
import io.github.vkardin.tukk.core.model.TriggerKind
import io.github.vkardin.tukk.service.GestureCapabilities
import io.github.vkardin.tukk.service.GestureStatus
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class HomeViewModel @Inject constructor(
    private val repository: TukkRepository,
    private val capabilities: GestureCapabilities,
    private val torch: TorchController,
    status: GestureStatus,
) : ViewModel() {
    val triggers: StateFlow<List<TriggerConfig>> = repository.triggers
    val running: StateFlow<Boolean> = status.running
    val pulse: StateFlow<Long> = status.pulse
    val failure: StateFlow<Boolean> = status.failure
    val sos: StateFlow<Boolean> = torch.sosActive

    fun isAvailable(kind: TriggerKind) = capabilities.isAvailable(kind)

    fun isReady(config: TriggerConfig) = capabilities.isReady(config)

    fun setEnabled(config: TriggerConfig, enabled: Boolean) {
        viewModelScope.launch { repository.save(config.copy(enabled = enabled)) }
    }

    fun add(config: TriggerConfig) {
        viewModelScope.launch { repository.save(config.copy(added = true, enabled = false)) }
    }

    fun stopSos() = torch.stopSos()
}
