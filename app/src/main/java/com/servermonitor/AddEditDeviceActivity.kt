package com.servermonitor

import android.graphics.Color
import android.graphics.Typeface
import android.os.Bundle
import android.view.Gravity
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import kotlin.concurrent.thread

class AddEditDeviceActivity : AppCompatActivity() {

    private var editingId: String? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.statusBarColor = Color.parseColor("#0A0A0F")
        window.navigationBarColor = Color.parseColor("#0A0A0F")

        editingId = intent.getStringExtra("device_id")
        val existing = editingId?.let { id -> DeviceStore.getDevices(this).find { it.id == id } }

        val scroll = ScrollView(this).apply { setBackgroundColor(Color.parseColor("#0A0A0F")) }
        val container = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL; setPadding(48, 80, 48, 80)
        }

        container.addView(TextView(this).apply {
            text = if (existing != null) "EDIT DEVICE" else "ADD DEVICE"
            textSize = 24f; setTextColor(Color.parseColor("#F0F0F5"))
            typeface = Typeface.DEFAULT_BOLD; letterSpacing = 0.1f; setPadding(0, 0, 0, 56)
        })

        fun addLabel(text: String) = container.addView(TextView(this).apply {
            this.text = text.uppercase(); textSize = 10f
            setTextColor(Color.parseColor("#444455")); letterSpacing = 0.2f; setPadding(0, 0, 0, 8)
        })

        fun addField(hint: String, value: String = "", numeric: Boolean = false, password: Boolean = false): EditText {
            return EditText(this).apply {
                this.hint = hint; setText(value)
                setHintTextColor(Color.parseColor("#2A2A3E"))
                setTextColor(Color.parseColor("#F0F0F5"))
                setBackgroundColor(Color.parseColor("#0F0F1A"))
                setPadding(24, 24, 24, 24)
                inputType = when {
                    password -> android.text.InputType.TYPE_CLASS_TEXT or android.text.InputType.TYPE_TEXT_VARIATION_PASSWORD
                    numeric -> android.text.InputType.TYPE_CLASS_NUMBER
                    else -> android.text.InputType.TYPE_CLASS_TEXT
                }
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT
                ).apply { bottomMargin = 36 }
            }.also { container.addView(it) }
        }

        addLabel("Device name")
        val nameField = addField("e.g. JenuServer", existing?.name ?: "")
        addLabel("IP address (Tailscale)")
        val ipField = addField("e.g. 100.80.46.6", existing?.ip ?: "")
        addLabel("Port")
        val portField = addField("5050", (existing?.port ?: 5050).toString(), numeric = true)
        addLabel("Auth token")
        val tokenField = addField("Token from server install", existing?.token ?: "", password = true)

        // ── Post-shutdown script section ──────────────────────────────────────
        container.addView(View(this).apply {
            setBackgroundColor(Color.parseColor("#1A1A2E"))
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 1).apply {
                topMargin = 16; bottomMargin = 32
            }
        })

        container.addView(TextView(this).apply {
            text = "POST-SHUTDOWN SCRIPT (OPTIONAL)"
            textSize = 10f; setTextColor(Color.parseColor("#444455"))
            letterSpacing = 0.2f; setPadding(0, 0, 0, 8)
        })
        container.addView(TextView(this).apply {
            text = "Run a script on the always-on device after this server shuts down. " +
                   "Useful for things like cutting smart plug power after shutdown completes."
            textSize = 12f; setTextColor(Color.parseColor("#333344")); setPadding(0, 0, 0, 24)
        })

        // Find which AOD this device belongs to, and offer to load its scripts
        val aod = DeviceStore.getAodForDevice(this, existing?.id ?: "")

        addLabel("Script name (from always-on device)")
        val scriptField = addField(
            if (aod != null) "e.g. cut_power (tap LOAD to see options)" else "Add this device to a network first",
            existing?.postShutdownScript ?: ""
        )

        // Load scripts button (only if device has an AOD)
        if (aod != null) {
            container.addView(TextView(this).apply {
                text = "LOAD SCRIPTS FROM ${aod.name.uppercase()}"
                textSize = 11f; setTextColor(Color.parseColor("#7B7BFF"))
                setPadding(0, 0, 0, 24)
                isClickable = true; isFocusable = true
                setOnClickListener {
                    thread {
                        val scripts = Network.listScripts(aod)
                        runOnUiThread {
                            if (scripts.isEmpty()) {
                                Toast.makeText(this@AddEditDeviceActivity,
                                    "No scripts found on ${aod.name}", Toast.LENGTH_SHORT).show()
                                return@runOnUiThread
                            }
                            android.app.AlertDialog.Builder(this@AddEditDeviceActivity)
                                .setTitle("Available scripts on ${aod.name}")
                                .setItems(scripts.toTypedArray()) { _, which ->
                                    scriptField.setText(scripts[which])
                                }.show()
                        }
                    }
                }
            })
        }

        addLabel("Delay before running script (seconds)")
        val delayField = addField(
            "30",
            (existing?.postShutdownDelaySeconds ?: 30).toString(),
            numeric = true
        )
        container.addView(TextView(this).apply {
            text = "How long to wait after triggering shutdown before the script runs. " +
                   "Give the server enough time to actually power off (usually 15–60s)."
            textSize = 11f; setTextColor(Color.parseColor("#2A2A3E")); setPadding(0, 4, 0, 0)
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { bottomMargin = 40 }
        })

        // ── Save button ───────────────────────────────────────────────────────
        container.addView(TextView(this).apply {
            text = if (existing != null) "SAVE CHANGES" else "ADD DEVICE"
            textSize = 14f; setTextColor(Color.parseColor("#0A0A0F"))
            setBackgroundColor(Color.parseColor("#7B7BFF"))
            gravity = Gravity.CENTER; setPadding(0, 40, 0, 40)
            typeface = Typeface.DEFAULT_BOLD
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { topMargin = 16 }
            isClickable = true; isFocusable = true
            setOnClickListener {
                val name = nameField.text.toString().trim()
                val ip = ipField.text.toString().trim()
                val port = portField.text.toString().toIntOrNull() ?: 5050
                val token = tokenField.text.toString().trim()
                val script = scriptField.text.toString().trim()
                val delay = delayField.text.toString().toIntOrNull() ?: 30

                if (name.isEmpty() || ip.isEmpty() || token.isEmpty()) {
                    Toast.makeText(this@AddEditDeviceActivity, "Fill in all required fields", Toast.LENGTH_SHORT).show()
                    return@setOnClickListener
                }

                val devices = DeviceStore.getDevices(this@AddEditDeviceActivity)
                if (existing != null) {
                    val idx = devices.indexOfFirst { it.id == editingId }
                    if (idx >= 0) devices[idx] = devices[idx].copy(
                        name = name, ip = ip, port = port, token = token,
                        postShutdownScript = script, postShutdownDelaySeconds = delay
                    )
                } else {
                    devices.add(Device(
                        id = DeviceStore.newId(), name = name, ip = ip,
                        port = port, token = token,
                        postShutdownScript = script, postShutdownDelaySeconds = delay
                    ))
                }
                DeviceStore.saveDevices(this@AddEditDeviceActivity, devices)
                finish()
            }
        })

        container.addView(TextView(this).apply {
            text = "CANCEL"; textSize = 13f; setTextColor(Color.parseColor("#444455"))
            gravity = Gravity.CENTER; setPadding(0, 32, 0, 0)
            isClickable = true; isFocusable = true; setOnClickListener { finish() }
        })

        scroll.addView(container)
        setContentView(scroll)
    }
}
