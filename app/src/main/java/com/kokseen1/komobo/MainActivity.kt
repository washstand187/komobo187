package com.kokseen1.komobo

import android.Manifest
import android.app.Dialog
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.content.Intent
import android.content.pm.PackageManager
import android.content.res.ColorStateList
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
import android.widget.Button
import android.widget.CheckBox
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.core.view.WindowCompat

class MainActivity : AppCompatActivity() {

    private var dark = false

    private val bgColor: String
        get() = if (dark) "#000000" else "#F4F1E8"
    private val fgColor: String
        get() = if (dark) "#FFFFFF" else "#000000"
    private val mutedColor: String
        get() = if (dark) "#9E9E9E" else "#000000"
    private val lineColor: String
        get() = if (dark) "#555555" else "#000000"

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
        dark = Prefs.darkMode(this)
        requestRuntimePermissions()
        setContentView(buildUi())
        applySystemBars()
    }

    override fun onResume() {
        super.onResume()
        if (requestBluetoothEnableIfNeeded()) send(HidRemoteService.ACTION_START)
        updateConnectionCard()
        handler.post(statusUpdater)
    }

    override fun onPause() {
        handler.removeCallbacks(statusUpdater)
        super.onPause()
    }

    private fun applySystemBars() {
        window.setBackgroundDrawable(ColorDrawable(color(bgColor)))
        window.statusBarColor = color(bgColor)
        window.navigationBarColor = color(bgColor)
        WindowCompat.getInsetsController(window, window.decorView).apply {
            isAppearanceLightStatusBars = !dark
            isAppearanceLightNavigationBars = !dark
        }
    }

    private fun buildUi(): View {
        val screenHeightDp = resources.displayMetrics.heightPixels / resources.displayMetrics.density
        val controlSpaceDp = (screenHeightDp - 460f).coerceIn(250f, 420f)
        val nextButtonHeight = dp((controlSpaceDp * .68f).toInt().coerceIn(170, 270))
        val previousButtonHeight = dp((controlSpaceDp * .32f).toInt().coerceIn(80, 125))

        val page = vertical().apply {
            setPadding(dp(24), dp(30), dp(24), dp(30))
            setBackgroundColor(color(bgColor))
        }

        page.addView(horizontal().apply {
            gravity = Gravity.CENTER_VERTICAL
            addView(TextView(this@MainActivity).apply {
                text = "komobo"
                textSize = 30f
                setTextColor(color(fgColor))
                typeface = Typeface.create("serif", Typeface.BOLD_ITALIC)
            }, LinearLayout.LayoutParams(0, dp(54), 1f).apply { gravity = Gravity.CENTER_VERTICAL })
            addView(actionButton(tr("Ajustes", "Settings"), secondary = true) { showSettings() }.apply {
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
                    statusText = label(Texts.statusText(this@MainActivity, HidRemoteService.status), 15f, bold = true).apply {
                        setTextColor(color(fgColor))
                    }
                    deviceText = label(tr("Ningún dispositivo elegido", "No device selected"), 12f).apply {
                        setTextColor(color(mutedColor))
                    }
                    addView(statusText)
                    addView(deviceText)
                }, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
                addView(actionButton(tr("Reconectar", "Reconnect"), secondary = true) {
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

        page.addView(label(tr("Pasapáginas", "Page turner"), 35f, bold = true).apply {
            typeface = Typeface.create("serif", Typeface.BOLD)
            setTextColor(color(fgColor))
            setPadding(0, dp(24), 0, 0)
        })
        page.addView(label(tr("CONTROL REMOTO BLUETOOTH", "BLUETOOTH PAGE REMOTE"), 11f, bold = true).apply {
            letterSpacing = .08f
            setTextColor(color(mutedColor))
            setPadding(0, dp(4), 0, dp(16))
        })

        page.addView(
            remoteButton("→", tr("Página siguiente", "Next page"), HidRemoteService.ACTION_SEND_DOWN, primary = true),
            matchHeight(nextButtonHeight)
        )
        page.addView(
            remoteButton("←", tr("Página anterior", "Previous page"), HidRemoteService.ACTION_SEND_UP, compact = true),
            matchHeight(previousButtonHeight).apply { topMargin = dp(12) }
        )

        page.addView(actionButton(tr("○   Bloquear / desbloquear Kobo", "○   Lock / unlock Kobo"), secondary = true) {
            send(HidRemoteService.ACTION_SEND_BACK)
        }.apply {
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(50)).apply {
                topMargin = dp(12)
            }
        })

        page.addView(divider().apply {
            layoutParams = matchHeight(dp(1)).apply { topMargin = dp(20) }
        })
        page.addView(label(tr("Los botones de volumen pasan página con la pantalla apagada.", "The volume buttons turn pages with the screen off."), 13f).apply {
            gravity = Gravity.CENTER
            typeface = Typeface.create("serif", Typeface.ITALIC)
            setTextColor(color(mutedColor))
            setPadding(dp(8), dp(12), dp(8), 0)
        })

        return ScrollView(this).apply {
            isFillViewport = true
            setBackgroundColor(color(bgColor))
            addView(page)
        }
    }

    private fun remoteButton(symbol: String, title: String, action: String, primary: Boolean = false, compact: Boolean = false): View =
        vertical().apply {
            val filled = primary && !dark
            val textColor = if (filled) "#FFFFFF" else fgColor
            gravity = Gravity.CENTER
            isClickable = true
            isFocusable = true
            background = when {
                filled -> rounded("#000000", 3)
                primary -> outlined(bgColor, fgColor, 3, 3)
                else -> outlined(bgColor, fgColor, if (dark) 1 else 2, 3)
            }
            contentDescription = title
            addView(label(symbol, if (compact) 34f else 64f, bold = false).apply {
                gravity = Gravity.CENTER
                setTextColor(color(textColor))
            }, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(if (compact) 42 else 88)))
            addView(label(title, if (compact) 15f else 20f, bold = true).apply {
                gravity = Gravity.CENTER
                typeface = Typeface.create("serif", Typeface.BOLD)
                setTextColor(color(textColor))
            })
            setOnClickListener {
                performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                send(action)
            }
        }

    private fun showSettings() {
        val dialog = Dialog(this)
        val themeTitle = tr("Tema", "Theme")
        val languageTitle = tr("Idioma", "Language")
        val dayName = tr("Día", "Day")
        val darkName = tr("Oscuro", "Dark")
        val currentThemeName = if (dark) darkName else dayName
        val currentLanguageName = if (isEnglish()) "English" else "Español"
        val content = vertical().apply {
            setPadding(dp(22), dp(20), dp(22), dp(20))
            background = outlined(bgColor, lineColor, 1, 2)
            addView(horizontal().apply {
                gravity = Gravity.CENTER_VERTICAL
                addView(label(tr("Ajustes", "Settings"), 26f, bold = true).apply {
                    typeface = Typeface.create("serif", Typeface.BOLD)
                }, LinearLayout.LayoutParams(0, dp(48), 1f))
                addView(actionButton(tr("Listo", "Done"), secondary = true) { dialog.dismiss() }.apply {
                    layoutParams = LinearLayout.LayoutParams(dp(86), dp(40))
                })
            })
            addView(divider().apply { layoutParams = matchHeight(dp(1)).apply { bottomMargin = dp(18) } })
            addView(sectionLabel(tr("CONEXIÓN", "CONNECTION")))
            addView(actionButton(tr("Cambiar dispositivo", "Change device")) { showDevicePicker() })
            addView(actionButton(tr("Ajustes de Bluetooth", "Bluetooth settings"), secondary = true) {
                startActivity(Intent(Settings.ACTION_BLUETOOTH_SETTINGS))
            }.apply { layoutParams = matchHeight(dp(52)).apply { topMargin = dp(8) } })
            addView(divider().apply { layoutParams = matchHeight(dp(1)).apply { topMargin = dp(22); bottomMargin = dp(18) } })
            addView(sectionLabel(tr("APARIENCIA", "APPEARANCE")))
            addView(actionButton("$themeTitle: $currentThemeName  ▾", secondary = true) {
                showChoiceDialog(
                    title = themeTitle,
                    choices = listOf(dayName, darkName),
                    selectedIndex = if (dark) 1 else 0
                ) { index ->
                    Prefs.setDarkMode(this@MainActivity, index == 1)
                    dialog.dismiss()
                    this@MainActivity.recreate()
                }
            }.apply { layoutParams = matchHeight(dp(48)) })
            addView(actionButton("$languageTitle: $currentLanguageName  ▾", secondary = true) {
                showChoiceDialog(
                    title = languageTitle,
                    choices = listOf("Español", "English"),
                    selectedIndex = if (isEnglish()) 1 else 0
                ) { index ->
                    Prefs.setLanguage(this@MainActivity, if (index == 1) Prefs.LANG_EN else Prefs.LANG_ES)
                    dialog.dismiss()
                    this@MainActivity.recreate()
                }
            }.apply { layoutParams = matchHeight(dp(48)).apply { topMargin = dp(8) } })
            addView(divider().apply { layoutParams = matchHeight(dp(1)).apply { topMargin = dp(22); bottomMargin = dp(18) } })
            addView(sectionLabel(tr("ACCIÓN DE LOS BOTONES DE VOLUMEN", "VOLUME BUTTON ACTIONS")))
            addView(mappingSpinner(tr("Subir volumen", "Volume up"), true))
            addView(mappingSpinner(tr("Bajar volumen", "Volume down"), false))
            addView(CheckBox(this@MainActivity).apply {
                text = tr("Iniciar el control al reiniciar el teléfono", "Start the remote after phone restart")
                textSize = 14f
                setTextColor(color(fgColor))
                buttonTintList = ColorStateList.valueOf(color(fgColor))
                isChecked = Prefs.startOnBoot(this@MainActivity)
                setOnCheckedChangeListener { _, checked -> Prefs.setStartOnBoot(this@MainActivity, checked) }
                setPadding(0, dp(12), 0, dp(6))
            })
            addView(divider().apply { layoutParams = matchHeight(dp(1)).apply { topMargin = dp(12); bottomMargin = dp(14) } })
            addView(actionButton(tr("Desconectar y detener el control", "Disconnect and stop the remote"), secondary = true) {
                confirmStopRemote()
            }.apply { layoutParams = matchHeight(dp(52)) })
        }
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE)
        dialog.setContentView(ScrollView(this).apply {
            setBackgroundColor(Color.TRANSPARENT)
            addView(content)
        })
        dialog.window?.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
        dialog.show()
        dialog.window?.setLayout((resources.displayMetrics.widthPixels * .92f).toInt(), LinearLayout.LayoutParams.WRAP_CONTENT)
    }

    private fun confirmStopRemote() {
        showConfirmationDialog(
            title = tr("¿Desconectar el control?", "Disconnect the remote?"),
            message = tr(
                "Los botones de página y de volumen dejarán de funcionar hasta que vuelvas a conectar.",
                "The page and volume buttons will stop working until you reconnect."
            ),
            confirmLabel = tr("Desconectar", "Disconnect")
        ) {
            send(HidRemoteService.ACTION_STOP)
        }
    }

    private fun mappingSpinner(label: String, isUp: Boolean): View = vertical().apply {
        addView(label(label, 13f, bold = true).apply { setPadding(dp(2), dp(7), 0, dp(2)) })
        val commands = listOf(RemoteCommand.DPAD_UP, RemoteCommand.DPAD_DOWN, RemoteCommand.ENTER, RemoteCommand.BACK)
        val selected = if (isUp) Prefs.volumeUpCommand(this@MainActivity) else Prefs.volumeDownCommand(this@MainActivity)
        lateinit var pickerButton: Button
        pickerButton = actionButton("${friendlyCommand(selected)}  ▾", secondary = true) {
            val selectedCommand = if (isUp) Prefs.volumeUpCommand(this@MainActivity)
            else Prefs.volumeDownCommand(this@MainActivity)
            showChoiceDialog(
                title = tr("Acción: $label", "Action: $label"),
                choices = commands.map(::friendlyCommand),
                selectedIndex = commands.indexOf(selectedCommand)
            ) { index ->
                val command = commands[index]
                if (isUp) Prefs.setVolumeUpCommand(this@MainActivity, command)
                else Prefs.setVolumeDownCommand(this@MainActivity, command)
                pickerButton.text = "${friendlyCommand(command)}  ▾"
            }
        }
        addView(pickerButton, matchHeight(dp(48)))
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
            showConfirmationDialog(
                title = tr("No hay dispositivos emparejados", "No paired devices"),
                message = tr(
                    "Empareja tu lector en los ajustes de Bluetooth de Android y vuelve a Komobo.",
                    "Pair your e-reader in Android Bluetooth settings, then return to Komobo."
                ),
                confirmLabel = tr("Ajustes de Bluetooth", "Bluetooth settings")
            ) {
                startActivity(Intent(Settings.ACTION_BLUETOOTH_SETTINGS))
            }
            return
        }
        val selectedAddress = Prefs.hostAddress(this)
        showChoiceDialog(
            title = tr("Cambiar dispositivo", "Change device"),
            choices = devices.map(::safeName),
            selectedIndex = devices.indexOfFirst { it.address == selectedAddress }
        ) { index ->
            Prefs.setHostAddress(this, devices[index].address)
            updateConnectionCard()
            send(HidRemoteService.ACTION_START)
            send(HidRemoteService.ACTION_CONNECT_SELECTED)
        }
    }

    private fun showChoiceDialog(
        title: String,
        choices: List<String>,
        selectedIndex: Int,
        onSelected: (Int) -> Unit
    ) {
        val dialog = Dialog(this)
        val content = dialogContent(dialog, title)
        choices.forEachIndexed { index, choice ->
            val selected = index == selectedIndex
            content.addView(actionButton(if (selected) "✓  $choice" else choice, secondary = !selected) {
                dialog.dismiss()
                onSelected(index)
            }.apply {
                gravity = Gravity.START or Gravity.CENTER_VERTICAL
                setPadding(dp(18), 0, dp(14), 0)
                layoutParams = matchHeight(dp(52)).apply { topMargin = dp(8) }
            })
        }
        showKomoboDialog(dialog, content)
    }

    private fun showConfirmationDialog(
        title: String,
        message: String,
        confirmLabel: String,
        onConfirm: () -> Unit
    ) {
        val dialog = Dialog(this)
        val content = dialogContent(dialog, title).apply {
            addView(label(message, 15f).apply {
                setTextColor(color(fgColor))
                setPadding(dp(2), dp(16), dp(2), dp(20))
            })
            addView(horizontal().apply {
                addView(actionButton(tr("Cancelar", "Cancel"), secondary = true) { dialog.dismiss() }, weighted(1f, dp(50)).apply {
                    rightMargin = dp(5)
                })
                addView(actionButton(confirmLabel) {
                    dialog.dismiss()
                    onConfirm()
                }, weighted(1f, dp(50)).apply {
                    leftMargin = dp(5)
                })
            })
        }
        showKomoboDialog(dialog, content)
    }

    private fun dialogContent(dialog: Dialog, title: String): LinearLayout = vertical().apply {
        setPadding(dp(22), dp(20), dp(22), dp(22))
        background = outlined(bgColor, lineColor, 1, 2)
        addView(horizontal().apply {
            gravity = Gravity.CENTER_VERTICAL
            addView(label(title, 21f, bold = true).apply {
                typeface = Typeface.create("serif", Typeface.BOLD)
                maxLines = 2
                setPadding(0, dp(6), dp(10), dp(6))
                minimumHeight = dp(48)
                gravity = Gravity.CENTER_VERTICAL
            }, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
            addView(
                actionButton(tr("Cerrar", "Close"), secondary = true) { dialog.dismiss() },
                LinearLayout.LayoutParams(dp(86), dp(40))
            )
        })
        addView(divider().apply {
            layoutParams = matchHeight(dp(1)).apply { bottomMargin = dp(4) }
        })
    }

    private fun showKomoboDialog(dialog: Dialog, content: View) {
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE)
        dialog.setContentView(ScrollView(this).apply {
            setBackgroundColor(Color.TRANSPARENT)
            addView(content)
        })
        dialog.window?.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
        dialog.show()
        dialog.window?.setLayout(
            (resources.displayMetrics.widthPixels * .92f).toInt(),
            LinearLayout.LayoutParams.WRAP_CONTENT
        )
    }

    private fun updateConnectionCard() {
        if (!::statusText.isInitialized) return
        val status = HidRemoteService.status
        val connected = status == HidRemoteService.Status.CONNECTED
        statusText.text = Texts.statusText(this, status)
        statusDot.text = if (connected) "●" else "○"
        statusDot.setTextColor(color(fgColor))
        val address = Prefs.hostAddress(this)
        deviceText.text = selectedDeviceName(address)
            ?: if (address == null) tr("Elige tu Kobo en Ajustes", "Choose your Kobo in Settings") else address
    }

    private fun selectedDeviceName(address: String?): String? {
        if (address == null || !hasBtConnectPermission()) return null
        return (getSystemService(BLUETOOTH_SERVICE) as BluetoothManager).adapter?.bondedDevices
            ?.firstOrNull { it.address == address }?.let(::safeName)
    }

    private fun send(action: String) {
        if (!hasBtConnectPermission()) {
            requestRuntimePermissions()
            return
        }
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
        if (requestCode == 100 && hasBtConnectPermission()) {
            if (requestBluetoothEnableIfNeeded()) send(HidRemoteService.ACTION_START)
        }
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

    private fun isEnglish(): Boolean = Prefs.language(this) == Prefs.LANG_EN

    private fun tr(es: String, en: String): String = Texts.tr(this, es, en)

    private fun friendlyCommand(command: RemoteCommand): String = when (command) {
        RemoteCommand.DPAD_UP, RemoteCommand.DPAD_LEFT -> tr("Página anterior", "Previous page")
        RemoteCommand.DPAD_DOWN, RemoteCommand.DPAD_RIGHT -> tr("Página siguiente", "Next page")
        RemoteCommand.ENTER -> tr("Seleccionar / OK", "Select / OK")
        RemoteCommand.BACK -> tr("Bloquear / desbloquear Kobo", "Lock / unlock Kobo")
        else -> command.label
    }

    private fun sectionLabel(value: String) = label(value, 11f, bold = true).apply {
        letterSpacing = .1f
        setTextColor(color(mutedColor))
        setPadding(dp(2), 0, 0, dp(10))
    }

    private fun vertical() = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
    private fun horizontal() = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
    private fun label(value: String, size: Float, bold: Boolean = false) = TextView(this).apply {
        text = value
        textSize = size
        setTextColor(color(fgColor))
        if (bold) setTypeface(typeface, Typeface.BOLD)
    }
  
    private fun actionButton(value: String, secondary: Boolean = false, action: () -> Unit) = Button(this).apply {
        val filled = !secondary && !dark
        text = value
        isAllCaps = false
        setTextColor(color(if (filled) "#FFFFFF" else fgColor))
        background = if (filled) rounded("#000000", 2) else outlined(bgColor, fgColor, if (secondary) 1 else 2, 2)
        setOnClickListener { action() }
    }
    private fun divider() = View(this).apply { setBackgroundColor(color(lineColor)); layoutParams = matchHeight(dp(1)) }
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
