package io.github.vkardin.tukk

import android.content.Intent
import android.os.Build
import android.os.SystemClock
import androidx.activity.compose.BackHandler
import androidx.appcompat.app.AppCompatDelegate
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.*
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.os.LocaleListCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import kotlinx.coroutines.delay

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun TukkApp(
    onToggle: (Boolean) -> Unit,
    notificationDialog: Boolean,
    onDismissNotification: () -> Unit,
    onRequestNotification: () -> Unit,
) {
    val context = LocalContext.current
    var page by rememberSaveable { mutableStateOf("home") }
    var editing by rememberSaveable { mutableStateOf<String?>(null) }
    var showAdd by remember { mutableStateOf(false) }
    var showLanguage by remember { mutableStateOf(false) }
    var developer by rememberSaveable { mutableStateOf(false) }
    var onboarding by rememberSaveable { mutableStateOf(!context.gesturePreferences().getBoolean("onboarded", false)) }
    var settings by remember { mutableStateOf(GestureSettings.load(context)) }
    var configs by remember { mutableStateOf(TriggerKind.entries.map { TriggerConfig.load(context, it) }) }
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    DisposableEffect(context, lifecycle) {
        val prefs = context.gesturePreferences()
        val refresh = {
            settings = GestureSettings.load(context)
            configs = TriggerKind.entries.map { TriggerConfig.load(context, it) }
        }
        val listener = android.content.SharedPreferences.OnSharedPreferenceChangeListener { _, _ -> refresh() }
        val observer = androidx.lifecycle.LifecycleEventObserver { _, event ->
            if (event == androidx.lifecycle.Lifecycle.Event.ON_RESUME) refresh()
        }
        prefs.registerOnSharedPreferenceChangeListener(listener)
        lifecycle.addObserver(observer)
        onDispose {
            prefs.unregisterOnSharedPreferenceChangeListener(listener)
            lifecycle.removeObserver(observer)
        }
    }
    val active by GestureService.running.collectAsStateWithLifecycle()
    val pulse by GestureService.pulse.collectAsStateWithLifecycle()
    val sos by GestureService.sos.collectAsStateWithLifecycle()
    val failed by GestureService.failure.collectAsStateWithLifecycle()
    val recording by MotionRecorder.active.collectAsStateWithLifecycle()
    val dark = settings.theme == 0 || (settings.theme == 2 && isSystemInDarkTheme())
    val colors = when {
        settings.dynamicColor && Build.VERSION.SDK_INT >= 31 ->
            if (dark) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        dark -> darkColorScheme(
            primary = Color(0xFFFF9A66), onPrimary = Color(0xFF351709),
            background = Color(0xFF121010), surface = Color(0xFF1B1715),
            surfaceContainer = Color(0xFF231D19), onSurface = Color(0xFFF7F1EB),
            onBackground = Color(0xFFF7F1EB), onSurfaceVariant = Color(0xFFC7B8AE),
            secondaryContainer = Color(0xFF513221), onSecondaryContainer = Color(0xFFFFD9A0),
            outlineVariant = Color(0xFF3E342D),
        )
        else -> lightColorScheme(
            primary = Color(0xFF9D3C0B), onPrimary = Color.White,
            background = Color(0xFFF7F1EB), surface = Color(0xFFFFFBF7),
            surfaceContainer = Color(0xFFF0E6DD),
            secondaryContainer = Color(0xFFFFDBC1), onSecondaryContainer = Color(0xFF512100),
        )
    }
    val base = Typography()
    val headings = FontFamily(Font(R.font.sora_bold, FontWeight.Bold))
    val body = FontFamily(Font(R.font.inter_regular))
    val typography = Typography(
        headlineLarge = base.headlineLarge.copy(fontFamily = headings),
        headlineMedium = base.headlineMedium.copy(fontFamily = headings),
        titleLarge = base.titleLarge.copy(fontFamily = headings),
        titleMedium = base.titleMedium.copy(fontFamily = headings),
        bodyLarge = base.bodyLarge.copy(fontFamily = body),
        bodyMedium = base.bodyMedium.copy(fontFamily = body),
        bodySmall = base.bodySmall.copy(fontFamily = body),
        labelLarge = base.labelLarge.copy(fontFamily = body),
    )
    fun save(config: TriggerConfig) {
        config.save(context)
        configs = configs.map { if (it.kind == config.kind) config else it }
    }
    BackHandler(editing != null || page != "home") {
        if (editing != null) editing = null else page = "home"
    }
    MaterialTheme(colorScheme = colors, typography = typography) {
        Surface(Modifier.fillMaxSize(), color = colors.background) {
            Scaffold(
                containerColor = colors.background,
                bottomBar = {
                    if (editing == null) {
                        NavigationBar(containerColor = colors.surfaceContainer) {
                            NavigationBarItem(
                                selected = page == "home", onClick = { page = "home" },
                                icon = { Text("⌂", fontSize = 25.sp, modifier = Modifier.clearAndSetSemantics {}) },
                                label = { Text(stringResource(R.string.home)) },
                            )
                            NavigationBarItem(
                                selected = page == "settings", onClick = { page = "settings" },
                                icon = { Text("⚙", fontSize = 24.sp, modifier = Modifier.clearAndSetSemantics {}) },
                                label = { Text(stringResource(R.string.settings)) },
                            )
                        }
                    }
                },
            ) { padding ->
                Box(Modifier.padding(padding).fillMaxSize(), contentAlignment = Alignment.TopCenter) {
                    val editor = configs.firstOrNull { it.kind.name == editing }
                    when {
                        editor != null -> TriggerEditor(
                            editor, onChange = ::save, onBack = { editing = null },
                            onRemove = { save(editor.copy(added = false, enabled = false)); editing = null },
                        )
                        page == "settings" -> SettingsPage(
                            settings = settings,
                            onChange = { settings = it; it.save(context) },
                            onLanguage = { showLanguage = true },
                            developer = developer, recording = recording,
                            onRecord = { MotionRecorder.toggle(context) },
                        )
                        else -> {
                            LazyColumn(
                                Modifier.widthIn(max = 600.dp).fillMaxWidth(),
                                contentPadding = PaddingValues(24.dp),
                                verticalArrangement = Arrangement.spacedBy(16.dp),
                            ) {
                                item {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Ember(pulse, active, Modifier.size(42.dp).combinedClickable(
                                            onClick = {}, onLongClick = { if (BuildConfig.DEBUG) developer = !developer },
                                        ))
                                        Spacer(Modifier.width(12.dp))
                                        Text("tukk", style = MaterialTheme.typography.headlineLarge)
                                    }
                                    Spacer(Modifier.height(8.dp))
                                    Text(stringResource(R.string.home_subtitle), color = colors.onSurfaceVariant)
                                }
                                item {
                                    Card(
                                        shape = RoundedCornerShape(24.dp),
                                        colors = CardDefaults.cardColors(containerColor = colors.surfaceContainer),
                                    ) {
                                        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                            Row(verticalAlignment = Alignment.CenterVertically) {
                                                Column(Modifier.weight(1f)) {
                                                    Text(
                                                        stringResource(if (active) R.string.active else R.string.paused),
                                                        color = if (active) colors.primary else colors.onSurface,
                                                        style = MaterialTheme.typography.titleMedium,
                                                    )
                                                    Spacer(Modifier.height(6.dp))
                                                    Text(
                                                        stringResource(R.string.active_count,
                                                            configs.count { it.added && it.enabled }, configs.count { it.added }),
                                                        style = MaterialTheme.typography.bodySmall, color = colors.onSurfaceVariant,
                                                    )
                                                }
                                                val label = stringResource(R.string.gestures)
                                                Switch(active, onToggle, Modifier.semantics { contentDescription = label })
                                            }
                                            if (sos) {
                                                Button(onClick = { GestureService.stopSos() }) { Text(stringResource(R.string.stop_sos)) }
                                            }
                                        }
                                    }
                                }
                                if (failed) item {
                                    Text(stringResource(R.string.action_failed), color = colors.error)
                                }
                                item {
                                    val addLabel = stringResource(R.string.add_gesture)
                                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                                        Text(stringResource(R.string.my_gestures), Modifier.weight(1f), style = MaterialTheme.typography.titleLarge)
                                        TextButton(onClick = { showAdd = true }, enabled = configs.any { !it.added }) {
                                            Text("+", fontSize = 26.sp, modifier = Modifier.semantics {
                                                contentDescription = addLabel
                                            })
                                        }
                                    }
                                }
                                items(configs.filter { it.added }, key = { it.kind.name }) { config ->
                                    val available = config.kind.available(context)
                                    Card(
                                        onClick = { editing = config.kind.name },
                                        shape = RoundedCornerShape(20.dp),
                                        colors = CardDefaults.cardColors(containerColor = colors.surfaceContainer),
                                    ) {
                                        Row(Modifier.fillMaxWidth().padding(18.dp), verticalAlignment = Alignment.CenterVertically) {
                                            GestureBadge(config.kind)
                                            Spacer(Modifier.width(14.dp))
                                            Column(Modifier.weight(1f)) {
                                                Text(stringResource(config.kind.title), style = MaterialTheme.typography.titleMedium)
                                                Spacer(Modifier.height(5.dp))
                                                Text(
                                                    stringResource(if (available) config.action.title else R.string.unsupported),
                                                    color = colors.onSurfaceVariant, style = MaterialTheme.typography.bodyMedium,
                                                )
                                            }
                                            val label = stringResource(config.kind.title)
                                            Switch(
                                                checked = config.enabled, enabled = available,
                                                onCheckedChange = {
                                                    if (it && !config.isReady(context)) editing = config.kind.name
                                                    else save(config.copy(enabled = it))
                                                },
                                                modifier = Modifier.semantics { contentDescription = label },
                                            )
                                        }
                                    }
                                }
                                item {
                                    OutlinedButton(
                                        onClick = { showAdd = true }, enabled = configs.any { !it.added },
                                        modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp),
                                    ) { Text(stringResource(R.string.add_gesture)) }
                                }
                            }
                        }
                    }
                }
            }
        }
        if (showAdd) {
            ModalBottomSheet(onDismissRequest = { showAdd = false }) {
                LazyColumn(contentPadding = PaddingValues(start = 24.dp, end = 24.dp, bottom = 32.dp)) {
                    item {
                        Text(stringResource(R.string.choose_gesture), style = typography.titleLarge)
                        Spacer(Modifier.height(16.dp))
                    }
                    items(configs.filter { !it.added }, key = { it.kind.name }) { config ->
                        ListItem(
                            headlineContent = { Text(stringResource(config.kind.title)) },
                            supportingContent = {
                                Text(stringResource(if (config.kind.available(context)) config.kind.hint else R.string.unsupported))
                            },
                            leadingContent = { GestureBadge(config.kind) },
                            modifier = Modifier.clickable(enabled = config.kind.available(context)) {
                                save(config.copy(added = true, enabled = false))
                                showAdd = false
                                editing = config.kind.name
                            },
                        )
                    }
                }
            }
        }
        if (showLanguage) {
            AlertDialog(
                onDismissRequest = { showLanguage = false },
                title = { Text(stringResource(R.string.language)) },
                text = {
                    Column {
                        for ((tag, label) in listOf("uk" to "Українська", "en" to "English", "ru" to "Русский")) {
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
        if (onboarding && !showLanguage) {
            AlertDialog(
                onDismissRequest = {},
                title = { Text(stringResource(R.string.onboarding_title)) },
                text = { Text(stringResource(R.string.onboarding_body)) },
                confirmButton = {
                    TextButton(onClick = {
                        context.gesturePreferences().edit().putBoolean("onboarded", true).apply()
                        onboarding = false
                    }) { Text(stringResource(R.string.get_started)) }
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

@Composable
internal fun GestureBadge(kind: TriggerKind) {
    Box(
        Modifier.size(48.dp).background(MaterialTheme.colorScheme.secondaryContainer, RoundedCornerShape(15.dp)),
        contentAlignment = Alignment.Center,
    ) {
        Text(kind.symbol, fontSize = 26.sp, color = MaterialTheme.colorScheme.onSecondaryContainer, modifier = Modifier.clearAndSetSemantics {})
    }
}

@Composable
private fun Ember(pulse: Long, active: Boolean, modifier: Modifier) {
    var flashing by remember { mutableStateOf(false) }
    LaunchedEffect(pulse) {
        val remaining = 600 - (SystemClock.elapsedRealtime() - pulse)
        flashing = pulse > 0 && remaining > 0
        if (flashing) delay(remaining)
        flashing = false
    }
    val alpha by animateFloatAsState(if (flashing) 1f else if (active) 0.85f else 0.45f)
    Image(painterResource(R.drawable.ic_ember), null, modifier.alpha(alpha))
}

@Composable
internal fun SectionTitle(title: Int) {
    Text(stringResource(title), style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
}

@Composable
internal fun ToggleRow(label: Int, checked: Boolean, onChange: (Boolean) -> Unit) {
    val text = stringResource(label)
    Row(Modifier.fillMaxWidth().heightIn(min = 52.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(text, Modifier.weight(1f))
        Switch(checked, onChange, Modifier.padding(start = 12.dp).semantics { contentDescription = text })
    }
}

@Composable
internal fun ChoiceRow(label: Int, selected: Boolean, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick).padding(vertical = 4.dp)
            .semantics(mergeDescendants = true) { this.selected = selected },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RadioButton(selected, onClick = null)
        Spacer(Modifier.width(14.dp))
        Text(stringResource(label))
    }
}

