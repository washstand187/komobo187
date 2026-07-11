package com.kokseen1.komobo

object HidReports {
    const val REPORT_ID_KEYBOARD = 1
    const val REPORT_ID_CONSUMER = 2

    private const val KEY_DPAD_UP: Byte = 0x52.toByte()
    private const val KEY_DPAD_DOWN: Byte = 0x51.toByte()
    private const val KEY_DPAD_LEFT: Byte = 0x50.toByte()
    private const val KEY_DPAD_RIGHT: Byte = 0x4F.toByte()
    private const val KEY_ENTER: Byte = 0x28.toByte()
    private const val KEY_ESC: Byte = 0x29.toByte()

    private const val CONSUMER_VOLUME_UP = 0x00E9
    private const val CONSUMER_VOLUME_DOWN = 0x00EA
    private const val CONSUMER_MUTE = 0x00E2
    private const val CONSUMER_CHANNEL_UP = 0x009C
    private const val CONSUMER_CHANNEL_DOWN = 0x009D

    data class Report(val reportId: Int, val press: ByteArray, val release: ByteArray)

    fun reportFor(command: RemoteCommand): Report = when (command) {
        RemoteCommand.DPAD_UP -> keyboard(KEY_DPAD_UP)
        RemoteCommand.DPAD_DOWN -> keyboard(KEY_DPAD_DOWN)
        RemoteCommand.DPAD_LEFT -> keyboard(KEY_DPAD_LEFT)
        RemoteCommand.DPAD_RIGHT -> keyboard(KEY_DPAD_RIGHT)
        RemoteCommand.ENTER -> keyboard(KEY_ENTER)
        RemoteCommand.BACK -> keyboard(KEY_ESC)
        RemoteCommand.CHANNEL_UP -> consumer(CONSUMER_CHANNEL_UP)
        RemoteCommand.CHANNEL_DOWN -> consumer(CONSUMER_CHANNEL_DOWN)
        RemoteCommand.VOLUME_UP -> consumer(CONSUMER_VOLUME_UP)
        RemoteCommand.VOLUME_DOWN -> consumer(CONSUMER_VOLUME_DOWN)
        RemoteCommand.MUTE -> consumer(CONSUMER_MUTE)
    }

    private fun keyboard(key: Byte): Report = Report(
        REPORT_ID_KEYBOARD,
        byteArrayOf(0, 0, key, 0, 0, 0, 0, 0),
        byteArrayOf(0, 0, 0, 0, 0, 0, 0, 0)
    )

    private fun consumer(usage: Int): Report = Report(
        REPORT_ID_CONSUMER,
        byteArrayOf((usage and 0xFF).toByte(), ((usage ushr 8) and 0xFF).toByte()),
        byteArrayOf(0, 0)
    )

    fun descriptor(): ByteArray = byteArrayOf(
        // Keyboard collection, Report ID 1.
        0x05, 0x01, 0x09, 0x06, 0xA1.toByte(), 0x01,
        0x85.toByte(), REPORT_ID_KEYBOARD.toByte(),
        0x05, 0x07, 0x19, 0xE0.toByte(), 0x29, 0xE7.toByte(),
        0x15, 0x00, 0x25, 0x01, 0x75, 0x01, 0x95.toByte(), 0x08,
        0x81.toByte(), 0x02,
        0x95.toByte(), 0x01, 0x75, 0x08, 0x81.toByte(), 0x03,
        0x95.toByte(), 0x06, 0x75, 0x08,
        0x15, 0x00, 0x25, 0x65,
        0x05, 0x07, 0x19, 0x00, 0x29, 0x65,
        0x81.toByte(), 0x00,
        0xC0.toByte(),

        // Consumer control collection, Report ID 2. Two-byte usage value.
        0x05, 0x0C, 0x09, 0x01, 0xA1.toByte(), 0x01,
        0x85.toByte(), REPORT_ID_CONSUMER.toByte(),
        0x15, 0x00,
        0x26, 0xFF.toByte(), 0x02,
        0x19, 0x00,
        0x2A, 0xFF.toByte(), 0x02,
        0x75, 0x10,
        0x95.toByte(), 0x01,
        0x81.toByte(), 0x00,
        0xC0.toByte()
    )
}
