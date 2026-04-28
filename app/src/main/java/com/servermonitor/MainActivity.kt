package com.servermonitor

import android.app.AlertDialog
import android.content.Intent
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout
import kotlin.concurrent.thread

class MainActivity : AppCompatActivity() {

    private lateinit var tabDevices: TextView
    private lateinit var tabNetworks: TextView
    private lateinit var contentFrame: FrameLayout
    private var currentTab = "devices"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.statusBarColor = Color.parseColor("#0A0A0F")
        window.navigationBarColor = Color.parseColor("#0A0A0F")

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.parseColor("#0A0A0F"))
        }

        // ── Header ────────────────────────────────────────────────────────────
        val header = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.parseColor("#0A0A0F"))
            setPadding(48, 72, 48, 0)
        }
        header.addView(TextView(this).apply {
            text = "SERVER SWITCH"
            textSize = 26f
            setTextColor(Color.parseColor("#F0F0F5"))
            typeface = Typeface.DEFAULT_BOLD
            letterSpacing = 0.1f
        })

        // ── Tabs ──────────────────────────────────────────────────────────────
        val tabRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(48, 24, 48, 0)
        }

        tabDevices = TextView(this).apply {
            text = "DEVICES"
            textSize = 12f
            letterSpacing = 0.2f
            setPadding(0, 16, 48, 16)
            isClickable = true; isFocusable = true
            setOnClickListener { switchTab("devices") }
        }
        tabNetworks = TextView(this).apply {
            text = "NETWORKS"
            textSize = 12f
            letterSpacing = 0.2f
            setPadding(0, 16, 48, 16)
            isClickable = true; isFocusable = true
            setOnClickListener { switchTab("networks") }
        }
        tabRow.addView(tabDevices)
        tabRow.addView(tabNetworks)

        // Tab underline
        val tabLine = View(this).apply {
            setBackgroundColor(Color.parseColor("#1A1A2E"))
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 1).apply {
                leftMargin = 48; rightMargin = 48
            }
        }

        contentFrame = FrameLayout(this).apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f
            )
        }

        root.addView(header)
        root.addView(tabRow)
        root.addView(tabLine)
        root.addView(contentFrame)
        setContentView(root)

        switchTab("devices")
    }

    override fun onResume() {
        super.onResume()
        switchTab(currentTab)
    }

    private fun switchTab(tab: String) {
        currentTab = tab
        val activeColor = Color.parseColor("#7B7BFF")
        val inactiveColor = Color.parseColor("#444455")
        tabDevices.setTextColor(if (tab == "devices") activeColor else inactiveColor)
        tabNetworks.setTextColor(if (tab == "networks") activeColor else inactiveColor)
        contentFrame.removeAllViews()
        contentFrame.addView(if (tab == "devices") buildDevicesTab() else buildNetworksTab())
    }

    // ── DEVICES TAB ───────────────────────────────────────────────────────────
    private fun buildDevicesTab(): View {
        val swipe = SwipeRefreshLayout(this).apply {
            setColorSchemeColors(Color.parseColor("#7B7BFF"))
            setBackgroundColor(Color.parseColor("#0A0A0F"))
        }
        val scroll = ScrollView(this).apply { setBackgroundColor(Color.parseColor("#0A0A0F")) }
        val container = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(48, 32, 48, 80)
        }

        val devices = DeviceStore.getDevices(this)
        val aods = DeviceStore.getAods(this)

        if (devices.isEmpty()) {
            container.addView(TextView(this).apply {
                text = "No devices yet.\nTap + ADD DEVICE below."
                textSize = 14f; setTextColor(Color.parseColor("#333344"))
                gravity = Gravity.CENTER; setPadding(0, 64, 0, 0)
            })
        } else {
            devices.forEach { device ->
                val aod = aods.firstOrNull { it.deviceIds.contains(device.id) }
                container.addView(makeDeviceCard(device, aod))
            }
        }

        val addBtn = TextView(this).apply {
            text = "+ ADD DEVICE"
            textSize = 14f
            setTextColor(Color.parseColor("#7B7BFF"))
            gravity = Gravity.CENTER
            setPadding(0, 48, 0, 0)
            isClickable = true
            isFocusable = true
            setOnClickListener {
                startActivity(Intent(this@MainActivity, AddEditDeviceActivity::class.java))
            }
        }
        container.addView(addBtn)

        swipe.setOnRefreshListener {
            thread {
                devices.forEach { d ->
                    val s = Network.pollDevice(d)
                    DeviceStore.updateDeviceStatus(this, d.id, s)
                }
                aods.forEach { a ->
                    val s = Network.pollAod(a)
                    DeviceStore.updateAodStatus(this, a.id, s)
                }
                runOnUiThread { swipe.isRefreshing = false; switchTab(currentTab) }
            }
        }

        scroll.addView(container)
        swipe.addView(scroll)

