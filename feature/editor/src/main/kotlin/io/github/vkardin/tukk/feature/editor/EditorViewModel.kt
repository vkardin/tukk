package io.github.vkardin.tukk.feature.editor

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import io.github.vkardin.tukk.actions.TorchController
import io.github.vkardin.tukk.core.data.TukkRepository
import io.github.vkardin.tukk.core.model.ActionKind
import io.github.vkardin.tukk.core.model.TriggerConfig
import io.github.vkardin.tukk.core.model.TriggerKind
import io.github.vkardin.tukk.service.AccessibilityConnection
import io.github.vkardin.tukk.service.GestureCapabilities
import io.github.vkardin.tukk.service.GestureStatus
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class EditorViewModel @Inject constructor(
    private val repository: TukkRepository,
    private val capabilities: GestureCapabilities,
    private val torch: TorchController,
    private val status: GestureStatus,
    accessibility: AccessibilityConnection,
) : ViewModel() {
    /** Unsaved edits, shown immediately so sliders stay smooth while writes are coalesced. */
    private val drafts = MutableStateFlow<Map<TriggerKind, TriggerConfig>>(emptyMap())
    private val writes = mutableMapOf<TriggerKind, Job>()

    val triggers: StateFlow<List<TriggerConfig>> = combine(repository.triggers, drafts) { saved, edits ->
        saved.map { edits[it.kind] ?: it }
    }.stateIn(viewModelScope, SharingStarted.Eagerly, repository.triggers.value)

    val accessibilityConnected: StateFlow<Boolean> = accessibility.connected

    val torchLevels: Int get() = torch.maxLevel
    val defaultTorchLevel: Int get() = torch.defaultLevel

    fun isAvailable(kind: TriggerKind) = capabilities.isAvailable(kind)
    fun isSupported(action: ActionKind) = capabilities.isSupported(action)
    fun isReady(config: TriggerConfig) = capabilities.isReady(config)

    fun update(config: TriggerConfig) {
        drafts.update { it + (config.kind to config) }
        writes[config.kind]?.cancel()
        writes[config.kind] = viewModelScope.launch {
            delay(WRITE_DELAY_MS)
            repository.save(config)
            repository.triggers.first { saved -> saved.any { it == config } }
            drafts.update { if (it[config.kind] == config) it - config.kind else it }
        }
    }

    /** Applies [action]; the gesture stays on only if it can still run. */
    fun chooseAction(config: TriggerConfig, action: ActionKind) {
        val changed = config.copy(action = action)
        update(changed.copy(enabled = changed.enabled && isReady(changed)))
    }

    fun remove(config: TriggerConfig) = update(config.copy(added = false, enabled = false))

    fun setPreviewActive(active: Boolean) {
        status.previewActive = active
    }

    override fun onCleared() {
        status.previewActive = false
        // Unsaved edits are written right away when the editor closes.
        drafts.value.values.forEach { config -> repository.saveDetached(config) }
    }

    private companion object {
        const val WRITE_DELAY_MS = 250L
    }
}
