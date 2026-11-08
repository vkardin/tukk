package io.github.vkardin.tukk

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.compose.runtime.*
import androidx.core.os.LocaleListCompat

class MainActivity : AppCompatActivity() {
    private var notificationDialog by mutableStateOf(false)
    private val notificationPermission = registerForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted ->
        if (granted) enableGestures() else {
            android.widget.Toast.makeText(this, R.string.notification_denied, android.widget.Toast.LENGTH_LONG).show()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (!gesturePreferences().getBoolean("locale_initialized", false)) {
            if (AppCompatDelegate.getApplicationLocales().isEmpty &&
                resources.configuration.locales[0].language != "en"
            ) AppCompatDelegate.setApplicationLocales(LocaleListCompat.forLanguageTags("uk"))
            gesturePreferences().edit().putBoolean("locale_initialized", true).apply()
        }
        enableEdgeToEdge()
        setContent {
            TukkApp(
                onToggle = { enabled ->
                    when {
                        !enabled -> {
                            gesturePreferences().edit().putBoolean(GestureService.ENABLED, false).apply()
                            stopService(Intent(this, GestureService::class.java))
                        }
                        needsNotificationPermission() -> notificationDialog = true
                        else -> enableGestures()
                    }
                },
                notificationDialog = notificationDialog,
                onDismissNotification = { notificationDialog = false },
                onRequestNotification = {
                    notificationDialog = false
                    if (Build.VERSION.SDK_INT >= 33) {
                        notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
                    }
                },
            )
        }
    }

    override fun onStop() {
        MotionRecorder.stop()
        super.onStop()
    }

    private fun needsNotificationPermission(): Boolean =
        Build.VERSION.SDK_INT >= 33 &&
            checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED

    private fun enableGestures() {
        gesturePreferences().edit().putBoolean(GestureService.ENABLED, true).apply()
        if (!GestureService.start(this)) {
            gesturePreferences().edit().putBoolean(GestureService.ENABLED, false).apply()
        }
    }
}

