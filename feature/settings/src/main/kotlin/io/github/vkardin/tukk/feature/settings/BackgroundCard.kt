package io.github.vkardin.tukk.feature.settings

import android.annotation.SuppressLint
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import io.github.vkardin.tukk.core.ui.R
import io.github.vkardin.tukk.core.ui.SettingsCard
import io.github.vkardin.tukk.core.ui.rememberResumeCount
import io.github.vkardin.tukk.core.ui.startSettings

private enum class Vendor(@StringRes val advice: Int) {
    XIAOMI(R.string.background_xiaomi),
    SAMSUNG(R.string.background_samsung),
    HUAWEI(R.string.background_huawei),
    ONEPLUS(R.string.background_oneplus),
    OTHER(R.string.background_advice),
}

private fun currentVendor(): Vendor = when (Build.MANUFACTURER.lowercase()) {
    "xiaomi", "redmi", "poco" -> Vendor.XIAOMI
    "samsung" -> Vendor.SAMSUNG
    "huawei", "honor" -> Vendor.HUAWEI
    "oneplus", "oppo", "realme" -> Vendor.ONEPLUS
    else -> Vendor.OTHER
}

/** Autostart screens of MIUI and HyperOS, which have no public intent. */
private val XIAOMI_AUTOSTART = listOf(
    ComponentName("com.miui.securitycenter", "com.miui.permcenter.autostart.AutoStartManagementActivity"),
    ComponentName("com.miui.securitycenter", "com.miui.powercenter.PowerSettings"),
)

/** The "Background work" guidance with per-manufacturer instructions. */
@Composable
internal fun BackgroundCard() {
    val context = LocalContext.current
    val resumes = rememberResumeCount()
    val unrestricted = remember(resumes) {
        context.getSystemService(PowerManager::class.java).isIgnoringBatteryOptimizations(context.packageName)
    }
    val vendor = remember { currentVendor() }
    SettingsCard {
        Text(stringResource(R.string.battery_title), style = MaterialTheme.typography.titleMedium)
        Text(
            stringResource(if (unrestricted) R.string.battery_unrestricted else R.string.battery_restricted),
            color = if (unrestricted) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
        )
        if (!unrestricted) {
            TextButton(onClick = { context.openBatterySettings() }) { Text(stringResource(R.string.battery_open)) }
        }
        Spacer(Modifier.height(8.dp))
        Text(stringResource(vendor.advice))
        if (vendor == Vendor.XIAOMI) {
            TextButton(onClick = { context.openXiaomiAutostart() }) { Text(stringResource(R.string.autostart_open)) }
        }
        Spacer(Modifier.height(4.dp))
        Text(stringResource(R.string.screen_warning), style = MaterialTheme.typography.bodySmall)
        TextButton(onClick = {
            context.startSettings(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:${context.packageName}")))
        }) { Text(stringResource(R.string.system_settings)) }
    }
}

// The system list is used instead of a direct exemption request, which Google Play restricts.
@SuppressLint("BatteryLife")
private fun Context.openBatterySettings() {
    startSettings(Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS))
}

private fun Context.openXiaomiAutostart() {
    val intent = XIAOMI_AUTOSTART.map { Intent().setComponent(it) }
        .firstOrNull { it.resolveActivity(packageManager) != null }
        ?: Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:$packageName"))
    startSettings(intent)
}
