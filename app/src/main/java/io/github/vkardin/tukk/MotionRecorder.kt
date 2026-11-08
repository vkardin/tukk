package io.github.vkardin.tukk

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.Handler
import android.os.Looper
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.File
import java.io.IOException

internal object MotionRecorder : SensorEventListener {
    val enabled = BuildConfig.DEBUG
    private val recording = MutableStateFlow(false)
    val active = recording.asStateFlow()
    private val timer = Handler(Looper.getMainLooper())
    private val finish = Runnable { stop() }
    private var manager: SensorManager? = null
    private var output: File? = null
    private var rotationOutput: File? = null
    private val rows = StringBuilder()
    private val rotations = StringBuilder()

    fun toggle(context: Context) {
        if (!enabled) return
        if (recording.value) {
            stop()
            return
        }
        val sensors = context.getSystemService(SensorManager::class.java)
        val sensor = sensors.getDefaultSensor(Sensor.TYPE_ACCELEROMETER) ?: return
        if (!sensors.registerListener(this, sensor, 20_000)) return
        sensors.getDefaultSensor(Sensor.TYPE_GYROSCOPE)?.let {
            sensors.registerListener(this, it, 20_000)
        }

        manager = sensors
        output = File(context.filesDir, "motion.csv")
        rotationOutput = File(context.filesDir, "rotation.csv")
        rows.appendLine("timestampNs,x,y,z")
        rotations.appendLine("timestampNs,x,y,z")
        recording.value = true
        timer.postDelayed(finish, 15_000)
    }

    fun stop() {
        if (!recording.value) return
        timer.removeCallbacks(finish)
        manager?.unregisterListener(this)
        try {
            output?.writeText(rows.toString())
            rotationOutput?.writeText(rotations.toString())
        } catch (error: IOException) {
            android.util.Log.w("MotionRecorder", "Could not write recording", error)
        } finally {
            rows.clear()
            rotations.clear()
            manager = null
            output = null
            rotationOutput = null
            recording.value = false
        }
    }

    override fun onSensorChanged(event: SensorEvent) {
        if (!recording.value) return
        val destination = if (event.sensor.type == Sensor.TYPE_GYROSCOPE) rotations else rows
        destination.append(event.timestamp)
            .append(',').append(event.values[0])
            .append(',').append(event.values[1])
            .append(',').append(event.values[2]).appendLine()
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit
}
