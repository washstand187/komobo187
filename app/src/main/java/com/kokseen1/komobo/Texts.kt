package com.kokseen1.komobo

import android.content.Context

object Texts {
    fun tr(context: Context, es: String, en: String): String =
        if (Prefs.language(context) == Prefs.LANG_EN) en else es

    fun statusText(context: Context, status: HidRemoteService.Status): String = when (status) {
        HidRemoteService.Status.STARTING -> tr(context, "Iniciando…", "Starting…")
        HidRemoteService.Status.NOT_CONNECTED -> tr(context, "No conectado", "Not connected")
        HidRemoteService.Status.BLUETOOTH_UNAVAILABLE -> tr(context, "Servicio Bluetooth no disponible", "Bluetooth service unavailable")
        HidRemoteService.Status.READY -> tr(context, "Listo para conectar", "Ready to connect")
        HidRemoteService.Status.REMOTE_UNAVAILABLE -> tr(context, "Control no disponible", "Remote unavailable")
        HidRemoteService.Status.CONNECTED -> tr(context, "Conectado", "Connected")
        HidRemoteService.Status.CONNECTING -> tr(context, "Conectando…", "Connecting…")
        HidRemoteService.Status.DISCONNECTING -> tr(context, "Desconectando…", "Disconnecting…")
        HidRemoteService.Status.STOPPED -> tr(context, "Control detenido", "Remote stopped")
        HidRemoteService.Status.NEEDS_PERMISSION -> tr(context, "Falta permiso de Bluetooth", "Bluetooth permission needed")
        HidRemoteService.Status.BLUETOOTH_OFF -> tr(context, "Bluetooth apagado", "Bluetooth is off")
        HidRemoteService.Status.CHOOSE_DEVICE -> tr(context, "Elige tu Kobo en Ajustes", "Choose your Kobo in Settings")
    }
}
