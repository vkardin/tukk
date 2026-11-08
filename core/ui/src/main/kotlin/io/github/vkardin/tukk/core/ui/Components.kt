package io.github.vkardin.tukk.core.ui

import android.os.SystemClock
import androidx.annotation.StringRes
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.vkardin.tukk.core.model.TriggerKind
import kotlinx.coroutines.delay

@Composable
fun SettingsCard(content: @Composable ColumnScope.() -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
    ) {
        Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(6.dp), content = content)
    }
}

@Composable
fun SectionTitle(@StringRes title: Int) {
    Text(stringResource(title), style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
}

/** A whole-row switch; TalkBack reads the label and the state together. */
@Composable
fun ToggleRow(@StringRes label: Int, checked: Boolean, enabled: Boolean = true, onChange: (Boolean) -> Unit) {
    Row(
        Modifier.fillMaxWidth().heightIn(min = 52.dp)
            .toggleable(checked, enabled = enabled, role = Role.Switch, onValueChange = onChange),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(stringResource(label), Modifier.weight(1f))
        Switch(checked, onCheckedChange = null, enabled = enabled, modifier = Modifier.padding(start = 12.dp))
    }
}

@Composable
fun ChoiceRow(@StringRes label: Int, selected: Boolean, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().heightIn(min = 48.dp)
            .selectable(selected, role = Role.RadioButton, onClick = onClick),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RadioButton(selected, onClick = null)
        Spacer(Modifier.width(14.dp))
        Text(stringResource(label))
    }
}

@Composable
fun GestureBadge(kind: TriggerKind) {
    Box(
        Modifier.size(48.dp).background(MaterialTheme.colorScheme.secondaryContainer, RoundedCornerShape(15.dp)),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            kind.symbol, fontSize = 26.sp, color = MaterialTheme.colorScheme.onSecondaryContainer,
            modifier = Modifier.clearAndSetSemantics {},
        )
    }
}

/**
 * The ember mark in its three states: smoldering while paused, glowing while
 * gestures are active and a short flash after a gesture fires.
 */
@Composable
fun Ember(pulse: Long, active: Boolean, modifier: Modifier = Modifier) {
    var flashing by remember { mutableStateOf(false) }
    LaunchedEffect(pulse) {
        val remaining = FLASH_MS - (SystemClock.elapsedRealtime() - pulse)
        flashing = pulse > 0 && remaining > 0
        if (flashing) delay(remaining)
        flashing = false
    }
    val glow by animateFloatAsState(if (flashing) 1f else 0f, label = "glow")
    val drawable = when {
        flashing -> R.drawable.ic_ember_flash
        active -> R.drawable.ic_ember
        else -> R.drawable.ic_ember_dim
    }
    val description = stringResource(if (active) R.string.ember_active else R.string.ember_paused)
    Box(modifier, contentAlignment = Alignment.Center) {
        if (glow > 0f) {
            Canvas(Modifier.fillMaxSize().scale(1.6f)) {
                drawCircle(
                    Brush.radialGradient(listOf(TukkColors.Heat.copy(alpha = 0.45f * glow), Color.Transparent)),
                )
            }
        }
        Image(painterResource(drawable), description, Modifier.fillMaxSize())
    }
}

private const val FLASH_MS = 600L
