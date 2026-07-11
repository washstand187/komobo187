# Komobo

Komobo turns an Android phone into a Bluetooth page-turn remote for Kobo eReaders. It uses Android's Bluetooth HID Device API, so the Kobo receives standard keyboard reports without an app installed on the reader.

## Features

- Previous and next page controls
- Configurable physical volume-button actions
- Lock-screen and screen-off operation through a foreground service
- Lock/wake Kobo control
- Paired-device selection and reconnection
- Optional restart after the phone reboots
- Portrait-only, Kobo e-ink-inspired interface
- No internet permission, ads, analytics, or root access

## Requirements

- Android 9 or newer
- A phone that supports Bluetooth HID Device mode
- A paired Kobo eReader with Bluetooth remote support

Komobo has been developed and tested with a Samsung Galaxy S23 and Kobo Clara BW.

## Build

```bash
./gradlew assembleDebug
```

The APK is written to:

```text
app/build/outputs/apk/debug/app-debug.apk
```

GitHub Actions also builds the debug APK for every push and pull request. Download it from the workflow run's `komobo-debug-apk` artifact.

## Setup

1. Install and open Komobo on the Android phone.
2. Grant the requested Bluetooth and notification permissions.
3. Pair the phone from the Kobo's Bluetooth settings.
4. Open Komobo settings and select the Kobo under **Change device**.
5. Tap **Reconnect** if the connection is not established automatically.
6. Configure the phone's physical volume-button actions if desired.

## Notes

- The foreground service must remain active for volume controls to work while the phone is locked.
- Installing an updated APK restarts the HID service; tap **Reconnect** afterward if necessary.
- Bluetooth HID behavior depends on the Android device and receiving reader firmware.
