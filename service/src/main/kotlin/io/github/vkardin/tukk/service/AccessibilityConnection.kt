package io.github.vkardin.tukk.service

import android.content.ActivityNotFoundException
import android.content.Intent
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import io.github.vkardin.tukk.actions.AccessibilityBridge
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

/** Holds the connected accessibility service, if the user enabled it. */
@Singleton
class AccessibilityConnection @Inject constructor() : AccessibilityBridge {
    @Volatile
    private var service: GestureAccessibility? = null
    private val connectedState = MutableStateFlow(false)
    val connected: StateFlow<Boolean> = connectedState.asStateFlow()

    override val isConnected: Boolean get() = service != null

    internal fun attach(service: GestureAccessibility) {
        this.service = service
        connectedState.value = true
    }

    internal fun detach(service: GestureAccessibility) {
        if (this.service === service) {
            this.service = null
            connectedState.value = false
        }
    }

    override fun performGlobalAction(action: Int): Boolean = service?.performGlobalAction(action) == true

    override fun startActivity(intent: Intent): Boolean {
        val current = service ?: return false
        return try {
            current.startActivity(intent)
            true
        } catch (_: ActivityNotFoundException) {
            false
        } catch (_: SecurityException) {
            false
        }
    }
}

@Module
@InstallIn(SingletonComponent::class)
internal abstract class AccessibilityModule {
    @Binds
    abstract fun bridge(connection: AccessibilityConnection): AccessibilityBridge
}
