package io.github.vkardin.tukk

import android.accessibilityservice.AccessibilityService
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import android.media.AudioManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.provider.MediaStore
import android.view.KeyEvent

internal class ActionExecutor(
    private val context: Context,
    private val onSosChanged: (Boolean) -> Unit,
) : AutoCloseable {
    private val cameras = context.getSystemService(CameraManager::class.java)
    private val handler = Handler(Looper.getMainLooper())
    private var cameraId: String? = null
    private var torchOn: Boolean? = null
    private var registered = false
    private var sosIndex = 0
    var sosActive = false
        private set(value) {
            field = value
            onSosChanged(value)
        }

    private val callback = object : CameraManager.TorchCallback() {
        override fun onTorchModeChanged(id: String, enabled: Boolean) {
            if (id == cameraId) torchOn = enabled
        }
        override fun onTorchModeUnavailable(id: String) {
            if (id == cameraId) {
                torchOn = null
                stopSos()
            }
        }
    }

    init {
        try {
            cameraId = cameras.cameraIdList.firstOrNull {
                cameras.getCameraCharacteristics(it).get(CameraCharacteristics.FLASH_INFO_AVAILABLE) == true
            }
            cameras.registerTorchCallback(callback, handler)
            registered = true
        } catch (_: android.hardware.camera2.CameraAccessException) {
            cameraId = null
        }
    }

    private val sosDurations = longArrayOf(
        160, 160, 160, 160, 160, 480,
        480, 160, 480, 160, 480, 480,
        160, 160, 160, 160, 160, 1120,
    )
    private val sosStep = object : Runnable {
        override fun run() {
            if (!sosActive) return
            if (!setTorch(sosIndex % 2 == 0, 1)) {
                stopSos()
                return
            }
            val delay = sosDurations[sosIndex]
            sosIndex = (sosIndex + 1) % sosDurations.size
            handler.postDelayed(this, delay)
        }
    }

    fun execute(config: TriggerConfig): Boolean {
        return try {
            when (config.action) {
                ActionKind.TORCH -> {
                    if (sosActive) {
                        stopSos()
                        true
                    } else {
                        torchOn?.let { setTorch(!it, config.brightness) } ?: false
                    }
                }
                ActionKind.SOS -> {
                    if (sosActive) stopSos() else {
                        if (cameraId == null || torchOn == null) return false
                        sosIndex = 0
                        sosActive = true
                        handler.post(sosStep)
                    }
                    true
                }
                ActionKind.SILENT -> sound(AudioManager.RINGER_MODE_SILENT)
                ActionKind.VIBRATE -> sound(AudioManager.RINGER_MODE_VIBRATE)
                ActionKind.SOUND -> sound(AudioManager.RINGER_MODE_NORMAL)
                ActionKind.PLAY_PAUSE -> media(KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE)
                ActionKind.NEXT -> media(KeyEvent.KEYCODE_MEDIA_NEXT)
                ActionKind.PREVIOUS -> media(KeyEvent.KEYCODE_MEDIA_PREVIOUS)
                ActionKind.CAMERA -> {
                    val locked = context.getSystemService(android.app.KeyguardManager::class.java).isKeyguardLocked
                    val cameraAction = if (locked) MediaStore.INTENT_ACTION_STILL_IMAGE_CAMERA_SECURE
                        else MediaStore.INTENT_ACTION_STILL_IMAGE_CAMERA
                    offerLaunch(Intent(cameraAction), config)
                }
                ActionKind.LAUNCH_APP, ActionKind.RECORDER -> {
                    val intent = context.packageManager.getLaunchIntentForPackage(config.packageName)
                    intent != null && offerLaunch(intent, config)
                }
                ActionKind.SCREENSHOT -> Build.VERSION.SDK_INT >= 28 &&
                    GestureAccessibility.instance?.performGlobalAction(AccessibilityService.GLOBAL_ACTION_TAKE_SCREENSHOT) == true
            }
        } catch (_: SecurityException) {
            false
        } catch (_: IllegalArgumentException) {
            false
        }
    }

    private fun setTorch(enabled: Boolean, brightness: Int): Boolean {
        val id = cameraId ?: return false
        return try {
            val max = maximumTorchStrength(context)
            if (enabled && Build.VERSION.SDK_INT >= 33 && max > 1) {
                cameras.turnOnTorchWithStrengthLevel(id, brightness.coerceIn(1, max))
            } else {
                cameras.setTorchMode(id, enabled)
            }
            torchOn = enabled
            true
        } catch (_: android.hardware.camera2.CameraAccessException) {
            false
        } catch (_: IllegalArgumentException) {
            false
        } catch (_: SecurityException) {
            false
        }
    }

    private fun sound(mode: Int): Boolean {
        if (!context.getSystemService(NotificationManager::class.java).isNotificationPolicyAccessGranted) return false
        val audio = context.getSystemService(AudioManager::class.java)
        if (audio.isVolumeFixed) return false
        audio.ringerMode = mode
        return true
    }

    private fun media(key: Int): Boolean {
        val audio = context.getSystemService(AudioManager::class.java)
        val time = SystemClock.uptimeMillis()
        audio.dispatchMediaKeyEvent(KeyEvent(time, time, KeyEvent.ACTION_DOWN, key, 0))
        audio.dispatchMediaKeyEvent(KeyEvent(time, time, KeyEvent.ACTION_UP, key, 0))
        return true
    }

    private fun offerLaunch(intent: Intent, config: TriggerConfig): Boolean {
        if (intent.resolveActivity(context.packageManager) == null) return false
        val manager = context.getSystemService(NotificationManager::class.java)
        if (!manager.areNotificationsEnabled()) return false
        if (Build.VERSION.SDK_INT >= 33 &&
            context.checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS) != android.content.pm.PackageManager.PERMISSION_GRANTED
        ) return false
        manager.createNotificationChannel(NotificationChannel(
            "actions", context.getString(R.string.action_requests), NotificationManager.IMPORTANCE_HIGH,
        ))
        val launch = PendingIntent.getActivity(
            context, config.kind.ordinal + 10, intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        manager.notify(20 + config.kind.ordinal, Notification.Builder(context, "actions")
            .setSmallIcon(R.drawable.ic_status)
            .setContentTitle(context.getString(config.action.title))
            .setContentText(context.getString(R.string.tap_to_open))
            .setContentIntent(launch)
            .setAutoCancel(true)
            .build())
        return true
    }

    fun stopSos() {
        val wasActive = sosActive
        sosActive = false
        handler.removeCallbacks(sosStep)
        if (wasActive) setTorch(false, 1)
    }

    override fun close() {
        stopSos()
        if (registered) cameras.unregisterTorchCallback(callback)
    }
}

internal fun maximumTorchStrength(context: Context): Int {
    if (Build.VERSION.SDK_INT < 33) return 1
    return try {
        val cameras = context.getSystemService(CameraManager::class.java)
        val id = cameras.cameraIdList.firstOrNull {
            cameras.getCameraCharacteristics(it).get(CameraCharacteristics.FLASH_INFO_AVAILABLE) == true
        } ?: return 1
        cameras.getCameraCharacteristics(id).get(CameraCharacteristics.FLASH_INFO_STRENGTH_MAXIMUM_LEVEL) ?: 1
    } catch (_: android.hardware.camera2.CameraAccessException) {
        1
    }
}

