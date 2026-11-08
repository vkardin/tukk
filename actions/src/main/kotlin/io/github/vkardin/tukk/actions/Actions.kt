package io.github.vkardin.tukk.actions

import android.accessibilityservice.AccessibilityService
import android.app.KeyguardManager
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.media.AudioManager
import android.os.Build
import android.os.SystemClock
import android.provider.MediaStore
import android.view.KeyEvent
import dagger.hilt.android.qualifiers.ApplicationContext
import io.github.vkardin.tukk.core.model.ActionKind
import io.github.vkardin.tukk.core.model.TriggerConfig
import io.github.vkardin.tukk.core.ui.R
import io.github.vkardin.tukk.core.ui.title
import javax.inject.Inject

class TorchAction @Inject constructor(private val torch: TorchController) : Action {
    override fun isSupported() = torch.available
    override fun execute(config: TriggerConfig) = torch.toggle(config.torchLevel)
}

class SosAction @Inject constructor(private val torch: TorchController) : Action {
    override fun isSupported() = torch.available
    override fun execute(config: TriggerConfig) = torch.toggleSos()
}

/**
 * Switches the ringer to [mode]. Silent and vibrate act as toggles:
 * repeating the gesture returns to normal sound.
 */
class RingerAction(
    private val context: Context,
    private val mode: Int,
    private val toggle: Boolean,
) : Action {
    private val notifications get() = context.getSystemService(NotificationManager::class.java)
    private val audio get() = context.getSystemService(AudioManager::class.java)

    override fun isSupported() = !audio.isVolumeFixed

    override fun isReady(config: TriggerConfig) = isSupported() && notifications.isNotificationPolicyAccessGranted

    override fun execute(config: TriggerConfig): Boolean {
        if (!isReady(config)) return false
        val target = if (toggle && audio.ringerMode == mode) AudioManager.RINGER_MODE_NORMAL else mode
        audio.ringerMode = target
        return true
    }
}

class MediaKeyAction(private val context: Context, private val key: Int) : Action {
    override fun execute(config: TriggerConfig): Boolean {
        val audio = context.getSystemService(AudioManager::class.java)
        val time = SystemClock.uptimeMillis()
        audio.dispatchMediaKeyEvent(KeyEvent(time, time, KeyEvent.ACTION_DOWN, key, 0))
        audio.dispatchMediaKeyEvent(KeyEvent(time, time, KeyEvent.ACTION_UP, key, 0))
        return true
    }
}

class ScreenshotAction @Inject constructor(private val bridge: AccessibilityBridge) : Action {
    override fun isSupported() = Build.VERSION.SDK_INT >= 28
    override fun isReady(config: TriggerConfig) = isSupported() && bridge.isConnected
    override fun execute(config: TriggerConfig) = Build.VERSION.SDK_INT >= 28 &&
        bridge.performGlobalAction(AccessibilityService.GLOBAL_ACTION_TAKE_SCREENSHOT)
}

/**
 * Opens an app or the camera. Android blocks activity starts from the background,
 * so the app opens directly only through the optional accessibility service;
 * otherwise a notification offers to open it.
 */
