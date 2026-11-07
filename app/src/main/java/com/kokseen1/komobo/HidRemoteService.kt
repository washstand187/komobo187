package com.kokseen1.komobo

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothHidDevice
import android.bluetooth.BluetoothHidDeviceAppQosSettings
import android.bluetooth.BluetoothHidDeviceAppSdpSettings
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothProfile
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import android.support.v4.media.session.MediaSessionCompat
import android.support.v4.media.session.PlaybackStateCompat
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import androidx.media.VolumeProviderCompat
import java.util.concurrent.Executors

class HidRemoteService : Service() {
    // Estados que puede mostrar la app. El texto (español o inglés) lo pone la clase Texts.
    enum class Status {
        STARTING,
        NOT_CONNECTED,
        BLUETOOTH_UNAVAILABLE,
        READY,
        REMOTE_UNAVAILABLE,
        CONNECTED,
        CONNECTING,
        DISCONNECTING,
        STOPPED,
        NEEDS_PERMISSION,
        BLUETOOTH_OFF,
        CHOOSE_DEVICE
    }

    companion object {
        const val ACTION_START = "com.kokseen1.komobo.START"
        const val ACTION_STOP = "com.kokseen1.komobo.STOP"
        const val ACTION_SEND_UP = "com.kokseen1.komobo.SEND_UP"
        const val ACTION_SEND_DOWN = "com.kokseen1.komobo.SEND_DOWN"
        const val ACTION_SEND_LEFT = "com.kokseen1.komobo.SEND_LEFT"
        const val ACTION_SEND_RIGHT = "com.kokseen1.komobo.SEND_RIGHT"
        const val ACTION_SEND_ENTER = "com.kokseen1.komobo.SEND_ENTER"
        const val ACTION_SEND_BACK = "com.kokseen1.komobo.SEND_BACK"
        const val ACTION_CONNECT_SELECTED = "com.kokseen1.komobo.CONNECT_SELECTED"
        const val ACTION_VOLUME_UP_MAPPING = "com.kokseen1.komobo.VOLUME_UP_MAPPING"
        const val ACTION_VOLUME_DOWN_MAPPING = "com.kokseen1.komobo.VOLUME_DOWN_MAPPING"

        private const val TAG = "Komobo"
        private const val CHANNEL_ID = "hid_remote"
        private const val NOTIF_ID = 10

        @Volatile
        var status: Status = Status.NOT_CONNECTED
            private set
    }

    private val executor = Executors.newSingleThreadExecutor()
    private var hidDevice: BluetoothHidDevice? = null
    private var hostDevice: BluetoothDevice? = null
    private var profileProxyRequested = false
    private var mediaSession: MediaSessionCompat? = null
    private var wakeLock: PowerManager.WakeLock? = null
    @Volatile private var lastKeyboardReport = ByteArray(8)
    @Volatile private var lastConsumerReport = ByteArray(2)

    private val profileListener = object : BluetoothProfile.ServiceListener {
        override fun onServiceConnected(profile: Int, proxy: BluetoothProfile) {
            if (profile == BluetoothProfile.HID_DEVICE) {
                hidDevice = proxy as BluetoothHidDevice
                registerHidApp()
            }
        }

        override fun onServiceDisconnected(profile: Int) {
            if (profile == BluetoothProfile.HID_DEVICE) {
                profileProxyRequested = false
                hidDevice = null
                setStatus(Status.BLUETOOTH_UNAVAILABLE)
            }
        }
    }

    private val hidCallback = object : BluetoothHidDevice.Callback() {
        override fun onAppStatusChanged(pluggedDevice: BluetoothDevice?, registered: Boolean) {
            Log.i(TAG, "HID app registered=$registered plugged=$pluggedDevice")
            if (registered) {
                setStatus(Status.READY)
                pluggedDevice?.let { hostDevice = it }
                connectSelectedHost()
            } else {
                setStatus(Status.REMOTE_UNAVAILABLE)
            }
        }

        override fun onConnectionStateChanged(device: BluetoothDevice?, state: Int) {
            Log.i(TAG, "HID connection state=$state device=$device")
            if (state == BluetoothProfile.STATE_CONNECTED) {
                hostDevice = device
                setStatus(
                    Status.CONNECTED,
                    device?.let(::safeName) ?: Texts.tr(this@HidRemoteService, "desconocido", "unknown")
                )
            } else if (state == BluetoothProfile.STATE_DISCONNECTED && device == hostDevice) {
                hostDevice = null
                setStatus(Status.NOT_CONNECTED)
            } else if (state == BluetoothProfile.STATE_CONNECTING) {
                setStatus(Status.CONNECTING)
            } else if (state == BluetoothProfile.STATE_DISCONNECTING) {
                setStatus(Status.DISCONNECTING)
            }
        }

        override fun onGetReport(device: BluetoothDevice?, type: Byte, id: Byte, bufferSize: Int) {
            val report = when (id.toInt()) {
                HidReports.REPORT_ID_KEYBOARD -> lastKeyboardReport
                HidReports.REPORT_ID_CONSUMER -> lastConsumerReport
                else -> null
            }
            Log.i(TAG, "HID get report type=$type id=$id size=$bufferSize")
            if (device != null && report != null && hasBtConnectPermission()) {
                hidDevice?.replyReport(device, type, id, report.copyOf(minOf(report.size, bufferSize)))
            } else if (device != null && hasBtConnectPermission()) {
                hidDevice?.reportError(device, BluetoothHidDevice.ERROR_RSP_INVALID_RPT_ID)
            }
        }

        override fun onSetReport(device: BluetoothDevice?, type: Byte, id: Byte, data: ByteArray?) {
            Log.i(TAG, "HID set report type=$type id=$id bytes=${data?.size ?: 0}")
        }

        override fun onSetProtocol(device: BluetoothDevice?, protocol: Byte) {
            Log.i(TAG, "HID set protocol=$protocol device=$device")
        }
    }

