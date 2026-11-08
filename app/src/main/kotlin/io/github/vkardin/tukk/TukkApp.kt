package io.github.vkardin.tukk

import androidx.activity.compose.BackHandler
import androidx.appcompat.app.AppCompatDelegate
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.unit.sp
import androidx.core.os.LocaleListCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.vkardin.tukk.core.model.TriggerKind
import io.github.vkardin.tukk.core.ui.R
import io.github.vkardin.tukk.core.ui.TukkTheme
import io.github.vkardin.tukk.feature.editor.TriggerEditorScreen
import io.github.vkardin.tukk.feature.home.HomeScreen
import io.github.vkardin.tukk.feature.settings.SettingsScreen

enum class Page { HOME, SETTINGS }

private val LANGUAGES = listOf("uk" to "Українська", "en" to "English", "ru" to "Русский")

@Composable
internal fun TukkApp(
    viewModel: MainViewModel,
    requestedPage: Page?,
    onPageConsumed: () -> Unit,
    versionName: String,
    onToggleService: (Boolean) -> Unit,
    notificationDialog: Boolean,
    onDismissNotification: () -> Unit,
    onRequestNotification: () -> Unit,
) {
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    var page by rememberSaveable { mutableStateOf(Page.HOME) }
    var editing by rememberSaveable { mutableStateOf<TriggerKind?>(null) }
    var showLanguage by remember { mutableStateOf(false) }
    var developer by rememberSaveable { mutableStateOf(false) }

    LaunchedEffect(requestedPage) {
        requestedPage?.let {
            page = it
            editing = null
            onPageConsumed()
        }
    }
    BackHandler(editing != null || page != Page.HOME) {
        if (editing != null) editing = null else page = Page.HOME
    }

    TukkTheme(settings) {
        Scaffold(
            containerColor = MaterialTheme.colorScheme.background,
            bottomBar = {
                if (editing == null) {
                    NavigationBar(containerColor = MaterialTheme.colorScheme.surfaceContainer) {
                        NavigationBarItem(
                            selected = page == Page.HOME, onClick = { page = Page.HOME },
                            icon = { Text("⌂", fontSize = 25.sp, modifier = Modifier.clearAndSetSemantics {}) },
                            label = { Text(stringResource(R.string.home)) },
                        )
                        NavigationBarItem(
                            selected = page == Page.SETTINGS, onClick = { page = Page.SETTINGS },
                            icon = { Text("⚙", fontSize = 24.sp, modifier = Modifier.clearAndSetSemantics {}) },
                            label = { Text(stringResource(R.string.settings)) },
                        )
                    }
                }
            },
        ) { padding ->
            Box(Modifier.padding(padding).fillMaxSize(), contentAlignment = Alignment.TopCenter) {
                val kind = editing
                when {
                    kind != null -> TriggerEditorScreen(kind, onBack = { editing = null })
                    page == Page.SETTINGS -> SettingsScreen(
                        versionName = versionName,
                        developer = developer,
                        onLanguage = { showLanguage = true },
                    )
                    else -> HomeScreen(
                        onToggleService = onToggleService,
                        onEdit = { editing = it },
                        onEmberLongPress = { if (BuildConfig.DEBUG) developer = !developer },
                    )
                }
            }
        }

        if (showLanguage) {
            AlertDialog(
                onDismissRequest = { showLanguage = false },
                title = { Text(stringResource(R.string.language)) },
                text = {
                    Column {
                        for ((tag, label) in LANGUAGES) {
                            TextButton(onClick = {
                                showLanguage = false
                                AppCompatDelegate.setApplicationLocales(LocaleListCompat.forLanguageTags(tag))
                            }) { Text(label) }
                        }
                    }
                },
                confirmButton = {},
            )
        }
        // Waits for the stored settings, so returning users never see it flash.
        if (!settings.onboarded && settings.localeInitialized && !showLanguage) {
            AlertDialog(
                onDismissRequest = {},
                title = { Text(stringResource(R.string.onboarding_title)) },
                text = { Text(stringResource(R.string.onboarding_body)) },
                confirmButton = {
                    TextButton(onClick = viewModel::finishOnboarding) { Text(stringResource(R.string.get_started)) }
                },
                dismissButton = { TextButton(onClick = { showLanguage = true }) { Text(stringResource(R.string.language)) } },
            )
        }
        if (notificationDialog) {
            AlertDialog(
                onDismissRequest = onDismissNotification,
                title = { Text(stringResource(R.string.permission_title)) },
                text = { Text(stringResource(R.string.permission_body)) },
                confirmButton = { TextButton(onClick = onRequestNotification) { Text(stringResource(R.string.enable)) } },
                dismissButton = { TextButton(onClick = onDismissNotification) { Text(stringResource(R.string.cancel)) } },
            )
        }
    }
}
