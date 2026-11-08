package io.github.vkardin.tukk.feature.editor

import android.app.NotificationManager
import android.content.Intent
import android.content.pm.PackageManager
import android.provider.Settings
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.vkardin.tukk.core.model.ActionKind
import io.github.vkardin.tukk.core.model.ScreenMode
import io.github.vkardin.tukk.core.model.TriggerConfig
import io.github.vkardin.tukk.core.model.TriggerKind
import io.github.vkardin.tukk.core.model.VolumeSequence
import io.github.vkardin.tukk.core.ui.AccessibilityDisclosure
import io.github.vkardin.tukk.core.ui.ChoiceRow
import io.github.vkardin.tukk.core.ui.GestureBadge
import io.github.vkardin.tukk.core.ui.R
import io.github.vkardin.tukk.core.ui.SectionTitle
import io.github.vkardin.tukk.core.ui.SettingsCard
import io.github.vkardin.tukk.core.ui.ToggleRow
import io.github.vkardin.tukk.core.ui.hint
import io.github.vkardin.tukk.core.ui.rememberResumeCount
import io.github.vkardin.tukk.core.ui.startSettings
import io.github.vkardin.tukk.core.ui.title
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private val SENSITIVITY_PRESETS = listOf(R.string.low to 29f, R.string.medium to 23f, R.string.high to 18f)

