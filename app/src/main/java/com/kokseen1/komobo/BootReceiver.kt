package com.kokseen1.komobo

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.content.ContextCompat

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED && Prefs.startOnBoot(context)) {
            ContextCompat.startForegroundService(context, Intent(context, HidRemoteService::class.java).setAction(HidRemoteService.ACTION_START))
        }
    }
}