//        // Auto-poll on load
//        thread {
//            devices.forEach { d -> DeviceStore.updateDeviceStatus(this, d.id, Network.pollDevice(d)) }
//            aods.forEach { a -> DeviceStore.updateAodStatus(this, a.id, Network.pollAod(a)) }
//            runOnUiThread {
//                if (currentTab == "devices") switchTab("devices")
//            }
//        }

        return swipe
    }

    private fun makeDeviceCard(device: Device, aod: Aod?): View {
        val card = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.parseColor("#0F0F1A"))
            setPadding(40, 36, 40, 36)
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { bottomMargin = 20 }
        }

        val dotColor = when (device.lastStatus) {
            "on" -> "#1DB954"; "off" -> "#E53935"; else -> "#444455"
        }

        // Top row
        val topRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL
        }
        topRow.addView(View(this).apply {
            setBackgroundColor(Color.parseColor(dotColor))
            layoutParams = LinearLayout.LayoutParams(20, 20).apply { rightMargin = 20 }
        })
        topRow.addView(TextView(this).apply {
            text = device.name; textSize = 18f
            setTextColor(Color.parseColor("#F0F0F5")); typeface = Typeface.DEFAULT_BOLD
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        })
        topRow.addView(TextView(this).apply {
            text = when (device.lastStatus) { "on" -> "ONLINE"; "off" -> "OFFLINE"; else -> "UNKNOWN" }
            textSize = 11f; setTextColor(Color.parseColor(dotColor)); letterSpacing = 0.15f
        })
        card.addView(topRow)

        card.addView(TextView(this).apply {
            text = "${device.ip}:${device.port}"
            textSize = 12f; setTextColor(Color.parseColor("#2A2A3E")); setPadding(40, 10, 0, 4)
        })

        // AOD badge
        if (aod != null) {
            val aodColor = if (aod.lastStatus == "on") "#7B7BFF" else "#333344"
            card.addView(TextView(this).apply {
                text = "via ${aod.name}"
                textSize = 11f; setTextColor(Color.parseColor(aodColor)); setPadding(40, 2, 0, 24)
            })
        } else {
            card.addView(View(this).apply { layoutParams = LinearLayout.LayoutParams(0, 24) })
        }

        // Buttons
        val btnRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL; setPadding(40, 0, 0, 0)
        }

        // Power ON button — only if has AOD
        if (aod != null) {
            val aodOnline = aod.lastStatus == "on"
            val deviceOff = device.lastStatus != "on"
            val canWake = aodOnline && deviceOff
            btnRow.addView(makeBtn("POWER ON", if (canWake) "#1DB954" else "#2A2A3E") {
                if (!aodOnline) {
                    Toast.makeText(this, "${aod.name} is offline", Toast.LENGTH_SHORT).show()
                    return@makeBtn
                }
                if (!deviceOff) {
                    Toast.makeText(this, "${device.name} is already online", Toast.LENGTH_SHORT).show()
                    return@makeBtn
                }
                showWakeDialog(device, aod)
            })
        }

        btnRow.addView(makeBtn("SHUTDOWN", "#E53935") {
            if (device.lastStatus != "on") {
                Toast.makeText(this, "${device.name} is already offline", Toast.LENGTH_SHORT).show()
                return@makeBtn
            }
            AlertDialog.Builder(this).setTitle("Shut down ${device.name}?")
                .setMessage("The server will power off.")
                .setPositiveButton("Shut down") { _, _ ->
                    thread {
                        Network.sendShutdown(device)
                        // Wait for server to go down
                        Thread.sleep(5000)
                        DeviceStore.updateDeviceStatus(this, device.id, Network.pollDevice(device))
                        // Run post-shutdown script if configured
                        if (device.postShutdownScript.isNotEmpty()) {
                            val postAod = DeviceStore.getAodForDevice(this, device.id)
                            if (postAod != null) {
                                val remainingDelay = (device.postShutdownDelaySeconds * 1000L) - 5000L
                                if (remainingDelay > 0) Thread.sleep(remainingDelay)
                                val scriptArgs = device.postShutdownScriptArgs.split("\\s+".toRegex()).filter { it.isNotEmpty() }
                                Network.wakeViaScript(postAod, device.postShutdownScript, scriptArgs)
                                android.util.Log.d("ServerSwitch", "Post-shutdown script '${device.postShutdownScript}' ran for ${device.name} with ${scriptArgs.size} args")
                            }
                        }
                        DeviceStore.updateDeviceStatus(this, device.id, Network.pollDevice(device))
                        runOnUiThread { switchTab("devices") }
                    }
                }.setNegativeButton("Cancel", null).show()
        })

        btnRow.addView(makeBtn("REBOOT", "#FF9800") {
            if (device.lastStatus != "on") { Toast.makeText(this, "Offline", Toast.LENGTH_SHORT).show(); return@makeBtn }
            AlertDialog.Builder(this).setTitle("Reboot ${device.name}?")
                .setPositiveButton("Reboot") { _, _ ->
                    thread {
                        Network.sendReboot(device); Thread.sleep(2000)
                        DeviceStore.updateDeviceStatus(this, device.id, "off")
                        runOnUiThread { switchTab("devices") }
                    }
                }.setNegativeButton("Cancel", null).show()
        })

        btnRow.addView(makeBtn("INFO", "#7B7BFF") {
            if (device.lastStatus != "on") { Toast.makeText(this, "Offline", Toast.LENGTH_SHORT).show(); return@makeBtn }
            thread {
                val info = Network.getInfo(device)
                runOnUiThread {
                    if (info == null) { Toast.makeText(this, "Could not fetch info", Toast.LENGTH_SHORT).show(); return@runOnUiThread }
                    AlertDialog.Builder(this).setTitle("${device.name} — Info")
                        .setMessage(
                            "CPU:    ${info.cpuPercent}%\n" +
                                    "RAM:    ${info.ramPercent}% (${info.ramUsedGb}/${info.ramTotalGb} GB)\n" +
                                    "Disk:   ${info.diskPercent}% (${info.diskUsedGb}/${info.diskTotalGb} GB)\n" +
                                    "Uptime: ${formatUptime(info.uptimeSeconds)}"
                        ).setPositiveButton("OK", null).show()
                }
            }
        })

        val screenLogsBtn = makeBtn("SCREEN LOGS", "#7B7BFF") {
            if (device.lastStatus != "on") { Toast.makeText(this, "Offline", Toast.LENGTH_SHORT).show(); return@makeBtn }
            showScreenSelector(device, screenLogsBtn)
        }
        btnRow.addView(screenLogsBtn)

        btnRow.addView(makeBtn("EDIT", "#555566") {
            startActivity(Intent(this, AddEditDeviceActivity::class.java).apply { putExtra("device_id", device.id) })
        })

        card.addView(btnRow)
        return card
    }

    private fun showWakeDialog(device: Device, aod: Aod) {
        var scripts = emptyList<String>()
        thread {
            scripts = Network.listScripts(aod)
            val items = (listOf("Wake-on-LAN (MAC: ${if (device.mac.isNotEmpty()) device.mac else "not set"})") +
                    scripts.map { "Script: $it" }).toTypedArray()
            runOnUiThread {
                AlertDialog.Builder(this)
                    .setTitle("Power on ${device.name}")
                    .setItems(items) { _, which ->
                        if (which == 0) {
                            showWolDialog(device, aod)
                        } else {
                            val scriptName = scripts[which - 1]
                            thread {
                                val ok = Network.wakeViaScript(aod, scriptName)
                                runOnUiThread {
                                    Toast.makeText(this, if (ok) "Wake script sent!" else "Script failed", Toast.LENGTH_SHORT).show()
                                }
                            }
                        }
                    }.show()
            }
        }
    }

    private fun showWolDialog(device: Device, aod: Aod) {
        if (device.mac.isEmpty()) {
            AlertDialog.Builder(this)
                .setTitle("No MAC address saved")
                .setMessage("To use Wake-on-LAN, edit ${device.name} and enter its MAC address first.")
                .setPositiveButton("Edit device") { _, _ ->
                    startActivity(Intent(this, AddEditDeviceActivity::class.java).apply {
                        putExtra("device_id", device.id)
                    })
                }
                .setNegativeButton("Cancel", null)
                .show()
            return
        }
        AlertDialog.Builder(this)
            .setTitle("Wake ${device.name} via WoL?")
            .setMessage("Sending magic packet to MAC: ${device.mac}")
            .setPositiveButton("Wake") { _, _ ->
                thread {
                    val ok = Network.wakeViaWol(aod, device.mac)
                    runOnUiThread {
                        Toast.makeText(this, if (ok) "WoL sent to ${device.mac}!" else "WoL failed", Toast.LENGTH_SHORT).show()
                    }
                }
            }.setNegativeButton("Cancel", null).show()
    }

    // ── NETWORKS TAB ──────────────────────────────────────────────────────────
    private fun buildNetworksTab(): View {
        val swipe = SwipeRefreshLayout(this).apply {
            setColorSchemeColors(Color.parseColor("#7B7BFF"))
            setBackgroundColor(Color.parseColor("#0A0A0F"))
        }
        val scroll = ScrollView(this).apply { setBackgroundColor(Color.parseColor("#0A0A0F")) }
        val container = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL; setPadding(48, 32, 48, 80)
        }

        val aods = DeviceStore.getAods(this)

        if (aods.isEmpty()) {
            container.addView(TextView(this).apply {
                text = "No always-on devices yet.\nTap + ADD NETWORK below."
                textSize = 14f; setTextColor(Color.parseColor("#333344"))
                gravity = Gravity.CENTER; setPadding(0, 64, 0, 0)
            })
        } else {
            aods.forEach { container.addView(makeAodCard(it)) }
        }

        container.addView(TextView(this).apply {
            text = "+ ADD NETWORK"
            textSize = 14f; setTextColor(Color.parseColor("#7B7BFF"))
            gravity = Gravity.CENTER; setPadding(0, 48, 0, 0)
            isClickable = true; isFocusable = true
            setOnClickListener { startActivity(Intent(this@MainActivity, AddEditAodActivity::class.java)) }
        })

        swipe.setOnRefreshListener {
            thread {
                aods.forEach { a ->
                    val s = Network.pollAod(a)
                    DeviceStore.updateAodStatus(this, a.id, s)
                }
                runOnUiThread { swipe.isRefreshing = false; switchTab(currentTab) }
            }
        }

        scroll.addView(container)
        swipe.addView(scroll)
        return swipe
    }

    private fun makeAodCard(aod: Aod): View {
        val card = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.parseColor("#0F0F1A"))
            setPadding(40, 36, 40, 36)
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { bottomMargin = 20 }
        }

        val dotColor = when (aod.lastStatus) { "on" -> "#7B7BFF"; "off" -> "#E53935"; else -> "#444455" }

        val topRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL
        }
        topRow.addView(View(this).apply {
            setBackgroundColor(Color.parseColor(dotColor))
            layoutParams = LinearLayout.LayoutParams(20, 20).apply { rightMargin = 20 }
        })
        topRow.addView(TextView(this).apply {
            text = aod.name; textSize = 18f
            setTextColor(Color.parseColor("#F0F0F5")); typeface = Typeface.DEFAULT_BOLD
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        })
        topRow.addView(TextView(this).apply {
            text = when (aod.lastStatus) { "on" -> "ONLINE"; "off" -> "OFFLINE"; else -> "UNKNOWN" }
            textSize = 11f; setTextColor(Color.parseColor(dotColor)); letterSpacing = 0.15f
        })
        card.addView(topRow)

        card.addView(TextView(this).apply {
            text = "${aod.ip}:${aod.port}"
            textSize = 12f; setTextColor(Color.parseColor("#2A2A3E")); setPadding(40, 10, 0, 4)
        })

        // Devices in this network
        val allDevices = DeviceStore.getDevices(this)
        val linkedDevices = allDevices.filter { aod.deviceIds.contains(it.id) }
        val deviceNames = if (linkedDevices.isEmpty()) "No devices assigned"
        else linkedDevices.joinToString(", ") { it.name }
        card.addView(TextView(this).apply {
            text = deviceNames
            textSize = 11f; setTextColor(Color.parseColor("#444455")); setPadding(40, 4, 0, 24)
        })

        val btnRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL; setPadding(40, 0, 0, 0)
        }
        btnRow.addView(makeBtn("EDIT", "#7B7BFF") {
            startActivity(Intent(this, AddEditAodActivity::class.java).apply { putExtra("aod_id", aod.id) })
        })
        btnRow.addView(makeBtn("DELETE", "#2A2A3E") {
            AlertDialog.Builder(this).setTitle("Remove ${aod.name}?")
                .setPositiveButton("Remove") { _, _ ->
                    val aods = DeviceStore.getAods(this).filter { it.id != aod.id }
                    DeviceStore.saveAods(this, aods)
                    switchTab("networks")
                }.setNegativeButton("Cancel", null).show()
        })
        card.addView(btnRow)
        return card
    }

    private fun makeBtn(label: String, color: String, onClick: () -> Unit): TextView {
        return TextView(this).apply {
            text = label; textSize = 12f; letterSpacing = 0.08f
            setTextColor(Color.WHITE)
            gravity = Gravity.CENTER
            setPadding(30, 18, 30, 18)
            background = GradientDrawable().apply {
                setColor(Color.parseColor(color))
                cornerRadius = 32f
            }
            isClickable = true; isFocusable = true
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply {
                rightMargin = 12
            }
            setOnClickListener { onClick() }
        }
    }

    private fun showScreenSelector(device: Device, button: TextView) {
        val originalText = button.text.toString()
        button.isEnabled = false
        button.alpha = 0.6f
        button.text = "Loading..."

        thread {
            val screens = Network.listScreens(device)
            runOnUiThread {
                button.isEnabled = true
                button.alpha = 1f
                button.text = originalText

                if (screens.isEmpty()) {
                    showScreenNameDialog(device, "", button)
                } else {
                    val items = screens.map { it.substringAfter('.', it) }.toTypedArray()
                    AlertDialog.Builder(this)
                        .setTitle("Select an active screen")
                        .setItems(items) { _, which -> showScreenLog(device, screens[which], button) }
                        .setPositiveButton("Enter name") { _, _ -> showScreenNameDialog(device, "", button) }
                        .setNegativeButton("Cancel", null)
                        .show()
                }
            }
        }
    }

    private fun showScreenNameDialog(device: Device, prefill: String, button: TextView?) {
        val input = EditText(this).apply {
            setText(prefill)
            hint = "screen name"
            setTextColor(Color.parseColor("#F0F0F5"))
        }
        AlertDialog.Builder(this)
            .setTitle("Enter screen name")
            .setView(input)
            .setPositiveButton("Show logs") { _, _ ->
                val screenName = input.text.toString().trim()
                if (screenName.isEmpty()) {
                    Toast.makeText(this, "Enter a screen name", Toast.LENGTH_SHORT).show()
                    return@setPositiveButton
                }
                showScreenLog(device, screenName, button)
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun showScreenLog(device: Device, screenName: String, button: TextView?) {
        val originalText = button?.text?.toString()
        button?.apply {
            isEnabled = false
            alpha = 0.6f
            text = "Loading logs..."
        }

        thread {
            val logText = Network.getScreenLog(device, screenName)
            runOnUiThread {
                button?.apply {
                    isEnabled = true
                    alpha = 1f
                    text = originalText ?: text
                }
                if (logText == null) {
                    Toast.makeText(this, "Screen not found or log unavailable", Toast.LENGTH_LONG).show()
                    return@runOnUiThread
                }
                val logView = TextView(this).apply {
                    text = logText
                    textSize = 12f
                    setTextColor(Color.parseColor("#F0F0F5"))
                    setPadding(24, 24, 24, 24)
                    setTextIsSelectable(true)
                }
                val scroll = ScrollView(this).apply { addView(logView) }
                AlertDialog.Builder(this)
                    .setTitle("Logs: $screenName")
                    .setView(scroll)
                    .setPositiveButton("Close", null)
                    .show()
            }
        }
    }

    private fun formatUptime(s: Long): String {
        val d = s / 86400; val h = (s % 86400) / 3600; val m = (s % 3600) / 60
        return buildString { if (d > 0) append("${d}d "); if (h > 0) append("${h}h "); append("${m}m") }
    }
}