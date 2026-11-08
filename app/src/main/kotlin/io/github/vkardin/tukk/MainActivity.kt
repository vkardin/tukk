package io.github.vkardin.tukk

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.widget.Toast
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.os.LocaleListCompat
import androidx.lifecycle.lifecycleScope
import dagger.hilt.android.AndroidEntryPoint
import io.github.vkardin.tukk.core.ui.R
import io.github.vkardin.tukk.service.GestureService
import io.github.vkardin.tukk.service.MotionRecorder
import kotlinx.coroutines.launch

@AndroidEntryPoint
class MainActivity : AppCompatActivity() {
    private val viewModel: MainViewModel by viewModels()
    private var notificationDialog by mutableStateOf(false)
    private var requestedPage by mutableStateOf<Page?>(null)

    private val notificationPermission = registerForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted ->
        if (granted) {
            viewModel.setServiceEnabled(true)
        } else {
            Toast.makeText(this, R.string.notification_denied, Toast.LENGTH_LONG).show()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        initializeLocale()
        if (savedInstanceState == null) requestedPage = pageFrom(intent)
        setContent {
            TukkApp(
                viewModel = viewModel,
                requestedPage = requestedPage,
                onPageConsumed = { requestedPage = null },
                versionName = BuildConfig.VERSION_NAME,
                onToggleService = ::toggleService,
                notificationDialog = notificationDialog,
                onDismissNotification = { notificationDialog = false },
                onRequestNotification = {
                    notificationDialog = false
                    if (Build.VERSION.SDK_INT >= 33) notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
                },
            )
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        pageFrom(intent)?.let { requestedPage = it }
    }

    override fun onStop() {
        MotionRecorder.stop()
        super.onStop()
    }

    private fun pageFrom(intent: Intent?): Page? =
        if (intent?.getStringExtra(GestureService.EXTRA_PAGE) == GestureService.PAGE_SETTINGS) Page.SETTINGS else null

    /**
     * Ukrainian is the default language. On first launch, unless the system language
     * is English, the app starts in Ukrainian and offers a language switch in onboarding.
     */
    private fun initializeLocale() {
        lifecycleScope.launch {
            if (viewModel.loadSettings().localeInitialized) return@launch
            viewModel.markLocaleInitialized()
            if (AppCompatDelegate.getApplicationLocales().isEmpty &&
                resources.configuration.locales[0].language != "en"
            ) {
                AppCompatDelegate.setApplicationLocales(LocaleListCompat.forLanguageTags("uk"))
            }
        }
    }

    private fun toggleService(enabled: Boolean) {
        when {
            !enabled -> viewModel.setServiceEnabled(false)
            needsNotificationPermission() -> notificationDialog = true
            else -> viewModel.setServiceEnabled(true)
        }
    }

    private fun needsNotificationPermission(): Boolean =
        Build.VERSION.SDK_INT >= 33 &&
            checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
}
