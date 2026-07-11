package com.kokseen1.komobo

enum class RemoteCommand(val label: String) {
    DPAD_UP("D-pad Up"),
    DPAD_DOWN("D-pad Down"),
    DPAD_LEFT("D-pad Left"),
    DPAD_RIGHT("D-pad Right"),
    ENTER("Enter"),
    BACK("Back/Esc"),
    VOLUME_UP("Remote Volume Up"),
    VOLUME_DOWN("Remote Volume Down"),
    MUTE("Mute");

    companion object {
        fun fromName(value: String?, fallback: RemoteCommand): RemoteCommand =
            entries.firstOrNull { it.name == value } ?: fallback
    }
}