    override fun onCreate() {
        super.onCreate()
        status = Status.STARTING
        createNotificationChannel()
        startForeground(NOTIF_ID, buildNotification(Texts.statusText(this, Status.STARTING)))
        acquireWakeLock()
        startMediaSessionVolumeTrap()
        initBluetoothHid()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START -> initBluetoothHid()
            ACTION_CONNECT_SELECTED -> connectSelectedHost()
            ACTION_SEND_UP -> sendCommand(RemoteCommand.DPAD_UP)
            ACTION_SEND_DOWN -> sendCommand(RemoteCommand.DPAD_DOWN)
            ACTION_SEND_LEFT -> sendCommand(RemoteCommand.DPAD_LEFT)
            ACTION_SEND_RIGHT -> sendCommand(RemoteCommand.DPAD_RIGHT)
            ACTION_SEND_ENTER -> sendCommand(RemoteCommand.ENTER)
            ACTION_SEND_BACK -> sendCommand(RemoteCommand.BACK)
            ACTION_VOLUME_UP_MAPPING -> sendCommand(Prefs.volumeUpCommand(this))
            ACTION_VOLUME_DOWN_MAPPING -> sendCommand(Prefs.volumeDownCommand(this))
            ACTION_STOP -> stopSelf()
        }
        return START_STICKY
    }

    override fun onDestroy() {
        status = Status.STOPPED
        mediaSession?.isActive = false
        mediaSession?.release()
        wakeLock?.takeIf { it.isHeld }?.release()
        if (hasBtConnectPermission()) {
            hostDevice?.let { host -> runCatching { hidDevice?.disconnect(host) } }
        }
        runCatching { hidDevice?.unregisterApp() }
        val adapter = (getSystemService(BLUETOOTH_SERVICE) as BluetoothManager).adapter
        runCatching { adapter?.closeProfileProxy(BluetoothProfile.HID_DEVICE, hidDevice) }
        executor.shutdownNow()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun setStatus(newStatus: Status, detail: String? = null) {
        status = newStatus
        val text = Texts.statusText(this, newStatus) + (if (detail != null) ": $detail" else "")
        runCatching { startForeground(NOTIF_ID, buildNotification(text)) }
            .onFailure { Log.e(TAG, "No se pudo actualizar la notificación", it) }
    }

    private fun initBluetoothHid() {
        if (hidDevice != null || profileProxyRequested) return
        if (!hasBtConnectPermission()) {
            Log.w(TAG, "Missing BLUETOOTH_CONNECT")
            setStatus(Status.NEEDS_PERMISSION)
            return
        }
        val adapter = (getSystemService(BLUETOOTH_SERVICE) as BluetoothManager).adapter ?: return
        if (!adapter.isEnabled) {
            setStatus(Status.BLUETOOTH_OFF)
            return
        }
        profileProxyRequested = adapter.getProfileProxy(this, profileListener, BluetoothProfile.HID_DEVICE)
    }

    private fun registerHidApp() {
        if (!hasBtConnectPermission()) return
        val sdp = BluetoothHidDeviceAppSdpSettings(
            "Komobo",
            "Bluetooth HID remote with shortcuts and volume-key mappings",
            "Local PoC",
            BluetoothHidDevice.SUBCLASS1_KEYBOARD,
            HidReports.descriptor()
        )
        val qos = BluetoothHidDeviceAppQosSettings(
            BluetoothHidDeviceAppQosSettings.SERVICE_BEST_EFFORT,
            800,
            9,
            0,
            11250,
            BluetoothHidDeviceAppQosSettings.MAX
        )
        hidDevice?.registerApp(sdp, null, qos, executor, hidCallback)
    }

    private fun connectSelectedHost() {
        if (!hasBtConnectPermission()) return
        val adapter = (getSystemService(BLUETOOTH_SERVICE) as BluetoothManager).adapter
        if (adapter?.isEnabled != true) {
            setStatus(Status.BLUETOOTH_OFF)
            return
        }
        if (hidDevice == null) {
            initBluetoothHid()
            setStatus(Status.STARTING)
            return
        }
        val host = selectedHost()
        if (host != null) {
            hostDevice = host
            val hid = hidDevice
            val state = runCatching { hid?.getConnectionState(host) }.getOrNull()
            if (state == BluetoothProfile.STATE_CONNECTED) {
                setStatus(Status.CONNECTED, safeName(host))
            } else {
                setStatus(Status.CONNECTING, safeName(host))
                runCatching { hid?.connect(host) }
            }
        } else {
            setStatus(Status.CHOOSE_DEVICE)
        }
    }

    private fun rememberReport(report: HidReports.Report, data: ByteArray) {
        if (report.reportId == HidReports.REPORT_ID_KEYBOARD) lastKeyboardReport = data
        else lastConsumerReport = data
    }

    private fun sendCommand(command: RemoteCommand) {
        val host = hostDevice ?: selectedHost()
        val hid = hidDevice
        if (hid == null || host == null || !hasBtConnectPermission()) {
            Log.w(TAG, "No se puede enviar ${command.label}. hid=$hid host=$host permiso=${hasBtConnectPermission()}")
            return
        }
        hostDevice = host
        val report = HidReports.reportFor(command)
        executor.execute {
            try {
                rememberReport(report, report.press)
                val pressed = hid.sendReport(host, report.reportId, report.press)
                Log.i(TAG, "Pulsada ${command.label} en ${safeName(host)} ok=$pressed")
                Thread.sleep(35)
            } catch (e: Exception) {
                Log.e(TAG, "Falló al pulsar ${command.label}", e)
            } finally {
                // La tecla siempre se suelta, aunque algo haya fallado antes.
                rememberReport(report, report.release)
                val released = runCatching { hid.sendReport(host, report.reportId, report.release) }.getOrDefault(false)
                Log.i(TAG, "Soltada ${command.label} ok=$released")
            }
        }
    }

    private fun selectedHost(): BluetoothDevice? {
        if (!hasBtConnectPermission()) return null
        val address = Prefs.hostAddress(this) ?: return null
        val adapter = (getSystemService(BLUETOOTH_SERVICE) as BluetoothManager).adapter ?: return null
        return runCatching { adapter.getRemoteDevice(address) }.getOrNull()
    }

    private fun startMediaSessionVolumeTrap() {
        val session = MediaSessionCompat(this, "Komobo")
        session.setPlaybackState(
            PlaybackStateCompat.Builder()
                .setState(PlaybackStateCompat.STATE_PLAYING, 0L, 1f)
                .setActions(PlaybackStateCompat.ACTION_PLAY or PlaybackStateCompat.ACTION_PAUSE)
                .build()
        )
        val provider = object : VolumeProviderCompat(VolumeProviderCompat.VOLUME_CONTROL_RELATIVE, 100, 50) {
            override fun onAdjustVolume(direction: Int) {
                when {
                    direction > 0 -> sendCommand(Prefs.volumeUpCommand(this@HidRemoteService))
                    direction < 0 -> sendCommand(Prefs.volumeDownCommand(this@HidRemoteService))
                }
                currentVolume = 50
            }
        }
        session.setPlaybackToRemote(provider)
        session.isActive = true
        mediaSession = session
    }

    private fun acquireWakeLock() {
        val pm = getSystemService(POWER_SERVICE) as PowerManager
        wakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "Komobo:service").apply {
            setReferenceCounted(false)
            acquire(10 * 60 * 1000L)
        }
    }

    private fun hasBtConnectPermission(): Boolean = Build.VERSION.SDK_INT < 31 ||
        ContextCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED

    private fun buildNotification(text: String): Notification {
        fun serviceIntent(action: String, requestCode: Int) = PendingIntent.getService(
            this, requestCode, Intent(this, HidRemoteService::class.java).setAction(action), pendingFlags()
        )
        val openIntent = PendingIntent.getActivity(
            this,
            99,
            Intent(this, MainActivity::class.java).addFlags(
                Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
            ),
            pendingFlags()
        )
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.stat_sys_data_bluetooth)
            .setContentTitle("Komobo")
            .setContentText(text)
            .setContentIntent(openIntent)
            .setOngoing(true)
            .addAction(0, Texts.tr(this, "Anterior", "Previous"), serviceIntent(ACTION_SEND_UP, 1))
            .addAction(0, Texts.tr(this, "Siguiente", "Next"), serviceIntent(ACTION_SEND_DOWN, 2))
            .addAction(0, Texts.tr(this, "Desconectar", "Disconnect"), serviceIntent(ACTION_STOP, 3))
            .build()
    }

    private fun pendingFlags(): Int = PendingIntent.FLAG_UPDATE_CURRENT or
        if (Build.VERSION.SDK_INT >= 23) PendingIntent.FLAG_IMMUTABLE else 0

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= 26) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                Texts.tr(this, "Control Komobo", "Komobo remote"),
                NotificationManager.IMPORTANCE_LOW
            )
            getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
        }
    }

    private fun safeName(device: BluetoothDevice): String = if (hasBtConnectPermission()) {
        runCatching { device.name ?: device.address }.getOrDefault(device.address)
    } else Texts.tr(this, "Dispositivo Bluetooth", "Bluetooth device")
}
