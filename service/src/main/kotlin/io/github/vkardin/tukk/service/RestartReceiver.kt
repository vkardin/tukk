package io.github.vkardin.tukk.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import dagger.hilt.android.AndroidEntryPoint
import io.github.vkardin.tukk.core.data.ApplicationScope
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import javax.inject.Inject

/** Restores the gesture service after a reboot or an app update. */
@AndroidEntryPoint
class RestartReceiver : BroadcastReceiver() {
    @Inject lateinit var controller: GestureController
    @Inject @ApplicationScope lateinit var scope: CoroutineScope

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED &&
            intent.action != Intent.ACTION_MY_PACKAGE_REPLACED
        ) return
        val pending = goAsync()
        scope.launch {
            try {
                controller.restore()
            } finally {
                pending.finish()
            }
        }
    }
}
