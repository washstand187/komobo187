package com.kokseen1.komobo

import android.Manifest
import android.app.AlertDialog
import android.app.Dialog
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.ColorDrawable
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.view.Gravity
import android.view.HapticFeedbackConstants
import android.view.View
import android.view.Window
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.CheckBox
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.Spinner
import android.widget.TextView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat

class MainActivity : AppCompatActivity() {
    private val handler = Handler(Looper.getMainLooper())
    private lateinit var statusDot: TextView
    private lateinit var statusText: TextView
    private lateinit var deviceText: TextView
    private var bluetoothEnableRequestInFlight = false
    private var bluetoothEnablePromptAttempted = false
    private var connectAfterBluetoothEnabled = false

    private val enableBluetoothLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) {
        bluetoothEnableRequestInFlight = false
        if (isBluetoothEnabled()) {
            send(HidRemoteService.ACTION_START)
            if (connectAfterBluetoothEnabled) send(HidRemoteService.ACTION_CONNECT_SELECTED)
        }
        connectAfterBluetoothEnabled = false
        updateConnectionCard()
    }

    private val statusUpdater = object : Runnable {
        override fun run() {
            updateConnectionCard()
            handler.postDelayed(this, 750)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        requestRuntimePermissions()
        setContentView(buildUi())
    }

    override fun onResume() {
        super.onResume()
        requestBluetoothEnableIfNeeded()
        updateConnectionCard()
        handler.post(statusUpdater)
    }

    override fun onPause() {
        handler.removeCallbacks(statusUpdater)
        super.onPause()
    }

    private fun buildUi(): View {
        val screenHeightDp = resources.displayMetrics.heightPixels / resources.displayMetrics.density
        val controlSpaceDp = (screenHeightDp - 460f).coerceIn(250f, 420f)
        val nextButtonHeight = dp((controlSpaceDp * .68f).toInt().coerceIn(170, 270))
        val previousButtonHeight = dp((controlSpaceDp * .32f).toInt().coerceIn(80, 125))

        val page = vertical().apply {
            setPadding(dp(24), dp(30), dp(24), dp(30))
            setBackgroundColor(color("#F4F1E8"))
        }

        page.addView(horizontal().apply {
            gravity = Gravity.CENTER_VERTICAL
            addView(TextView(this@MainActivity).apply {
                text = "komobo"
                textSize = 30f
                setTextColor(Color.BLACK)
                typeface = Typeface.create("serif", Typeface.BOLD_ITALIC)
            }, LinearLayout.LayoutParams(0, dp(54), 1f).apply { gravity = Gravity.CENTER_VERTICAL })
            addView(actionButton("Settings", secondary = true) { showSettings() }.apply {
                layoutParams = LinearLayout.LayoutParams(dp(122), dp(42))
            })
        })
        page.addView(divider())

        page.addView(vertical().apply {
            setPadding(0, dp(18), 0, dp(18))
            addView(horizontal().apply {
                gravity = Gravity.CENTER_VERTICAL
                statusDot = TextView(this@MainActivity).apply {
                    text = "●"
                    textSize = 13f
                }
                addView(statusDot, LinearLayout.LayoutParams(dp(24), dp(36)))
                addView(vertical().apply {
                    statusText = label("Starting…", 15f, bold = true).apply { setTextColor(Color.BLACK) }
                    deviceText = label("No target selected", 12f).apply { setTextColor(color("#555555")) }
                    addView(statusText)
                    addView(deviceText)
                }, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
                addView(actionButton("Reconnect", secondary = true) {
                    if (requestBluetoothEnableIfNeeded(connectAfterEnable = true, allowRepeat = true)) {
                        send(HidRemoteService.ACTION_START)
                        send(HidRemoteService.ACTION_CONNECT_SELECTED)
                    }
                }.apply {
                    layoutParams = LinearLayout.LayoutParams(dp(122), dp(42))
                })
            })
        })
        page.addView(divider())

        page.addView(label("Page turner", 35f, bold = true).apply {
            typeface = Typeface.create("serif", Typeface.BOLD)
            setTextColor(Color.BLACK)
            setPadding(0, dp(24), 0, 0)
        })
        page.addView(label("BLUETOOTH PAGE REMOTE", 11f, bold = true).apply {
            letterSpacing = .08f
            setTextColor(color("#555555"))
            setPadding(0, dp(4), 0, dp(16))
        })

        page.addView(remoteButton("→", "Next page", HidRemoteService.ACTION_SEND_DOWN, primary = true), matchHeight(nextButtonHeight))
        page.addView(remoteButton("←", "Previous page", HidRemoteService.ACTION_SEND_UP, compact = true), matchHeight(previousButtonHeight).apply {
            topMargin = dp(12)
        })

        page.addView(actionButton("○   Lock / wake Kobo", secondary = true) {
            send(HidRemoteService.ACTION_SEND_BACK)
        }.apply {
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(50)).apply {
                topMargin = dp(12)
            }
        })

        page.addView(divider().apply {
            layoutParams = matchHeight(dp(1)).apply { topMargin = dp(20) }
        })
        page.addView(label("Volume keys remain active while your phone is locked.", 13f).apply {
            gravity = Gravity.CENTER
            typeface = Typeface.create("serif", Typeface.ITALIC)
            setTextColor(color("#444444"))
            setPadding(dp(8), dp(12), dp(8), 0)
        })

        return ScrollView(this).apply {
            isFillViewport = true
            setBackgroundColor(color("#F4F1E8"))
            addView(page)
        }
    }

    private fun remoteButton(symbol: String, title: String, action: String, primary: Boolean = false, compact: Boolean = false): View =
        vertical().apply {
            gravity = Gravity.CENTER
            isClickable = true
            isFocusable = true
            background = if (primary) rounded("#111111", 3) else outlined("#F4F1E8", "#111111", 2, 3)
            contentDescription = title
            addView(label(symbol, if (compact) 34f else 64f, bold = false).apply {
                gravity = Gravity.CENTER
                setTextColor(color(if (primary) "#FFFFFF" else "#111111"))
            }, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(if (compact) 42 else 88)))
            addView(label(title, if (compact) 15f else 20f, bold = true).apply {
                gravity = Gravity.CENTER
                typeface = Typeface.create("serif", Typeface.BOLD)
                setTextColor(color(if (primary) "#FFFFFF" else "#111111"))
            })
            setOnClickListener {
                performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                send(action)
            }
        }

    private fun showSettings() {
        val dialog = Dialog(this)
        val content = vertical().apply {
            setPadding(dp(22), dp(20), dp(22), dp(20))
            background = rounded("#F4F1E8", 2)
            addView(horizontal().apply {
                gravity = Gravity.CENTER_VERTICAL
                addView(label("Settings", 26f, bold = true).apply {
                    typeface = Typeface.create("serif", Typeface.BOLD)
                }, LinearLayout.LayoutParams(0, dp(48), 1f))
                addView(actionButton("Done", secondary = true) { dialog.dismiss() }.apply {
                    layoutParams = LinearLayout.LayoutParams(dp(86), dp(40))
                })
            })
            addView(divider().apply { layoutParams = matchHeight(dp(1)).apply { bottomMargin = dp(18) } })
            addView(sectionLabel("CONNECTION"))
            addView(actionButton("Change device") { showDevicePicker() })
            addView(actionButton("Bluetooth settings", secondary = true) {
                startActivity(Intent(Settings.ACTION_BLUETOOTH_SETTINGS))
            }.apply { layoutParams = matchHeight(dp(52)).apply { topMargin = dp(8) } })
            addView(divider().apply { layoutParams = matchHeight(dp(1)).apply { topMargin = dp(22); bottomMargin = dp(18) } })
            addView(sectionLabel("VOLUME BUTTON ACTIONS"))
            addView(mappingSpinner("Volume up", true))
            addView(mappingSpinner("Volume down", false))
            addView(CheckBox(this@MainActivity).apply {
                text = "Start remote after phone restart"
                textSize = 14f
                setTextColor(Color.BLACK)
                isChecked = Prefs.startOnBoot(this@MainActivity)
                setOnCheckedChangeListener { _, checked -> Prefs.setStartOnBoot(this@MainActivity, checked) }
                setPadding(0, dp(12), 0, dp(6))
            })
            addView(divider().apply { layoutParams = matchHeight(dp(1)).apply { topMargin = dp(12); bottomMargin = dp(14) } })
            addView(actionButton("Disconnect and stop remote", secondary = true) {
                confirmStopRemote()
            }.apply { layoutParams = matchHeight(dp(52)) })
        }
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE)
        dialog.setContentView(ScrollView(this).apply {
            setBackgroundColor(color("#F4F1E8"))
            addView(content)
        })
        dialog.window?.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
        dialog.show()
        dialog.window?.setLayout((resources.displayMetrics.widthPixels * .92f).toInt(), LinearLayout.LayoutParams.WRAP_CONTENT)
    }

    private fun confirmStopRemote() {
        AlertDialog.Builder(this)
            .setTitle("Disconnect remote?")
            .setMessage("Page controls and physical volume-button actions will stop until you reconnect.")
            .setNegativeButton("Cancel", null)
            .setPositiveButton("Disconnect and stop") { _, _ -> send(HidRemoteService.ACTION_STOP) }
            .show()
    }

    private fun mappingSpinner(label: String, isUp: Boolean): View = vertical().apply {
        addView(label(label, 13f, bold = true).apply { setPadding(dp(2), dp(7), 0, dp(2)) })
        val commands = listOf(RemoteCommand.DPAD_UP, RemoteCommand.DPAD_DOWN, RemoteCommand.ENTER, RemoteCommand.BACK)
        val spinner = Spinner(this@MainActivity).apply {
            background = outlined("#F4F1E8", "#111111", 1, 2)
            setPadding(dp(12), 0, dp(8), 0)
            adapter = ArrayAdapter(this@MainActivity, android.R.layout.simple_spinner_dropdown_item, commands.map { friendlyCommand(it) })
            val selected = if (isUp) Prefs.volumeUpCommand(this@MainActivity) else Prefs.volumeDownCommand(this@MainActivity)
            setSelection(commands.indexOf(selected).coerceAtLeast(0))
            onItemSelectedListener = object : android.widget.AdapterView.OnItemSelectedListener {
                override fun onItemSelected(parent: android.widget.AdapterView<*>?, view: View?, position: Int, id: Long) {
                    if (isUp) Prefs.setVolumeUpCommand(this@MainActivity, commands[position])
                    else Prefs.setVolumeDownCommand(this@MainActivity, commands[position])
                }
                override fun onNothingSelected(parent: android.widget.AdapterView<*>?) = Unit
            }
        }
        addView(spinner, matchHeight(dp(48)))
    }

    private fun showDevicePicker() {
        if (!hasBtConnectPermission()) {
            requestRuntimePermissions()
            return
        }
        if (!requestBluetoothEnableIfNeeded(allowRepeat = true)) return
        val devices = (getSystemService(BLUETOOTH_SERVICE) as BluetoothManager).adapter?.bondedDevices
            ?.sortedBy { safeName(it) }.orEmpty()
        if (devices.isEmpty()) {
            AlertDialog.Builder(this).setMessage("No paired Bluetooth devices found.")
                .setPositiveButton("Bluetooth settings") { _, _ -> startActivity(Intent(Settings.ACTION_BLUETOOTH_SETTINGS)) }
                .setNegativeButton("Cancel", null).show()
            return
        }
        AlertDialog.Builder(this)
            .setTitle("Select device")
            .setItems(devices.map(::safeName).toTypedArray()) { _, index ->
                Prefs.setHostAddress(this, devices[index].address)
                updateConnectionCard()
                send(HidRemoteService.ACTION_START)
                send(HidRemoteService.ACTION_CONNECT_SELECTED)
            }
            .show()
    }

    private fun updateConnectionCard() {
        if (!::statusText.isInitialized) return
        val status = HidRemoteService.connectionStatus
        val connected = status.startsWith("Connected")
        statusText.text = status
        statusDot.text = if (connected) "●" else "○"
        statusDot.setTextColor(Color.BLACK)
        val address = Prefs.hostAddress(this)
        deviceText.text = selectedDeviceName(address) ?: if (address == null) "Select a device in Settings" else address
    }

    private fun selectedDeviceName(address: String?): String? {
        if (address == null || !hasBtConnectPermission()) return null
        return (getSystemService(BLUETOOTH_SERVICE) as BluetoothManager).adapter?.bondedDevices
            ?.firstOrNull { it.address == address }?.let(::safeName)
    }

    private fun send(action: String) {
        ContextCompat.startForegroundService(this, Intent(this, HidRemoteService::class.java).setAction(action))
    }

    private fun requestRuntimePermissions() {
        val permissions = mutableListOf<String>()
        if (Build.VERSION.SDK_INT >= 31 && !hasBtConnectPermission()) permissions += Manifest.permission.BLUETOOTH_CONNECT
        if (Build.VERSION.SDK_INT >= 33 && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            permissions += Manifest.permission.POST_NOTIFICATIONS
        }
        if (permissions.isNotEmpty()) ActivityCompat.requestPermissions(this, permissions.toTypedArray(), 100)
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == 100 && hasBtConnectPermission()) requestBluetoothEnableIfNeeded()
    }

    private fun requestBluetoothEnableIfNeeded(
        connectAfterEnable: Boolean = false,
        allowRepeat: Boolean = false
    ): Boolean {
        if (!hasBtConnectPermission()) return false
        val adapter = (getSystemService(BLUETOOTH_SERVICE) as BluetoothManager).adapter ?: return false
        if (adapter.isEnabled) return true

        connectAfterBluetoothEnabled = connectAfterBluetoothEnabled || connectAfterEnable
        if (!bluetoothEnableRequestInFlight && (allowRepeat || !bluetoothEnablePromptAttempted)) {
            bluetoothEnableRequestInFlight = true
            bluetoothEnablePromptAttempted = true
            enableBluetoothLauncher.launch(Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE))
        }
        return false
    }

    private fun isBluetoothEnabled(): Boolean {
        if (!hasBtConnectPermission()) return false
        return (getSystemService(BLUETOOTH_SERVICE) as BluetoothManager).adapter?.isEnabled == true
    }

    private fun hasBtConnectPermission() = Build.VERSION.SDK_INT < 31 ||
        checkSelfPermission(Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED

    private fun safeName(device: BluetoothDevice): String = runCatching { device.name ?: device.address }.getOrDefault(device.address)

    private fun friendlyCommand(command: RemoteCommand): String = when (command) {
        RemoteCommand.DPAD_UP, RemoteCommand.DPAD_LEFT -> "Previous page"
        RemoteCommand.DPAD_DOWN, RemoteCommand.DPAD_RIGHT -> "Next page"
        RemoteCommand.ENTER -> "Select / OK"
        RemoteCommand.BACK -> "Lock / wake Kobo"
        else -> command.label
    }

    private fun sectionLabel(value: String) = label(value, 11f, bold = true).apply {
        letterSpacing = .1f
        setTextColor(color("#555555"))
        setPadding(dp(2), 0, 0, dp(10))
    }

    private fun vertical() = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
    private fun horizontal() = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
    private fun label(value: String, size: Float, bold: Boolean = false) = TextView(this).apply {
        text = value
        textSize = size
        setTextColor(color("#241A17"))
        if (bold) setTypeface(typeface, Typeface.BOLD)
    }
    private fun actionButton(value: String, secondary: Boolean = false, action: () -> Unit) = Button(this).apply {
        text = value
        isAllCaps = false
        setTextColor(color(if (secondary) "#111111" else "#FFFFFF"))
        background = if (secondary) outlined("#F4F1E8", "#111111", 1, 2) else rounded("#111111", 2)
        setOnClickListener { action() }
    }
    private fun divider() = View(this).apply { setBackgroundColor(color("#777777")); layoutParams = matchHeight(dp(1)) }
    private fun outlined(fill: String, stroke: String, widthDp: Int, radiusDp: Int) = GradientDrawable().apply {
        shape = GradientDrawable.RECTANGLE
        cornerRadius = dp(radiusDp).toFloat()
        setColor(color(fill))
        setStroke(dp(widthDp), color(stroke))
    }
    private fun rounded(hex: String, radiusDp: Int) = GradientDrawable().apply {
        shape = GradientDrawable.RECTANGLE
        cornerRadius = dp(radiusDp).toFloat()
        setColor(color(hex))
    }
    private fun weighted(weight: Float, height: Int) = LinearLayout.LayoutParams(0, height, weight)
    private fun matchHeight(height: Int) = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, height)
    private fun color(value: String) = Color.parseColor(value)
    private fun dp(value: Int) = (value * resources.displayMetrics.density + .5f).toInt()
}
