package io.github.vkardin.tukk.service

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorManager
import dagger.hilt.android.qualifiers.ApplicationContext
import io.github.vkardin.tukk.actions.ActionExecutor
import io.github.vkardin.tukk.core.model.ActionKind
import io.github.vkardin.tukk.core.model.TriggerConfig
import io.github.vkardin.tukk.core.model.TriggerKind
import javax.inject.Inject
import javax.inject.Singleton

/** What this phone supports and whether a trigger has everything it needs to run. */
@Singleton
class GestureCapabilities @Inject constructor(
    @ApplicationContext context: Context,
    private val actions: ActionExecutor,
    private val accessibility: AccessibilityConnection,
) {
    private val sensors = context.getSystemService(SensorManager::class.java)
    private val accelerometer = sensors.getDefaultSensor(Sensor.TYPE_ACCELEROMETER) != null
    private val gyroscope = sensors.getDefaultSensor(Sensor.TYPE_GYROSCOPE) != null

    fun isAvailable(kind: TriggerKind): Boolean = when (kind) {
        TriggerKind.VOLUME -> true
        TriggerKind.CHOP -> accelerometer && gyroscope
        else -> accelerometer
    }

    fun isSupported(action: ActionKind): Boolean = actions.isSupported(action)

    fun isReady(config: TriggerConfig): Boolean {
        if (!isAvailable(config.kind)) return false
        if (config.kind == TriggerKind.VOLUME && !accessibility.isConnected) return false
        return actions.isReady(config)
    }
}
