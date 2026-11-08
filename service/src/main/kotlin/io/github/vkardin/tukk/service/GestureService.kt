package io.github.vkardin.tukk.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.media.AudioManager
import android.os.BatteryManager
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import android.os.SystemClock
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.widget.Toast
import dagger.hilt.android.AndroidEntryPoint
import io.github.vkardin.tukk.actions.ActionExecutor
import io.github.vkardin.tukk.actions.TorchController
import io.github.vkardin.tukk.core.data.TukkRepository
import io.github.vkardin.tukk.core.model.TriggerConfig
import io.github.vkardin.tukk.core.model.TriggerKind
import io.github.vkardin.tukk.core.ui.R
import io.github.vkardin.tukk.detection.MotionSample
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Foreground service that listens to motion sensors and runs the assigned actions.
 * It registers only the sensors that the enabled triggers need in the current
 * screen state, and batches wake-up sensor events while the screen is off.
 */
@AndroidEntryPoint
class GestureService : Service(), SensorEventListener {
    @Inject lateinit var repository: TukkRepository
    @Inject lateinit var actions: ActionExecutor
    @Inject lateinit var torch: TorchController
    @Inject lateinit var status: GestureStatus
    @Inject lateinit var capabilities: GestureCapabilities

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var startJob: Job? = null
    private val sensors by lazy { getSystemService(SensorManager::class.java) }
    private val power by lazy { getSystemService(PowerManager::class.java) }
    private var runtimes = emptyList<GestureRuntime>()
    private var proximityBlocked = false
    private var lastEnvironmentCheck = 0L
    private var screenOn = true
    private var charging = false
    private var inCall = false
    private var lastDispatchMs = 0L

