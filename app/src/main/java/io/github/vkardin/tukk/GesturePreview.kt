package io.github.vkardin.tukk

import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import io.github.vkardin.tukk.detection.MotionSample
import io.github.vkardin.tukk.detection.TapImpulseDetector

@Composable
internal fun GesturePreview(
    config: TriggerConfig,
    calibration: Boolean,
    onCalibrated: (Float) -> Unit,
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    var count by remember { mutableIntStateOf(0) }
    var completed by remember { mutableStateOf(false) }
    var unavailable by remember { mutableStateOf(false) }
    val calibrateCallback by rememberUpdatedState(onCalibrated)
    val dismissCallback by rememberUpdatedState(onDismiss)
    DisposableEffect(config.kind, calibration) {
        val sensors = context.getSystemService(SensorManager::class.java)
        val runtime = GestureRuntime(config)
        val impulses = TapImpulseDetector(1.5f)
        val amplitudes = mutableListOf<Float>()
        var lastCalibrationTap = 0L
        GestureService.previewActive = true
        val listener = object : SensorEventListener {
            override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit
            override fun onSensorChanged(event: SensorEvent) {
                if (completed) return
                val sample = MotionSample(event.timestamp, event.values[0], event.values[1], event.values[2])
                if (event.sensor.type == Sensor.TYPE_GYROSCOPE) {
                    runtime.rotation(sample)
                    return
                }
                if (calibration) {
                    val amplitude = impulses.accept(sample) ?: return
                    if (sample.timestampNs - lastCalibrationTap < 600_000_000L) return
                    lastCalibrationTap = sample.timestampNs
                    amplitudes += amplitude
                    count = amplitudes.size
                    if (count == 5) {
                        calibrateCallback((amplitudes.sorted()[2] * 0.65f).coerceIn(1.5f, 18f))
                        completed = true
                        sensors.unregisterListener(this)
                    }
                } else if (runtime.accept(sample, false)) {
                    count++
                }
            }
        }
        val accelerometer = sensors.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
        unavailable = accelerometer == null || !sensors.registerListener(listener, accelerometer, 10_000)
        if (config.kind == TriggerKind.CHOP) {
            sensors.getDefaultSensor(Sensor.TYPE_GYROSCOPE)?.let { sensors.registerListener(listener, it, 20_000) }
        }
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_STOP) dismissCallback()
        }
        lifecycle.addObserver(observer)
        onDispose {
            sensors.unregisterListener(listener)
            lifecycle.removeObserver(observer)
            GestureService.previewActive = false
        }
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(if (calibration) R.string.calibrate else R.string.test_gesture)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(18.dp)) {
                Text(stringResource(if (calibration) R.string.calibration_hint else R.string.test_hint))
                Text(stringResource(config.kind.hint))
                when {
                    unavailable -> Text(stringResource(R.string.unsupported))
                    completed -> Text(stringResource(R.string.calibration_saved), color = MaterialTheme.colorScheme.primary)
                    calibration -> {
                        LinearProgressIndicator(progress = { count / 5f }, modifier = Modifier.fillMaxWidth())
                        Text(stringResource(R.string.calibration_progress, count))
                    }
                    else -> Text(stringResource(R.string.detected_count, count), style = MaterialTheme.typography.headlineMedium)
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.close)) } },
    )
}