@Composable
fun TriggerEditorScreen(
    kind: TriggerKind,
    onBack: () -> Unit,
    viewModel: EditorViewModel = hiltViewModel(),
) {
    val triggers by viewModel.triggers.collectAsStateWithLifecycle()
    val config = triggers.firstOrNull { it.kind == kind } ?: return
    TriggerEditor(config, viewModel, onBack)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TriggerEditor(config: TriggerConfig, viewModel: EditorViewModel, onBack: () -> Unit) {
    val context = LocalContext.current
    val onChange = viewModel::update
    var showActions by remember { mutableStateOf(false) }
    var showApps by remember { mutableStateOf(false) }
    var preview by remember { mutableStateOf(false) }
    var calibration by remember { mutableStateOf(false) }
    var confirmRemove by remember { mutableStateOf(false) }
    val resumes = rememberResumeCount()
    val accessibility by viewModel.accessibilityConnected.collectAsStateWithLifecycle()
    val soundGranted = remember(resumes) {
        context.getSystemService(NotificationManager::class.java).isNotificationPolicyAccessGranted
    }
    val needsAccessibility = config.kind == TriggerKind.VOLUME || config.action.needsAccessibility
    val missingHardware = !viewModel.isAvailable(config.kind) || !viewModel.isSupported(config.action)
    val ready = remember(config, resumes, accessibility) { viewModel.isReady(config) }

    Column(
        Modifier.widthIn(max = 600.dp).fillMaxWidth().verticalScroll(rememberScrollState()).padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp),
    ) {
        TextButton(onClick = onBack) { Text("‹ " + stringResource(R.string.back)) }
        Row(verticalAlignment = Alignment.CenterVertically) {
            GestureBadge(config.kind)
            Spacer(Modifier.width(16.dp))
            Text(stringResource(config.kind.title), style = MaterialTheme.typography.headlineMedium)
        }
        Text(stringResource(config.kind.hint), color = MaterialTheme.colorScheme.onSurfaceVariant)
        SettingsCard {
            ToggleRow(R.string.enabled, config.enabled, enabled = ready || config.enabled) { enabled ->
                onChange(config.copy(enabled = enabled))
            }
            when {
                missingHardware -> Text(stringResource(R.string.unsupported), color = MaterialTheme.colorScheme.error)
                !ready -> Text(stringResource(R.string.not_ready), style = MaterialTheme.typography.bodySmall)
            }
        }

        SectionTitle(R.string.action)
        SettingsCard {
            ListItem(
                headlineContent = { Text(stringResource(config.action.title), style = MaterialTheme.typography.titleMedium) },
                trailingContent = { Text("›") },
                modifier = Modifier.clickable { showActions = true },
                colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
            )
            if (config.action.needsPackage) {
                val label = remember(config.packageName) { appLabel(context.packageManager, config.packageName) }
                TextButton(onClick = { showApps = true }) { Text(label ?: stringResource(R.string.choose_app)) }
            }
            if (config.action.needsPackage || config.action == ActionKind.CAMERA) {
                Text(stringResource(R.string.launch_hint), style = MaterialTheme.typography.bodySmall)
            }
            if (config.action == ActionKind.SILENT || config.action == ActionKind.VIBRATE) {
                Text(stringResource(R.string.sound_toggle_hint), style = MaterialTheme.typography.bodySmall)
            }
            val levels = viewModel.torchLevels
            if (config.action == ActionKind.TORCH && levels > 1) {
                val label = stringResource(R.string.brightness)
                Text(label)
                val level = if (config.torchLevel > 0) config.torchLevel else viewModel.defaultTorchLevel
                Slider(
                    level.coerceIn(1, levels).toFloat(),
                    onValueChange = { onChange(config.copy(torchLevel = it.toInt())) },
                    valueRange = 1f..levels.toFloat(),
                    steps = (levels - 2).coerceAtLeast(0),
                    modifier = Modifier.semantics { contentDescription = label },
                )
            }
        }
        if (needsAccessibility) {
            SettingsCard { AccessibilityDisclosure(accessibility) }
        }
        if (config.action.changesRingerMode && !soundGranted) {
            SettingsCard {
                Text(stringResource(R.string.dnd_body))
                TextButton(onClick = { context.startSettings(Intent(Settings.ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS)) }) {
                    Text(stringResource(R.string.grant_access))
                }
            }
        }

        if (config.kind == TriggerKind.SHAKE || config.kind == TriggerKind.CHOP) {
            SectionTitle(R.string.sensitivity)
            SettingsCard {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    for ((title, threshold) in SENSITIVITY_PRESETS) {
                        FilterChip(
                            selected = kotlin.math.abs(config.threshold - threshold) < 0.5f,
                            onClick = { onChange(config.copy(threshold = threshold)) },
                            label = { Text(stringResource(title)) },
                        )
                    }
                }
                val label = stringResource(R.string.sensitivity)
                // Higher sensitivity means a lower threshold, so the slider is mirrored.
                val range = TriggerConfig.THRESHOLD_RANGE
                Slider(
                    range.start + range.endInclusive - config.threshold,
                    { onChange(config.copy(threshold = range.start + range.endInclusive - it)) },
                    valueRange = range,
                    modifier = Modifier.semantics { contentDescription = label },
                )
            }
        }
        if (config.kind == TriggerKind.BACK_TAP) {
            SettingsCard {
                SectionTitle(R.string.tap_count)
                ChoiceRow(R.string.double_tap, config.taps == 2) { onChange(config.copy(taps = 2)) }
                ChoiceRow(R.string.triple_tap, config.taps == 3) { onChange(config.copy(taps = 3)) }
                Button(onClick = { calibration = true }) { Text(stringResource(R.string.calibrate)) }
            }
        }
        if (config.kind == TriggerKind.VOLUME) {
            SettingsCard {
                val titles = listOf(R.string.volume_up, R.string.volume_down, R.string.volume_combo)
                for (sequence in VolumeSequence.entries) {
                    ChoiceRow(titles[sequence.ordinal], config.volumeSequence == sequence) {
                        onChange(config.copy(volumeSequence = sequence))
                    }
                }
            }
        } else {
            OutlinedButton(onClick = { preview = true }, modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.test_gesture))
            }
        }

        SectionTitle(R.string.conditions)
        SettingsCard {
            val screenTitles = listOf(R.string.always, R.string.screen_on, R.string.screen_off)
            for (mode in ScreenMode.entries) {
                ChoiceRow(screenTitles[mode.ordinal], config.screenMode == mode) { onChange(config.copy(screenMode = mode)) }
            }
            Text(stringResource(R.string.screen_warning), style = MaterialTheme.typography.bodySmall)
            HorizontalDivider()
            ToggleRow(R.string.pocket_guard, config.pocketGuard) { onChange(config.copy(pocketGuard = it)) }
            ToggleRow(R.string.pause_call, config.pauseCall) { onChange(config.copy(pauseCall = it)) }
            ToggleRow(R.string.pause_charging, config.pauseCharging) { onChange(config.copy(pauseCharging = it)) }
            ToggleRow(R.string.haptic, config.vibration) { onChange(config.copy(vibration = it)) }
            if (config.vibration) {
                Text(stringResource(R.string.haptic_strength), style = MaterialTheme.typography.bodySmall)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(R.string.low, R.string.medium, R.string.high).forEachIndexed { index, title ->
                        FilterChip(
                            selected = config.hapticStrength == index,
                            onClick = { onChange(config.copy(hapticStrength = index)) },
                            label = { Text(stringResource(title)) },
                        )
                    }
                }
            }
            HorizontalDivider()
            Text(stringResource(R.string.cooldown, config.cooldownMs))
            val cooldown = TriggerConfig.COOLDOWN_RANGE
            Slider(
                config.cooldownMs.toFloat(), { onChange(config.copy(cooldownMs = (it / 100).toInt() * 100)) },
                valueRange = cooldown.first.toFloat()..cooldown.last.toFloat(),
                steps = (cooldown.last - cooldown.first) / 100 - 1,
            )
        }
        TextButton(onClick = { confirmRemove = true }) {
            Text(stringResource(R.string.remove), color = MaterialTheme.colorScheme.error)
        }
    }

    if (showActions) {
        ModalBottomSheet(onDismissRequest = { showActions = false }) {
            LazyColumn(contentPadding = PaddingValues(24.dp)) {
                item { Text(stringResource(R.string.action), style = MaterialTheme.typography.titleLarge) }
                items(ActionKind.entries) { action ->
                    val supported = viewModel.isSupported(action)
                    ListItem(
                        headlineContent = { Text(stringResource(action.title)) },
                        supportingContent = { if (!supported) Text(stringResource(R.string.unsupported)) },
                        modifier = Modifier.clickable(enabled = supported) {
                            viewModel.chooseAction(config, action)
                            showActions = false
                        },
                    )
                }
            }
        }
    }
    if (showApps) {
        AppPicker(
            onDismiss = { showApps = false },
            onChoose = { onChange(config.copy(packageName = it)); showApps = false },
        )
    }
    if (preview || calibration) {
        GesturePreview(
            config, calibration,
            onActiveChange = viewModel::setPreviewActive,
            onCalibrated = { onChange(config.copy(tapThreshold = it)) },
            onDismiss = { preview = false; calibration = false },
        )
    }
    if (confirmRemove) {
        AlertDialog(
            onDismissRequest = { confirmRemove = false },
            title = { Text(stringResource(R.string.remove)) },
            text = { Text(stringResource(R.string.remove_body)) },
            confirmButton = {
                TextButton(onClick = {
                    confirmRemove = false
                    viewModel.remove(config)
                    onBack()
                }) { Text(stringResource(R.string.remove)) }
            },
            dismissButton = { TextButton(onClick = { confirmRemove = false }) { Text(stringResource(R.string.cancel)) } },
        )
    }
}

