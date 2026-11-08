package io.github.vkardin.tukk

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.IBinder
import android.os.SystemClock
import androidx.core.content.ContextCompat
import io.github.vkardin.tukk.detection.MotionSample
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

internal fun Context.gesturePreferences() = getSharedPreferences("gesture", Context.MODE_PRIVATE)

class GestureService : Service(), SensorEventListener {
    private val sensors by lazy { getSystemService(SensorManager::class.java) }
    private lateinit var actions: ActionExecutor
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
            if (running.value) reload()
        }
    }
    private val handler = android.os.Handler(android.os.Looper.getMainLooper())
    private val reloadTask = Runnable { reload() }
    private val preferencesListener = android.content.SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
        if (key?.startsWith("trigger.") == true) {
            // Coalesce bursts of changes, such as dragging a slider.
            handler.removeCallbacks(reloadTask)
            handler.postDelayed(reloadTask, RELOAD_DELAY_MS)
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        actions = ActionExecutor(this) { active ->
            mutableSos.value = active
            if (running.value) getSystemService(NotificationManager::class.java).notify(NOTIFICATION_ID, notification())
        }
        instance = this
        reload()
        gesturePreferences().registerOnSharedPreferenceChangeListener(preferencesListener)
        ContextCompat.registerReceiver(this, screenReceiver, android.content.IntentFilter().apply {
            addAction(Intent.ACTION_SCREEN_ON)
            addAction(Intent.ACTION_SCREEN_OFF)
        }, ContextCompat.RECEIVER_NOT_EXPORTED)
    }

    private fun reload() {
        runtimes = TriggerKind.entries.map { TriggerConfig.load(this, it) }
            .filter { it.added && it.enabled && it.kind.available(this) }.map(::GestureRuntime)
        if (running.value) registerSensors()
    }

    /** Triggers that can fire in the current screen state. */
    private fun eligible(interactive: Boolean) = runtimes.filter {
        when (it.config.screenMode) {
            1 -> interactive
            2 -> !interactive
            else -> true
        }
    }

    private fun registerSensors(): Boolean {
        sensors.unregisterListener(this)
        proximityBlocked = false
        val interactive = getSystemService(android.os.PowerManager::class.java).isInteractive
        screenOn = interactive
        val listening = eligible(interactive)
        // While the screen is on the processor is awake anyway, so regular sensors suffice.
        // With the screen off, wake-up sensors deliver batched events without a wake lock.
        fun sensor(type: Int) = if (interactive) {
            sensors.getDefaultSensor(type) ?: sensors.getDefaultSensor(type, true)
        } else {
            sensors.getDefaultSensor(type, true) ?: sensors.getDefaultSensor(type)
        }
        val latency = if (interactive) 0 else SCREEN_OFF_LATENCY_US
        if (listening.any { it.config.kind != TriggerKind.VOLUME }) {
            val accelerometer = sensor(Sensor.TYPE_ACCELEROMETER) ?: return false
            val period = if (interactive && listening.any { it.config.kind == TriggerKind.BACK_TAP }) 10_000 else 20_000
            if (!sensors.registerListener(this, accelerometer, period, latency)) return false
        }
        if (listening.any { it.config.kind == TriggerKind.CHOP }) {
            sensor(Sensor.TYPE_GYROSCOPE)?.let { sensors.registerListener(this, it, 20_000, latency) }
        }
        if (listening.any { it.config.pocketGuard && it.config.kind != TriggerKind.VOLUME } ||
            runtimes.any { it.config.pocketGuard && it.config.kind == TriggerKind.VOLUME }
        ) {
            sensors.getDefaultSensor(Sensor.TYPE_PROXIMITY)?.let {
                sensors.registerListener(this, it, SensorManager.SENSOR_DELAY_NORMAL)
            }
        }
        return true
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action != PAUSE) startForeground(NOTIFICATION_ID, notification())
        if (intent?.action == PAUSE || !gesturePreferences().getBoolean(ENABLED, false)) {
            gesturePreferences().edit().putBoolean(ENABLED, false).apply()
            stopSelf()
            return START_NOT_STICKY
        }
        if (intent?.action == STOP_SOS) stopSos()
        if (running.value) return START_STICKY
        if (!registerSensors()) {
            mutableFailure.value = true
            gesturePreferences().edit().putBoolean(ENABLED, false).apply()
            stopSelf()
            return START_NOT_STICKY
        }
        mutableRunning.value = true
        updateTile()
        return START_STICKY
    }

    private fun blocked(config: TriggerConfig): Boolean {
        val now = SystemClock.elapsedRealtime()
        if (now - lastEnvironmentCheck >= 250) {
            screenOn = getSystemService(android.os.PowerManager::class.java).isInteractive
            charging = getSystemService(android.os.BatteryManager::class.java).isCharging
            val mode = getSystemService(android.media.AudioManager::class.java).mode
            inCall = mode == android.media.AudioManager.MODE_IN_CALL ||
                mode == android.media.AudioManager.MODE_IN_COMMUNICATION
            lastEnvironmentCheck = now
        }
        return previewActive || (config.pocketGuard && proximityBlocked) || (config.pauseCall && inCall) ||
            (config.screenMode == 1 && !screenOn) ||
            (config.screenMode == 2 && screenOn) || (config.pauseCharging && charging)
    }

    override fun onSensorChanged(event: SensorEvent) {
        if (event.sensor.type == Sensor.TYPE_PROXIMITY) {
            proximityBlocked = event.values[0] < minOf(event.sensor.maximumRange, 5f)
            return
        }
        val sample = MotionSample(event.timestamp, event.values[0], event.values[1], event.values[2])
        if (event.sensor.type == Sensor.TYPE_GYROSCOPE) {
            runtimes.forEach { it.rotation(sample) }
            return
        }
        val matched = runtimes.filter { it.accept(sample, blocked(it.config)) }
        // Prefer the more specific gesture if one movement satisfies multiple detectors.
        val selected = matched.firstOrNull { it.config.kind != TriggerKind.SHAKE } ?: matched.firstOrNull()
        selected?.let { dispatch(it.config) }
    }

    private fun dispatch(config: TriggerConfig) {
        val now = SystemClock.elapsedRealtime()
        if (now - lastDispatchMs < 400) return
        lastDispatchMs = now
        if (!actions.execute(config)) {
            mutableFailure.value = true
            android.widget.Toast.makeText(this, R.string.action_failed, android.widget.Toast.LENGTH_SHORT).show()
            return
        }
        mutableFailure.value = false
        mutablePulse.value = now
        mutableLastGesture.value = config.kind
        mutableSos.value = actions.sosActive
        if (config.vibration) confirm(config.hapticStrength)
    }

    private fun confirm(strength: Int) {
        val vibrator = if (android.os.Build.VERSION.SDK_INT >= 31) {
            getSystemService(android.os.VibratorManager::class.java).defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            getSystemService(android.os.Vibrator::class.java)
        }
        if (!vibrator.hasVibrator()) return
        val duration = longArrayOf(25, 40, 60)[strength.coerceIn(0, 2)]
        val amplitude = if (vibrator.hasAmplitudeControl()) intArrayOf(70, 150, 255)[strength.coerceIn(0, 2)]
            else android.os.VibrationEffect.DEFAULT_AMPLITUDE
        vibrator.vibrate(android.os.VibrationEffect.createOneShot(duration, amplitude))
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit

    override fun onDestroy() {
        unregisterReceiver(screenReceiver)
        handler.removeCallbacks(reloadTask)
        gesturePreferences().unregisterOnSharedPreferenceChangeListener(preferencesListener)
        sensors.unregisterListener(this)
        actions.close()
        mutableRunning.value = false
        mutableSos.value = false
        if (instance === this) instance = null
        updateTile()
        super.onDestroy()
    }

    private fun updateTile() {
        android.service.quicksettings.TileService.requestListeningState(
            this, android.content.ComponentName(this, GestureTile::class.java),
        )
    }

    private fun notification(): Notification {
        val manager = getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(
            NotificationChannel(CHANNEL, getString(R.string.gestures), NotificationManager.IMPORTANCE_LOW)
                .apply { setShowBadge(false) },
        )
        val open = PendingIntent.getActivity(
            this, 0, Intent(this, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE,
        )
        val settings = PendingIntent.getActivity(
            this, 2, Intent(this, MainActivity::class.java).putExtra(MainActivity.EXTRA_PAGE, MainActivity.PAGE_SETTINGS),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val pause = PendingIntent.getService(
            this, 0, Intent(this, GestureService::class.java).setAction(PAUSE), PendingIntent.FLAG_IMMUTABLE,
        )
        val builder = Notification.Builder(this, CHANNEL)
            .setSmallIcon(R.drawable.ic_status)
            .setContentTitle(getString(R.string.app_name))
            .setContentText(getString(R.string.active))
            .setContentIntent(open)
            .setOngoing(true)
            .setShowWhen(false)
            .setCategory(Notification.CATEGORY_SERVICE)
        if (sos.value) {
            val stopSos = PendingIntent.getService(
                this, 1, Intent(this, GestureService::class.java).setAction(STOP_SOS), PendingIntent.FLAG_IMMUTABLE,
            )
            builder.addAction(Notification.Action.Builder(null, getString(R.string.stop_sos), stopSos).build())
        }
        builder.addAction(Notification.Action.Builder(null, getString(R.string.pause), pause).build())
        builder.addAction(Notification.Action.Builder(null, getString(R.string.settings), settings).build())
        if (android.os.Build.VERSION.SDK_INT >= 31) {
            builder.setForegroundServiceBehavior(Notification.FOREGROUND_SERVICE_IMMEDIATE)
        }
        return builder.build()
    }

    companion object {
        internal const val ENABLED = "enabled"
        private const val PAUSE = "io.github.vkardin.tukk.PAUSE"
        private const val STOP_SOS = "io.github.vkardin.tukk.STOP_SOS"
        private const val CHANNEL = "gestures"
        private const val NOTIFICATION_ID = 1
        private const val RELOAD_DELAY_MS = 300L
        /** Maximum batching delay with the screen off: fewer wake-ups at a small reaction cost. */
        private const val SCREEN_OFF_LATENCY_US = 200_000
        private var instance: GestureService? = null
        internal var previewActive = false
        private val mutableRunning = MutableStateFlow(false)
        private val mutablePulse = MutableStateFlow(0L)
        private val mutableFailure = MutableStateFlow(false)
        private val mutableSos = MutableStateFlow(false)
        private val mutableLastGesture = MutableStateFlow<TriggerKind?>(null)
        internal val running = mutableRunning.asStateFlow()
        internal val pulse = mutablePulse.asStateFlow()
        internal val failure = mutableFailure.asStateFlow()
        internal val sos = mutableSos.asStateFlow()
        internal val lastGesture = mutableLastGesture.asStateFlow()

        internal fun volumeGesture() {
            val service = instance ?: return
            val runtime = service.runtimes.firstOrNull { it.config.kind == TriggerKind.VOLUME } ?: return
            if (!service.blocked(runtime.config) && runtime.allow(SystemClock.elapsedRealtimeNanos())) {
                service.dispatch(runtime.config)
            }
        }

        internal fun stopSos() {
            instance?.actions?.stopSos()
            mutableSos.value = false
        }

        internal fun start(context: Context): Boolean {
            mutableFailure.value = false
            return try {
                ContextCompat.startForegroundService(context, Intent(context, GestureService::class.java))
                true
            } catch (_: IllegalStateException) {
                mutableFailure.value = true
                false
            } catch (_: SecurityException) {
                mutableFailure.value = true
                false
            }
        }
    }
}

class RestartReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED &&
            intent.action != Intent.ACTION_MY_PACKAGE_REPLACED
        ) return
        if (context.gesturePreferences().getBoolean(GestureService.ENABLED, false)) GestureService.start(context)
    }
}

