package io.github.vkardin.tukk.service

import io.github.vkardin.tukk.core.model.TriggerKind
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

/** Live state of the gesture service, shared with the UI, the tile and the accessibility service. */
@Singleton
class GestureStatus @Inject constructor() {
    private val runningState = MutableStateFlow(false)
    private val pulseState = MutableStateFlow(0L)
    private val failureState = MutableStateFlow(false)
    private val lastGestureState = MutableStateFlow<TriggerKind?>(null)
    private val volumeEvents = MutableSharedFlow<Unit>(
        extraBufferCapacity = 1, onBufferOverflow = BufferOverflow.DROP_OLDEST,
    )

    val running: StateFlow<Boolean> = runningState.asStateFlow()

    /** Elapsed realtime of the last gesture that ran its action. */
    val pulse: StateFlow<Long> = pulseState.asStateFlow()
    val failure: StateFlow<Boolean> = failureState.asStateFlow()
    val lastGesture: StateFlow<TriggerKind?> = lastGestureState.asStateFlow()
    internal val volumeSequences: SharedFlow<Unit> = volumeEvents.asSharedFlow()

    /** True while a gesture test is open; actions are suspended. */
    @Volatile
    var previewActive: Boolean = false

    internal fun setRunning(running: Boolean) {
        runningState.value = running
    }

    fun setFailure(failed: Boolean) {
        failureState.value = failed
    }

    internal fun fired(kind: TriggerKind, atMs: Long) {
        failureState.value = false
        pulseState.value = atMs
        lastGestureState.value = kind
    }

    internal fun volumeSequence() {
        volumeEvents.tryEmit(Unit)
    }
}
