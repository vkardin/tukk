package io.github.vkardin.tukk.actions

import android.content.Context
import android.hardware.camera2.CameraAccessException
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.PowerManager
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

/** Flashlight state, strength levels and the SOS pattern. */
@Singleton
class TorchController @Inject constructor(@ApplicationContext context: Context) {
    private val cameras = context.getSystemService(CameraManager::class.java)
    private val handler = Handler(Looper.getMainLooper())

    // Keeps the SOS pattern running while the screen is off; renewed on every step.
    private val sosWakeLock = context.getSystemService(PowerManager::class.java)
        .newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "tukk:sos")
        .apply { setReferenceCounted(false) }

    private val cameraId: String? = try {
        cameras.cameraIdList.firstOrNull {
            cameras.getCameraCharacteristics(it).get(CameraCharacteristics.FLASH_INFO_AVAILABLE) == true
        }
    } catch (_: CameraAccessException) {
        null
    }

    /** Null while another app holds the camera or before the first callback. */
    private var torchOn: Boolean? = null
    private var sosIndex = 0
    private val sosState = MutableStateFlow(false)
    val sosActive: StateFlow<Boolean> = sosState.asStateFlow()

    val available: Boolean get() = cameraId != null

    /** Number of strength levels; 1 when the torch has a single level. */
    val maxLevel: Int by lazy {
        if (Build.VERSION.SDK_INT >= 33) characteristic(CameraCharacteristics.FLASH_INFO_STRENGTH_MAXIMUM_LEVEL) else 1
    }

    val defaultLevel: Int by lazy {
        if (Build.VERSION.SDK_INT >= 33) characteristic(CameraCharacteristics.FLASH_INFO_STRENGTH_DEFAULT_LEVEL) else 1
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
        if (cameraId != null) cameras.registerTorchCallback(callback, handler)
    }

    private fun characteristic(key: CameraCharacteristics.Key<Int>): Int {
        val id = cameraId ?: return 1
        return try {
            cameras.getCameraCharacteristics(id).get(key) ?: 1
        } catch (_: CameraAccessException) {
            1
        }
    }

    fun toggle(level: Int): Boolean {
        if (sosActive.value) {
            stopSos()
            return true
        }
        val current = torchOn ?: return false
        return setTorch(!current, level)
    }

    fun toggleSos(): Boolean {
        if (sosActive.value) {
            stopSos()
            return true
        }
        if (cameraId == null || torchOn == null) return false
        sosIndex = 0
        sosState.value = true
        handler.post(sosStep)
        return true
    }

    fun stopSos() {
        val wasActive = sosState.value
        sosState.value = false
        handler.removeCallbacks(sosStep)
        if (wasActive) setTorch(false, 0)
        if (sosWakeLock.isHeld) sosWakeLock.release()
    }

    private val sosStep = object : Runnable {
        override fun run() {
            if (!sosState.value) return
            sosWakeLock.acquire(SOS_WAKE_LOCK_MS)
            if (!setTorch(sosIndex % 2 == 0, 0)) {
                stopSos()
                return
            }
            val delay = SOS_PATTERN_MS[sosIndex]
            sosIndex = (sosIndex + 1) % SOS_PATTERN_MS.size
            handler.postDelayed(this, delay)
        }
    }

    /** Turns the torch on at [level], or at the device default level when [level] is 0. */
    private fun setTorch(enabled: Boolean, level: Int): Boolean {
        val id = cameraId ?: return false
        return try {
            if (enabled && Build.VERSION.SDK_INT >= 33 && maxLevel > 1 && level > 0) {
                cameras.turnOnTorchWithStrengthLevel(id, level.coerceIn(1, maxLevel))
            } else {
                cameras.setTorchMode(id, enabled)
            }
            torchOn = enabled
            true
        } catch (_: CameraAccessException) {
            false
        } catch (_: IllegalArgumentException) {
            false
        } catch (_: SecurityException) {
            false
        }
    }

    private companion object {
        const val SOS_WAKE_LOCK_MS = 10_000L

        /** On and off durations for · · · — — — · · · followed by a pause. */
        val SOS_PATTERN_MS = longArrayOf(
            160, 160, 160, 160, 160, 480,
            480, 160, 480, 160, 480, 480,
            160, 160, 160, 160, 160, 1120,
        )
    }
}
