package io.github.vkardin.tukk.actions

import android.content.Intent
import dagger.MapKey
import io.github.vkardin.tukk.core.model.ActionKind
import io.github.vkardin.tukk.core.model.TriggerConfig
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Something a gesture can do. New actions are added as implementations
 * bound into the map with [ActionKey], without changes to the gesture service.
 */
interface Action {
    /** The phone has the hardware and OS version this action needs. */
    fun isSupported(): Boolean = true

    /** Permissions and choices needed to run [config] are in place. */
    fun isReady(config: TriggerConfig): Boolean = isSupported()

    /** Runs the action and reports whether it succeeded. Called on the main thread. */
    fun execute(config: TriggerConfig): Boolean
}

@MapKey
annotation class ActionKey(val value: ActionKind)

/** Access to the optional accessibility service, implemented by the service module. */
interface AccessibilityBridge {
    val isConnected: Boolean
    fun performGlobalAction(action: Int): Boolean

    /** Starts an activity from the accessibility service, which is allowed in the background. */
    fun startActivity(intent: Intent): Boolean
}

@Singleton
class ActionExecutor @Inject constructor(
    private val actions: Map<ActionKind, @JvmSuppressWildcards Action>,
) {
    fun isSupported(kind: ActionKind): Boolean = actions[kind]?.isSupported() == true

    fun isReady(config: TriggerConfig): Boolean = actions[config.action]?.isReady(config) == true

    fun execute(config: TriggerConfig): Boolean = try {
        actions[config.action]?.execute(config) == true
    } catch (_: SecurityException) {
        false
    } catch (_: IllegalArgumentException) {
        false
    } catch (_: IllegalStateException) {
        false
    }
}
