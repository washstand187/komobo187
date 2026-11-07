package com.kokseen1.komobo

import android.content.Context

object Prefs {
    const val LANG_ES = "es"
    const val LANG_EN = "en"

    private const val NAME = "bt_hid_remote_prefs"
    private const val KEY_HOST_ADDRESS = "host_address"
    private const val KEY_START_ON_BOOT = "start_on_boot"
    private const val KEY_VOLUME_UP_COMMAND = "volume_up_command"
    private const val KEY_VOLUME_DOWN_COMMAND = "volume_down_command"
    private const val KEY_DARK_MODE = "dark_mode"
    private const val KEY_LANGUAGE = "language"

    fun hostAddress(context: Context): String? = sp(context).getString(KEY_HOST_ADDRESS, null)
    fun setHostAddress(context: Context, address: String?) = sp(context).edit().putString(KEY_HOST_ADDRESS, address).apply()

    fun startOnBoot(context: Context): Boolean = sp(context).getBoolean(KEY_START_ON_BOOT, false)
    fun setStartOnBoot(context: Context, enabled: Boolean) = sp(context).edit().putBoolean(KEY_START_ON_BOOT, enabled).apply()

    fun volumeUpCommand(context: Context): RemoteCommand = RemoteCommand.fromName(sp(context).getString(KEY_VOLUME_UP_COMMAND, null), RemoteCommand.DPAD_UP)
    fun setVolumeUpCommand(context: Context, command: RemoteCommand) = sp(context).edit().putString(KEY_VOLUME_UP_COMMAND, command.name).apply()

    fun volumeDownCommand(context: Context): RemoteCommand = RemoteCommand.fromName(sp(context).getString(KEY_VOLUME_DOWN_COMMAND, null), RemoteCommand.DPAD_DOWN)
    fun setVolumeDownCommand(context: Context, command: RemoteCommand) = sp(context).edit().putString(KEY_VOLUME_DOWN_COMMAND, command.name).apply()

    fun darkMode(context: Context): Boolean = sp(context).getBoolean(KEY_DARK_MODE, false)
    fun setDarkMode(context: Context, enabled: Boolean) = sp(context).edit().putBoolean(KEY_DARK_MODE, enabled).apply()

    fun language(context: Context): String = sp(context).getString(KEY_LANGUAGE, LANG_ES) ?: LANG_ES
    fun setLanguage(context: Context, code: String) = sp(context).edit().putString(KEY_LANGUAGE, code).apply()

    private fun sp(context: Context) = context.getSharedPreferences(NAME, Context.MODE_PRIVATE)
}
