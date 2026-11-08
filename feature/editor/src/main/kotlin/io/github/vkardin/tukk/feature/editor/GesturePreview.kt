package io.github.vkardin.tukk.feature.editor

import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import io.github.vkardin.tukk.core.model.TriggerConfig
import io.github.vkardin.tukk.core.model.TriggerKind
import io.github.vkardin.tukk.core.ui.R
import io.github.vkardin.tukk.core.ui.hint
import io.github.vkardin.tukk.detection.MotionSample
import io.github.vkardin.tukk.detection.TapImpulseDetector
import io.github.vkardin.tukk.service.GestureRuntime

private const val CALIBRATION_TAPS = 5
private const val CALIBRATION_GAP_NS = 600_000_000L

/** Ratio of the median tap strength used as the detection threshold. */
private const val CALIBRATION_RATIO = 0.65f

/**
 * Live gesture test, or the five-tap calibration for back taps.
 * Actions are suspended while it is open.
 */
@Composable
internal fun GesturePreview(
    config: TriggerConfig,
    calibration: Boolean,
    onActiveChange: (Boolean) -> Unit,
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
    val activeCallback by rememberUpdatedState(onActiveChange)
    DisposableEffect(config.kind, calibration) {
        val sensors = context.getSystemService(SensorManager::class.java)
        val runtime = GestureRuntime(config)
        val impulses = TapImpulseDetector(TriggerConfig.TAP_THRESHOLD_RANGE.start)
        val amplitudes = mutableListOf<Float>()
        var lastCalibrationTap = 0L
        activeCallback(true)
        val listener = object : SensorEventListener {
            override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit
            override fun onSensorChanged(event: SensorEvent) {
                if (completed) return
                val sample = MotionSample(event.timestamp, event.values[0], event.values[1], event.values[2])
                if (event.sensor.type == Sensor.TYPE_GYROSCOPE) {
                    runtime.rotation(sample)
                    return
                }
                if (!calibration) {
                    if (runtime.accept(sample, false)) count++
                    return
                }
                val amplitude = impulses.accept(sample) ?: return
                if (sample.timestampNs - lastCalibrationTap < CALIBRATION_GAP_NS) return
                lastCalibrationTap = sample.timestampNs
                amplitudes += amplitude
                count = amplitudes.size
                if (count == CALIBRATION_TAPS) {
                    val median = amplitudes.sorted()[CALIBRATION_TAPS / 2]
                    calibrateCallback((median * CALIBRATION_RATIO).coerceIn(TriggerConfig.TAP_THRESHOLD_RANGE))
                    completed = true
                    sensors.unregisterListener(this)
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
            activeCallback(false)
        }
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(if (calibration) R.string.calibrate else R.string.test_gesture)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(18.dp)) {
                Text(stringResource(if (calibration) R.string.calibration_hint else R.string.test_hint))
                Text(stringResource(config.kind.hint))
                val live = Modifier.semantics { liveRegion = LiveRegionMode.Polite }
                when {
                    unavailable -> Text(stringResource(R.string.unsupported))
                    completed -> Text(
                        stringResource(R.string.calibration_saved), live, color = MaterialTheme.colorScheme.primary,
                    )
                    calibration -> {
                        LinearProgressIndicator(
                            progress = { count / CALIBRATION_TAPS.toFloat() }, modifier = Modifier.fillMaxWidth(),
                        )
                        Text(stringResource(R.string.calibration_progress, count), live)
                    }
                    else -> Text(
                        stringResource(R.string.detected_count, count), live,
                        style = MaterialTheme.typography.headlineMedium,
                    )
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.close)) } },
    )
}