private fun appLabel(packageManager: PackageManager, packageName: String): String? = try {
    packageManager.getApplicationLabel(packageManager.getApplicationInfo(packageName, 0)).toString()
} catch (_: PackageManager.NameNotFoundException) {
    null
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AppPicker(onDismiss: () -> Unit, onChoose: (String) -> Unit) {
    val context = LocalContext.current
    var apps by remember { mutableStateOf<List<Pair<String, String>>?>(null) }
    LaunchedEffect(Unit) {
        apps = withContext(Dispatchers.IO) {
            val packageManager = context.packageManager
            packageManager.queryIntentActivities(Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER), 0)
                .map { it.activityInfo.packageName to it.loadLabel(packageManager).toString() }
                .filter { it.first != context.packageName }
                .distinctBy { it.first }
                .sortedBy { it.second.lowercase() }
        }
    }
    ModalBottomSheet(onDismissRequest = onDismiss) {
        LazyColumn(contentPadding = PaddingValues(24.dp)) {
            item { Text(stringResource(R.string.choose_app), style = MaterialTheme.typography.titleLarge) }
            if (apps == null) item { CircularProgressIndicator() }
            if (apps?.isEmpty() == true) item { Text(stringResource(R.string.no_apps)) }
            items(apps.orEmpty(), key = { it.first }) { (pkg, label) ->
                ListItem(headlineContent = { Text(label) }, modifier = Modifier.clickable { onChoose(pkg) })
            }
        }
    }
}
