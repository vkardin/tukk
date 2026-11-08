package io.github.vkardin.tukk

import android.app.NotificationManager
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.provider.Settings
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun TriggerEditor(
    config: TriggerConfig,
    onChange: (TriggerConfig) -> Unit,
    onBack: () -> Unit,
    onRemove: () -> Unit,
) {
    val context = LocalContext.current
    var showActions by remember { mutableStateOf(false) }
    var showApps by remember { mutableStateOf(false) }
    var preview by remember { mutableStateOf(false) }
    var calibration by remember { mutableStateOf(false) }
    var confirmRemove by remember { mutableStateOf(false) }
    var revision by remember { mutableIntStateOf(0) }
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    DisposableEffect(lifecycle) {
        val observer = LifecycleEventObserver { _, event -> if (event == Lifecycle.Event.ON_RESUME) revision++ }
        lifecycle.addObserver(observer)
        onDispose { lifecycle.removeObserver(observer) }
    }
    val accessEnabled = remember(revision) { GestureAccessibility.instance != null }
    val soundGranted = remember(revision) {
        context.getSystemService(NotificationManager::class.java).isNotificationPolicyAccessGranted
    }
    val needsAccessibility = config.kind == TriggerKind.VOLUME || config.action == ActionKind.SCREENSHOT
    val needsSound = config.action in listOf(ActionKind.SILENT, ActionKind.SOUND, ActionKind.VIBRATE)
    val torchAvailable = context.packageManager.hasSystemFeature(PackageManager.FEATURE_CAMERA_FLASH)
    val missingHardware = !config.kind.available(context) ||
        (config.action in listOf(ActionKind.TORCH, ActionKind.SOS) && !torchAvailable) ||
        (config.action == ActionKind.SCREENSHOT && Build.VERSION.SDK_INT < 28)
    val needsApp = config.action in listOf(ActionKind.LAUNCH_APP, ActionKind.RECORDER)
    val ready = config.isReady(context)
    val maximumStrength = remember { maximumTorchStrength(context) }

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
            ToggleRow(R.string.enabled, config.enabled) { enabled ->
                if (!enabled || ready) onChange(config.copy(enabled = enabled))
            }
            if (missingHardware) Text(stringResource(R.string.unsupported), color = MaterialTheme.colorScheme.error)
        }
        SectionTitle(R.string.action)
        SettingsCard {
            ListItem(
                headlineContent = { Text(stringResource(config.action.title), style = MaterialTheme.typography.titleMedium) },
                trailingContent = { Text("›") },
                modifier = Modifier.clickable { showActions = true },
                colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
            )
            if (needsApp) {
                TextButton(onClick = { showApps = true }) {
                    val label = remember(config.packageName) {
                        try {
                            context.packageManager.getApplicationLabel(
                                context.packageManager.getApplicationInfo(config.packageName, 0),
                            ).toString()
                        } catch (_: PackageManager.NameNotFoundException) { null }
                    }
                    Text(label ?: stringResource(R.string.choose_app))
                }
            }
            if (needsApp || config.action == ActionKind.CAMERA) {
                Text(stringResource(R.string.launch_hint), style = MaterialTheme.typography.bodySmall)
            }
            if (config.action == ActionKind.TORCH && maximumStrength > 1) {
                Text(stringResource(R.string.brightness))
                Slider(
                    config.brightness.coerceIn(1, maximumStrength).toFloat(),
                    onValueChange = { onChange(config.copy(brightness = it.toInt())) },
                    valueRange = 1f..maximumStrength.toFloat(),
                    steps = (maximumStrength - 2).coerceAtLeast(0),
                )
            }
        }
        if (needsAccessibility) {
            SettingsCard {
                if (accessEnabled) Text(stringResource(R.string.accessibility_ready))
                AccessibilityDisclosure()
            }
        }
        if (needsSound && !soundGranted) {
            SettingsCard {
                Text(stringResource(R.string.dnd_body))
                TextButton(onClick = { context.startActivity(Intent(Settings.ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS)) }) {
                    Text(stringResource(R.string.grant_access))
                }
            }
        }
        if (config.kind == TriggerKind.SHAKE || config.kind == TriggerKind.CHOP) {
            SectionTitle(R.string.sensitivity)
            SettingsCard {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    for ((title, threshold) in listOf(R.string.low to 29f, R.string.medium to 23f, R.string.high to 18f)) {
                        FilterChip(config.threshold == threshold, { onChange(config.copy(threshold = threshold)) }, { Text(stringResource(title)) })
                    }
                }
                val sensitivityLabel = stringResource(R.string.sensitivity)
                Slider(
                    50f - config.threshold, { onChange(config.copy(threshold = 50f - it)) },
                    valueRange = 18f..32f, modifier = Modifier.semantics { contentDescription = sensitivityLabel },
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
                for ((index, title) in listOf(R.string.volume_up, R.string.volume_down, R.string.volume_combo).withIndex()) {
                    ChoiceRow(title, config.volumeMode == index) { onChange(config.copy(volumeMode = index)) }
                }
            }
        } else {
            OutlinedButton(onClick = { preview = true }, modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.test_gesture))
            }
        }
        SectionTitle(R.string.conditions)
        SettingsCard {
            for ((index, label) in listOf(R.string.always, R.string.screen_on, R.string.screen_off).withIndex()) {
                ChoiceRow(label, config.screenMode == index) { onChange(config.copy(screenMode = index)) }
            }
            Text(stringResource(R.string.screen_warning), style = MaterialTheme.typography.bodySmall)
            HorizontalDivider()
            ToggleRow(R.string.pocket_guard, config.pocketGuard) { onChange(config.copy(pocketGuard = it)) }
            ToggleRow(R.string.haptic, config.vibration) { onChange(config.copy(vibration = it)) }
            ToggleRow(R.string.pause_charging, config.pauseCharging) { onChange(config.copy(pauseCharging = it)) }
            Text(stringResource(R.string.cooldown, config.cooldownMs))
            Slider(
                config.cooldownMs.toFloat(), { onChange(config.copy(cooldownMs = it.toInt())) },
                valueRange = 500f..5000f, steps = 44,
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
                    val available = when (action) {
                        ActionKind.TORCH, ActionKind.SOS -> torchAvailable
                        ActionKind.SCREENSHOT -> Build.VERSION.SDK_INT >= 28
                        else -> true
                    }
                    ListItem(
                        headlineContent = { Text(stringResource(action.title)) },
                        supportingContent = { if (!available) Text(stringResource(R.string.unsupported)) },
                        modifier = Modifier.clickable(enabled = available) {
                            onChange(config.copy(action = action, enabled = false))
                            showActions = false
                        },
                    )
                }
            }
        }
    }
    if (showApps) AppPicker(
        onDismiss = { showApps = false },
        onChoose = { onChange(config.copy(packageName = it)); showApps = false },
    )
    if (preview || calibration) {
        GesturePreview(
            config, calibration,
            onCalibrated = { onChange(config.copy(tapThreshold = it)) },
            onDismiss = { preview = false; calibration = false },
        )
    }
    if (confirmRemove) {
        AlertDialog(
            onDismissRequest = { confirmRemove = false },
            title = { Text(stringResource(R.string.remove)) },
            text = { Text(stringResource(R.string.remove_body)) },
            confirmButton = { TextButton(onClick = onRemove) { Text(stringResource(R.string.remove)) } },
            dismissButton = { TextButton(onClick = { confirmRemove = false }) { Text(stringResource(R.string.cancel)) } },
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AppPicker(onDismiss: () -> Unit, onChoose: (String) -> Unit) {
    val context = LocalContext.current
    var apps by remember { mutableStateOf<List<Pair<String, String>>?>(null) }
    LaunchedEffect(Unit) {
        apps = withContext(Dispatchers.IO) {
            context.packageManager.queryIntentActivities(
                Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER), 0,
            ).map { it.activityInfo.packageName to it.loadLabel(context.packageManager).toString() }
                .distinctBy { it.first }.sortedBy { it.second.lowercase() }
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

