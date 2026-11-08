package io.github.vkardin.tukk.feature.home

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.vkardin.tukk.core.model.TriggerKind
import io.github.vkardin.tukk.core.ui.Ember
import io.github.vkardin.tukk.core.ui.GestureBadge
import io.github.vkardin.tukk.core.ui.R
import io.github.vkardin.tukk.core.ui.hint
import io.github.vkardin.tukk.core.ui.rememberResumeCount
import io.github.vkardin.tukk.core.ui.title

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun HomeScreen(
    onToggleService: (Boolean) -> Unit,
    onEdit: (TriggerKind) -> Unit,
    onEmberLongPress: () -> Unit,
    viewModel: HomeViewModel = hiltViewModel(),
) {
    val triggers by viewModel.triggers.collectAsStateWithLifecycle()
    val running by viewModel.running.collectAsStateWithLifecycle()
    val pulse by viewModel.pulse.collectAsStateWithLifecycle()
    val failure by viewModel.failure.collectAsStateWithLifecycle()
    val sos by viewModel.sos.collectAsStateWithLifecycle()
    var showAdd by rememberSaveable { mutableStateOf(false) }
    // Access granted in system settings changes readiness, so recheck on return.
    rememberResumeCount()
    val colors = MaterialTheme.colorScheme
    val added = triggers.filter { it.added }

    LazyColumn(
        Modifier.widthIn(max = 600.dp).fillMaxWidth(),
        contentPadding = PaddingValues(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Ember(
                    pulse, running,
                    Modifier.size(42.dp).combinedClickable(onClick = {}, onLongClick = onEmberLongPress),
                )
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
                    Row(
                        Modifier.fillMaxWidth().toggleable(running, role = Role.Switch, onValueChange = onToggleService),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text(
                                stringResource(if (running) R.string.active else R.string.paused),
                                color = if (running) colors.primary else colors.onSurface,
                                style = MaterialTheme.typography.titleMedium,
                            )
                            Spacer(Modifier.height(6.dp))
                            Text(
                                stringResource(R.string.active_count, added.count { it.enabled }, added.size),
                                style = MaterialTheme.typography.bodySmall, color = colors.onSurfaceVariant,
                            )
                        }
                        Switch(running, onCheckedChange = null)
                    }
                    if (sos) {
                        Button(onClick = viewModel::stopSos) { Text(stringResource(R.string.stop_sos)) }
                    }
                }
            }
        }
        if (failure) item {
            Text(stringResource(R.string.action_failed), color = colors.error)
        }
        item {
            val addLabel = stringResource(R.string.add_gesture)
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(stringResource(R.string.my_gestures), Modifier.weight(1f), style = MaterialTheme.typography.titleLarge)
                TextButton(onClick = { showAdd = true }, enabled = added.size < triggers.size) {
                    Text("+", fontSize = 26.sp, modifier = Modifier.semantics { contentDescription = addLabel })
                }
            }
        }
        items(added, key = { it.kind.name }) { config ->
            val available = viewModel.isAvailable(config.kind)
            val label = stringResource(config.kind.title)
            Card(
                onClick = { onEdit(config.kind) },
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = colors.surfaceContainer),
            ) {
                Row(Modifier.fillMaxWidth().padding(18.dp), verticalAlignment = Alignment.CenterVertically) {
                    GestureBadge(config.kind)
                    Spacer(Modifier.width(14.dp))
                    Column(Modifier.weight(1f)) {
                        Text(label, style = MaterialTheme.typography.titleMedium)
                        Spacer(Modifier.height(5.dp))
                        Text(
                            stringResource(if (available) config.action.title else R.string.unsupported),
                            color = colors.onSurfaceVariant, style = MaterialTheme.typography.bodyMedium,
                        )
                    }
                    Switch(
                        checked = config.enabled, enabled = available,
                        onCheckedChange = {
                            // A gesture that still lacks access or an app opens its editor instead.
                            if (it && !viewModel.isReady(config)) onEdit(config.kind) else viewModel.setEnabled(config, it)
                        },
                        modifier = Modifier.semantics { contentDescription = label },
                    )
                }
            }
        }
        item {
            OutlinedButton(
                onClick = { showAdd = true }, enabled = added.size < triggers.size,
                modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp),
            ) { Text(stringResource(R.string.add_gesture)) }
        }
    }

    if (showAdd) {
        ModalBottomSheet(onDismissRequest = { showAdd = false }) {
            LazyColumn(contentPadding = PaddingValues(start = 24.dp, end = 24.dp, bottom = 32.dp)) {
                item {
                    Text(stringResource(R.string.choose_gesture), style = MaterialTheme.typography.titleLarge)
                    Spacer(Modifier.height(16.dp))
                }
                items(triggers.filter { !it.added }, key = { it.kind.name }) { config ->
                    val available = viewModel.isAvailable(config.kind)
                    ListItem(
                        headlineContent = { Text(stringResource(config.kind.title)) },
                        supportingContent = { Text(stringResource(if (available) config.kind.hint else R.string.unsupported)) },
                        leadingContent = { GestureBadge(config.kind) },
                        modifier = Modifier.clickable(enabled = available) {
                            viewModel.add(config)
                            showAdd = false
                            onEdit(config.kind)
                        },
                    )
                }
            }
        }
    }
}