    private val screenReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            lastEnvironmentCheck = 0L
            if (status.running.value) registerSensors()
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_SCREEN_ON)
            addAction(Intent.ACTION_SCREEN_OFF)
        }
        if (Build.VERSION.SDK_INT >= 33) {
            registerReceiver(screenReceiver, filter, RECEIVER_NOT_EXPORTED)
        } else {
            registerReceiver(screenReceiver, filter)
        }
        scope.launch {
            torch.sosActive.drop(1).collect {
                if (status.running.value) {
                    getSystemService(NotificationManager::class.java).notify(NOTIFICATION_ID, notification())
                }
            }
        }
        scope.launch { status.volumeSequences.collect { volumeGesture() } }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_PAUSE -> {
                scope.launch {
                    repository.setServiceEnabled(false)
                    stopSelf()
                }
                return START_NOT_STICKY
            }
            ACTION_STOP_SOS -> {
                torch.stopSos()
                if (!status.running.value && startJob == null) stopSelf()
                return START_NOT_STICKY
            }
        }
        startForeground(NOTIFICATION_ID, notification())
        if (startJob == null) startJob = scope.launch { start() }
        return START_STICKY
    }

    @OptIn(FlowPreview::class)
    private suspend fun start() {
        if (!repository.loadSettings().serviceEnabled) {
            stopSelf()
            return
        }
        var applied = repository.loadTriggers()
        apply(applied)
        if (!registerSensors()) {
            status.setFailure(true)
            repository.setServiceEnabled(false)
            stopSelf()
            return
        }
        status.setRunning(true)
        GestureTile.requestUpdate(this)
        // Coalesce bursts of changes, such as dragging a slider in the editor.
        repository.triggerFlow.filter { it != applied }.debounce(RELOAD_DELAY_MS).collect { configs ->
            applied = configs
            apply(configs)
            registerSensors()
        }
    }

    private fun apply(configs: List<TriggerConfig>) {
        runtimes = configs.filter { it.added && it.enabled && capabilities.isAvailable(it.kind) }.map(::GestureRuntime)
    }

    private fun registerSensors(): Boolean {
        sensors.unregisterListener(this)
        proximityBlocked = false
        val interactive = power.isInteractive
        screenOn = interactive
        val listening = runtimes.filter { it.config.screenMode.allows(interactive) }

        // With the screen on the processor is awake anyway, so regular sensors are enough.
        // With the screen off, wake-up sensors deliver batched events without a wake lock.
        fun sensor(type: Int): Sensor? = if (interactive) {
            sensors.getDefaultSensor(type) ?: sensors.getDefaultSensor(type, true)
        } else {
            sensors.getDefaultSensor(type, true) ?: sensors.getDefaultSensor(type)
        }
        val latency = if (interactive) 0 else SCREEN_OFF_LATENCY_US
        val motion = listening.filter { it.config.kind.usesMotion }
        if (motion.isNotEmpty()) {
            val accelerometer = sensor(Sensor.TYPE_ACCELEROMETER) ?: return false
            val fast = interactive && motion.any { it.config.kind == TriggerKind.BACK_TAP }
            if (!sensors.registerListener(this, accelerometer, if (fast) 10_000 else 20_000, latency)) return false
        }
        if (motion.any { it.config.kind == TriggerKind.CHOP }) {
            sensor(Sensor.TYPE_GYROSCOPE)?.let { sensors.registerListener(this, it, 20_000, latency) }
        }
        if (listening.any { it.config.pocketGuard }) {
            sensors.getDefaultSensor(Sensor.TYPE_PROXIMITY)?.let {
                sensors.registerListener(this, it, SensorManager.SENSOR_DELAY_NORMAL)
            }
        }
        return true
    }

    private fun blocked(config: TriggerConfig): Boolean {
        val now = SystemClock.elapsedRealtime()
        if (now - lastEnvironmentCheck >= ENVIRONMENT_CHECK_MS) {
            screenOn = power.isInteractive
            charging = getSystemService(BatteryManager::class.java).isCharging
            val mode = getSystemService(AudioManager::class.java).mode
            inCall = mode == AudioManager.MODE_IN_CALL || mode == AudioManager.MODE_IN_COMMUNICATION
            lastEnvironmentCheck = now
        }
        return status.previewActive ||
            (config.pocketGuard && proximityBlocked) ||
            (config.pauseCall && inCall) ||
            (config.pauseCharging && charging) ||
            !config.screenMode.allows(screenOn)
    }

    override fun onSensorChanged(event: SensorEvent) {
        if (event.sensor.type == Sensor.TYPE_PROXIMITY) {
            proximityBlocked = event.values[0] < minOf(event.sensor.maximumRange, PROXIMITY_NEAR_CM)
            return
        }
        val sample = MotionSample(event.timestamp, event.values[0], event.values[1], event.values[2])
        if (event.sensor.type == Sensor.TYPE_GYROSCOPE) {
            runtimes.forEach { it.rotation(sample) }
            return
        }
        val matched = runtimes.filter { it.accept(sample, blocked(it.config)) }
        // Prefer the more specific gesture if one movement satisfies several detectors.
        val selected = matched.firstOrNull { it.config.kind != TriggerKind.SHAKE } ?: matched.firstOrNull()
        selected?.let { dispatch(it.config) }
    }

    private fun volumeGesture() {
        val runtime = runtimes.firstOrNull { it.config.kind == TriggerKind.VOLUME } ?: return
        if (!blocked(runtime.config) && runtime.allow(SystemClock.elapsedRealtimeNanos())) dispatch(runtime.config)
    }

    private fun dispatch(config: TriggerConfig) {
        val now = SystemClock.elapsedRealtime()
        if (now - lastDispatchMs < GLOBAL_DEBOUNCE_MS) return
        lastDispatchMs = now
        if (!actions.execute(config)) {
            status.setFailure(true)
            Toast.makeText(this, R.string.action_failed, Toast.LENGTH_SHORT).show()
            return
        }
        status.fired(config.kind, now)
        if (config.vibration) confirm(config.hapticStrength)
    }

    private fun confirm(strength: Int) {
        val vibrator = if (Build.VERSION.SDK_INT >= 31) {
            getSystemService(VibratorManager::class.java).defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            getSystemService(Vibrator::class.java)
        }
        if (!vibrator.hasVibrator()) return
        val level = strength.coerceIn(0, 2)
        val amplitude = if (vibrator.hasAmplitudeControl()) HAPTIC_AMPLITUDE[level] else VibrationEffect.DEFAULT_AMPLITUDE
        vibrator.vibrate(VibrationEffect.createOneShot(HAPTIC_DURATION_MS[level], amplitude))
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit

    override fun onDestroy() {
        unregisterReceiver(screenReceiver)
        sensors.unregisterListener(this)
        scope.cancel()
        torch.stopSos()
        status.setRunning(false)
        GestureTile.requestUpdate(this)
        super.onDestroy()
    }

    private fun notification(): Notification {
        val manager = getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(
            NotificationChannel(CHANNEL, getString(R.string.gestures), NotificationManager.IMPORTANCE_LOW)
                .apply { setShowBadge(false) },
        )
        val launch = packageManager.getLaunchIntentForPackage(packageName)
        val open = PendingIntent.getActivity(this, 0, launch, PendingIntent.FLAG_IMMUTABLE)
        val settings = PendingIntent.getActivity(
            this, 2, Intent(launch).putExtra(EXTRA_PAGE, PAGE_SETTINGS),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val pause = PendingIntent.getService(
            this, 0, Intent(this, GestureService::class.java).setAction(ACTION_PAUSE), PendingIntent.FLAG_IMMUTABLE,
        )
        val builder = Notification.Builder(this, CHANNEL)
            .setSmallIcon(R.drawable.ic_status)
            .setContentTitle(getString(R.string.app_name))
            .setContentText(getString(R.string.active))
            .setContentIntent(open)
            .setOngoing(true)
            .setShowWhen(false)
            .setCategory(Notification.CATEGORY_SERVICE)
        if (torch.sosActive.value) {
            val stopSos = PendingIntent.getService(
                this, 1, Intent(this, GestureService::class.java).setAction(ACTION_STOP_SOS),
                PendingIntent.FLAG_IMMUTABLE,
            )
            builder.addAction(Notification.Action.Builder(null, getString(R.string.stop_sos), stopSos).build())
        }
        builder.addAction(Notification.Action.Builder(null, getString(R.string.pause), pause).build())
        builder.addAction(Notification.Action.Builder(null, getString(R.string.settings), settings).build())
        if (Build.VERSION.SDK_INT >= 31) builder.setForegroundServiceBehavior(Notification.FOREGROUND_SERVICE_IMMEDIATE)
        return builder.build()
    }

    companion object {
        /** Extra on the launch intent that opens a specific page. */
        const val EXTRA_PAGE = "io.github.vkardin.tukk.PAGE"
        const val PAGE_SETTINGS = "settings"

        private const val ACTION_PAUSE = "io.github.vkardin.tukk.PAUSE"
        private const val ACTION_STOP_SOS = "io.github.vkardin.tukk.STOP_SOS"
        private const val CHANNEL = "gestures"
        private const val NOTIFICATION_ID = 1
        private const val RELOAD_DELAY_MS = 300L
        private const val ENVIRONMENT_CHECK_MS = 250L
        private const val GLOBAL_DEBOUNCE_MS = 400L
        private const val PROXIMITY_NEAR_CM = 5f

        /** Maximum batching delay with the screen off: fewer wake-ups at a small reaction cost. */
        private const val SCREEN_OFF_LATENCY_US = 200_000
        private val HAPTIC_DURATION_MS = longArrayOf(25, 40, 60)
        private val HAPTIC_AMPLITUDE = intArrayOf(70, 150, 255)
    }
}
