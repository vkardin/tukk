package io.github.vkardin.tukk

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp

@Composable
internal fun SettingsPage(
    settings: GestureSettings,
    onChange: (GestureSettings) -> Unit,
    onLanguage: () -> Unit,
    developer: Boolean,
    recording: Boolean,
    onRecord: () -> Unit,
) {
    val context = LocalContext.current
    Column(
        Modifier.widthIn(max = 600.dp).fillMaxWidth().verticalScroll(rememberScrollState()).padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp),
    ) {
        Text(stringResource(R.string.settings), style = MaterialTheme.typography.headlineLarge)
        Text(stringResource(R.string.saved_automatically), color = MaterialTheme.colorScheme.onSurfaceVariant)
        SectionTitle(R.string.appearance)
        SettingsCard {
            for ((index, label) in listOf(R.string.dark_theme, R.string.light_theme, R.string.system_theme).withIndex()) {
                ChoiceRow(label, settings.theme == index) { onChange(settings.copy(theme = index)) }
            }
            if (Build.VERSION.SDK_INT >= 31) {
                HorizontalDivider()
                ToggleRow(R.string.dynamic_color, settings.dynamicColor) { onChange(settings.copy(dynamicColor = it)) }
            }
        }
        SectionTitle(R.string.language)
        SettingsCard {
            TextButton(onClick = onLanguage, modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.language))
            }
        }
        SectionTitle(R.string.background_title)
        SettingsCard {
            Text(stringResource(R.string.background_advice))
            Spacer(Modifier.height(12.dp))
            Text(stringResource(R.string.screen_warning), style = MaterialTheme.typography.bodySmall)
            TextButton(onClick = {
                context.startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:${context.packageName}")))
            }) { Text(stringResource(R.string.system_settings)) }
        }
        SectionTitle(R.string.accessibility_title)
        SettingsCard { AccessibilityDisclosure() }
        SectionTitle(R.string.about)
        SettingsCard {
            Text(stringResource(R.string.version_label, BuildConfig.VERSION_NAME), style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(8.dp))
            Text(stringResource(R.string.about_body))
        }
        if (developer && BuildConfig.DEBUG) {
            SettingsCard {
                Text(stringResource(R.string.recording_help))
                TextButton(onClick = onRecord) {
                    Text(stringResource(if (recording) R.string.debug_stop else R.string.debug_record))
                }
            }
        }
    }
}

@Composable
internal fun SettingsCard(content: @Composable ColumnScope.() -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
    ) {
        Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(6.dp), content = content)
    }
}

@Composable
internal fun AccessibilityDisclosure() {
    val context = LocalContext.current
    var confirmation by remember { mutableStateOf(false) }
    Text(stringResource(R.string.accessibility_body), style = MaterialTheme.typography.bodyMedium)
    TextButton(onClick = { confirmation = true }) { Text(stringResource(R.string.open_accessibility)) }
    if (confirmation) {
        AlertDialog(
            onDismissRequest = { confirmation = false },
            title = { Text(stringResource(R.string.accessibility_title)) },
            text = { Text(stringResource(R.string.accessibility_body)) },
            confirmButton = {
                TextButton(onClick = {
                    confirmation = false
                    context.startActivity(Intent(android.provider.Settings.ACTION_ACCESSIBILITY_SETTINGS))
                }) { Text(stringResource(R.string.grant_access)) }
            },
            dismissButton = { TextButton(onClick = { confirmation = false }) { Text(stringResource(R.string.cancel)) } },
        )
    }
}