class LaunchAction(
    private val context: Context,
    private val bridge: AccessibilityBridge,
    private val camera: Boolean,
) : Action {
    private fun intent(config: TriggerConfig): Intent? {
        if (!camera) return context.packageManager.getLaunchIntentForPackage(config.packageName)
        val locked = context.getSystemService(KeyguardManager::class.java).isKeyguardLocked
        return Intent(
            if (locked) MediaStore.INTENT_ACTION_STILL_IMAGE_CAMERA_SECURE else MediaStore.INTENT_ACTION_STILL_IMAGE_CAMERA,
        )
    }

    override fun isReady(config: TriggerConfig) =
        camera || (config.packageName.isNotEmpty() && intent(config) != null)

    override fun execute(config: TriggerConfig): Boolean {
        val intent = intent(config) ?: return false
        if (intent.resolveActivity(context.packageManager) == null) return false
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        if (bridge.isConnected && bridge.startActivity(intent)) return true
        return notify(intent, config)
    }

    private fun notify(intent: Intent, config: TriggerConfig): Boolean {
        val manager = context.getSystemService(NotificationManager::class.java)
        if (!manager.areNotificationsEnabled()) return false
        if (Build.VERSION.SDK_INT >= 33 &&
            context.checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) return false
        manager.createNotificationChannel(
            NotificationChannel(CHANNEL, context.getString(R.string.action_requests), NotificationManager.IMPORTANCE_HIGH),
        )
        val launch = PendingIntent.getActivity(
            context, REQUEST_BASE + config.kind.ordinal, intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        manager.notify(
            NOTIFICATION_BASE + config.kind.ordinal,
            Notification.Builder(context, CHANNEL)
                .setSmallIcon(R.drawable.ic_status)
                .setContentTitle(context.getString(config.action.title))
                .setContentText(context.getString(R.string.tap_to_open))
                .setContentIntent(launch)
                .setAutoCancel(true)
                .setTimeoutAfter(NOTIFICATION_TIMEOUT_MS)
                .build(),
        )
        return true
    }

    private companion object {
        const val CHANNEL = "actions"
        const val REQUEST_BASE = 10
        const val NOTIFICATION_BASE = 20
        const val NOTIFICATION_TIMEOUT_MS = 60_000L
    }
}

@dagger.Module
@dagger.hilt.InstallIn(dagger.hilt.components.SingletonComponent::class)
object ActionsModule {
    @dagger.Provides @dagger.multibindings.IntoMap @ActionKey(ActionKind.TORCH)
    fun torch(action: TorchAction): Action = action

    @dagger.Provides @dagger.multibindings.IntoMap @ActionKey(ActionKind.SOS)
    fun sos(action: SosAction): Action = action

    @dagger.Provides @dagger.multibindings.IntoMap @ActionKey(ActionKind.SILENT)
    fun silent(@ApplicationContext context: Context): Action =
        RingerAction(context, AudioManager.RINGER_MODE_SILENT, toggle = true)

    @dagger.Provides @dagger.multibindings.IntoMap @ActionKey(ActionKind.VIBRATE)
    fun vibrate(@ApplicationContext context: Context): Action =
        RingerAction(context, AudioManager.RINGER_MODE_VIBRATE, toggle = true)

    @dagger.Provides @dagger.multibindings.IntoMap @ActionKey(ActionKind.SOUND)
    fun sound(@ApplicationContext context: Context): Action =
        RingerAction(context, AudioManager.RINGER_MODE_NORMAL, toggle = false)

    @dagger.Provides @dagger.multibindings.IntoMap @ActionKey(ActionKind.PLAY_PAUSE)
    fun playPause(@ApplicationContext context: Context): Action = MediaKeyAction(context, KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE)

    @dagger.Provides @dagger.multibindings.IntoMap @ActionKey(ActionKind.NEXT)
    fun next(@ApplicationContext context: Context): Action = MediaKeyAction(context, KeyEvent.KEYCODE_MEDIA_NEXT)

    @dagger.Provides @dagger.multibindings.IntoMap @ActionKey(ActionKind.PREVIOUS)
    fun previous(@ApplicationContext context: Context): Action = MediaKeyAction(context, KeyEvent.KEYCODE_MEDIA_PREVIOUS)

    @dagger.Provides @dagger.multibindings.IntoMap @ActionKey(ActionKind.LAUNCH_APP)
    fun launchApp(@ApplicationContext context: Context, bridge: AccessibilityBridge): Action =
        LaunchAction(context, bridge, camera = false)

    @dagger.Provides @dagger.multibindings.IntoMap @ActionKey(ActionKind.RECORDER)
    fun recorder(@ApplicationContext context: Context, bridge: AccessibilityBridge): Action =
        LaunchAction(context, bridge, camera = false)

    @dagger.Provides @dagger.multibindings.IntoMap @ActionKey(ActionKind.CAMERA)
    fun camera(@ApplicationContext context: Context, bridge: AccessibilityBridge): Action =
        LaunchAction(context, bridge, camera = true)

    @dagger.Provides @dagger.multibindings.IntoMap @ActionKey(ActionKind.SCREENSHOT)
    fun screenshot(action: ScreenshotAction): Action = action
}
