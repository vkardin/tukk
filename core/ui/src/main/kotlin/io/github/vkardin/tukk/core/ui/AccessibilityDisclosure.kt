package io.github.vkardin.tukk.core.ui

import android.content.ActivityNotFoundException
import android.content.Intent
import android.provider.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource

/**
 * Prominent disclosure shown before sending the user to the accessibility settings,
 * as required for apps that declare an accessibility service on Google Play.
 */
@Composable
fun AccessibilityDisclosure(connected: Boolean) {
    val context = LocalContext.current
    var confirmation by remember { mutableStateOf(false) }
    if (connected) Text(stringResource(R.string.accessibility_ready), color = MaterialTheme.colorScheme.primary)
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
                    context.startSettings(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
                }) { Text(stringResource(R.string.grant_access)) }
            },
            dismissButton = { TextButton(onClick = { confirmation = false }) { Text(stringResource(R.string.cancel)) } },
        )
    }
}

/** Opens a system settings screen, falling back to the app details page when it is missing. */
fun android.content.Context.startSettings(intent: Intent) {
    try {
        startActivity(intent)
    } catch (_: ActivityNotFoundException) {
        try {
            startActivity(
                Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, android.net.Uri.parse("package:$packageName")),
            )
        } catch (_: ActivityNotFoundException) {
            // Nothing else to offer.
        }
    }
}
