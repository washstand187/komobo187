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

## How it works

Komobo makes the Android phone behave like a Bluetooth keyboard. It does not install anything on the eReader and does not send commands over Wi-Fi or the internet.

When the background service starts, it:

1. Registers the app with Android's `BluetoothHidDevice` profile as a keyboard-class HID device.
2. Advertises a HID report descriptor containing standard keyboard and consumer-control reports.
3. Connects the HID profile to the selected bonded eReader.
4. Sends a short key-press report followed by an empty key-release report for each action.

The default page actions use standard keyboard usages that Kobo understands:

| Komobo action | HID key sent | Observed Kobo behavior |
| --- | --- | --- |
| Previous page | D-pad Up | Previous page |
| Next page | D-pad Down | Next page |
| Lock / wake | Escape / Back | Toggle sleep and wake |

The exact behavior is determined by the receiving eReader firmware.

### Physical volume buttons

Android normally sends the phone's volume keys to the active audio route. Komobo creates an active `MediaSessionCompat`, marks it as playing, and assigns it a remote relative-volume `VolumeProviderCompat`. This asks Android to route volume adjustments to Komobo's `onAdjustVolume()` callback.

The complete path is:

```text
Phone volume key
    → Android media session
    → Komobo VolumeProvider
    → configured Previous/Next/Lock action
    → Bluetooth HID press + release reports
    → eReader
```

The two physical keys are independently configurable in **Settings → Volume button actions**. Komobo resets its virtual volume to the midpoint after every adjustment, so repeated presses continue producing relative up/down callbacks instead of reaching a minimum or maximum.

### Why it works on the lock screen

The volume-key handling lives in `HidRemoteService`, not in the visible activity. Komobo runs that service in the foreground with an ongoing notification and keeps the media session active. Android can therefore deliver hardware volume adjustments while the app is hidden, the screen is off, or the phone is locked.

Komobo also takes a partial CPU wake lock for up to ten minutes when the service starts. This helps short-term continuity but is not the mechanism that captures the buttons, and it is not a guarantee that Android will keep the process forever. The foreground service and active media session are the essential pieces.

Behavior can still vary by phone manufacturer, Android version, current media playback, and audio-routing state. If another media app takes control of the volume route, reopen Komobo or tap **Reconnect**.

## Notes

- The foreground service must remain active for volume controls to work while the phone is locked.
- Installing an updated APK restarts the HID service; tap **Reconnect** afterward if necessary.
- Bluetooth HID behavior depends on the Android device and receiving reader firmware.
