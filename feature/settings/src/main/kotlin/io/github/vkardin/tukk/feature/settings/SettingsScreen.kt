package io.github.vkardin.tukk.feature.settings

import android.os.Build
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.vkardin.tukk.core.model.ThemeMode
import io.github.vkardin.tukk.core.ui.AccessibilityDisclosure
import io.github.vkardin.tukk.core.ui.ChoiceRow
import io.github.vkardin.tukk.core.ui.R
import io.github.vkardin.tukk.core.ui.SectionTitle
import io.github.vkardin.tukk.core.ui.SettingsCard
import io.github.vkardin.tukk.core.ui.ToggleRow
import io.github.vkardin.tukk.service.MotionRecorder

@Composable
fun SettingsScreen(
    versionName: String,
    developer: Boolean,
    onLanguage: () -> Unit,
    viewModel: SettingsViewModel = hiltViewModel(),
) {
    val context = LocalContext.current
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    val accessibility by viewModel.accessibilityConnected.collectAsStateWithLifecycle()
    val recording by viewModel.recording.collectAsStateWithLifecycle()
    Column(
        Modifier.widthIn(max = 600.dp).fillMaxWidth().verticalScroll(rememberScrollState()).padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp),
    ) {
        Text(stringResource(R.string.settings), style = MaterialTheme.typography.headlineLarge)
        Text(stringResource(R.string.saved_automatically), color = MaterialTheme.colorScheme.onSurfaceVariant)

        SectionTitle(R.string.appearance)
        SettingsCard {
            val titles = listOf(R.string.dark_theme, R.string.light_theme, R.string.system_theme)
            for (mode in ThemeMode.entries) {
                ChoiceRow(titles[mode.ordinal], settings.theme == mode) { viewModel.update { it.copy(theme = mode) } }
            }
            if (Build.VERSION.SDK_INT >= 31) {
                HorizontalDivider()
                ToggleRow(R.string.dynamic_color, settings.dynamicColor) { enabled ->
                    viewModel.update { it.copy(dynamicColor = enabled) }
                }
            }
        }

        SectionTitle(R.string.language)
        SettingsCard {
            TextButton(onClick = onLanguage, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.language)) }
        }

        SectionTitle(R.string.background_title)
        BackgroundCard()

        SectionTitle(R.string.accessibility_title)
        SettingsCard { AccessibilityDisclosure(accessibility) }

        SectionTitle(R.string.about)
        SettingsCard {
            Text(stringResource(R.string.version_label, versionName), style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(8.dp))
            Text(stringResource(R.string.about_body))
        }

        if (developer && MotionRecorder.enabled) {
            SettingsCard {
                Text(stringResource(R.string.recording_help))
                TextButton(onClick = { viewModel.toggleRecording(context) }) {
                    Text(stringResource(if (recording) R.string.debug_stop else R.string.debug_record))
                }
            }
        }
    }
}
