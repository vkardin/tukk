package io.github.vkardin.tukk.service

import android.content.Context
import android.content.Intent
import dagger.hilt.android.qualifiers.ApplicationContext
import io.github.vkardin.tukk.core.data.TukkRepository
import javax.inject.Inject
import javax.inject.Singleton

/** Turns the gesture service on and off and remembers the choice for restarts. */
@Singleton
class GestureController @Inject constructor(
    @ApplicationContext private val context: Context,
    private val repository: TukkRepository,
    private val status: GestureStatus,
) {
    /** Starts the service; call it from a user action while the app is visible. */
    suspend fun enable(): Boolean {
        repository.setServiceEnabled(true)
        if (start()) return true
        repository.setServiceEnabled(false)
        return false
    }

    suspend fun disable() {
        repository.setServiceEnabled(false)
        context.stopService(Intent(context, GestureService::class.java))
    }

    /** Starts the service if the user left it on, for example after a reboot. */
    suspend fun restore() {
        if (repository.loadSettings().serviceEnabled) start()
    }

    private fun start(): Boolean {
        status.setFailure(false)
        return try {
            context.startForegroundService(Intent(context, GestureService::class.java))
            true
        } catch (_: IllegalStateException) {
            // Includes ForegroundServiceStartNotAllowedException on Android 12 and later.
            status.setFailure(true)
            false
        } catch (_: SecurityException) {
            status.setFailure(true)
            false
        }
    }
}
