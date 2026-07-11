package com.kokseen1.komobo

import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat

class ShortcutActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val action = intent?.action ?: HidRemoteService.ACTION_START
        ContextCompat.startForegroundService(this, Intent(this, HidRemoteService::class.java).setAction(action))
        finish()
    }
}
