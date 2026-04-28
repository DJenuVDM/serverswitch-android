package com.servermonitor

import android.graphics.Color
import android.graphics.Typeface
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.widget.*
import androidx.appcompat.app.AppCompatActivity

class AddEditAodActivity : AppCompatActivity() {

    private var editingId: String? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.statusBarColor = Color.parseColor("#0A0A0F")
        window.navigationBarColor = Color.parseColor("#0A0A0F")

        editingId = intent.getStringExtra("aod_id")
        val existing = editingId?.let { id -> DeviceStore.getAods(this).find { it.id == id } }

        val scroll = ScrollView(this).apply { setBackgroundColor(Color.parseColor("#0A0A0F")) }
        val container = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(48, 80, 48, 80)
        }

        container.addView(TextView(this).apply {
            text = if (existing != null) "EDIT NETWORK" else "ADD NETWORK"
            textSize = 24f; setTextColor(Color.parseColor("#F0F0F5"))
            typeface = Typeface.DEFAULT_BOLD; letterSpacing = 0.1f; setPadding(0, 0, 0, 8)
        })
        container.addView(TextView(this).apply {
            text = "Always-on device that can wake others on its network"
            textSize = 12f; setTextColor(Color.parseColor("#444455")); setPadding(0, 0, 0, 56)
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
        val nameField = addField("e.g. Home Pi", existing?.name ?: "")
        addLabel("IP address (Tailscale)")
        val ipField = addField("e.g. 100.x.x.x", existing?.ip ?: "")
        addLabel("Port")
        val portField = addField("5051", (existing?.port ?: 5051).toString(), numeric = true)
        addLabel("Auth token")
        val tokenField = addField("Token from AOD install", existing?.token ?: "", password = true)
        addLabel("Broadcast address (for WoL)")
        val broadcastField = addField("e.g. 192.168.1.255", existing?.broadcast ?: "255.255.255.255")

        // Device assignment
        val divider = View(this).apply {
            setBackgroundColor(Color.parseColor("#1A1A2E"))
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 1).apply {
                topMargin = 16; bottomMargin = 32
            }
        }
        container.addView(divider)

        container.addView(TextView(this).apply {
            text = "DEVICES ON THIS NETWORK"
            textSize = 10f; setTextColor(Color.parseColor("#444455"))
            letterSpacing = 0.2f; setPadding(0, 0, 0, 16)
        })
        container.addView(TextView(this).apply {
            text = "Select which of your devices are on this AOD's local network"
            textSize = 12f; setTextColor(Color.parseColor("#333344")); setPadding(0, 0, 0, 24)
        })

        val allDevices = DeviceStore.getDevices(this)
        val checkedIds = existing?.deviceIds?.toMutableList() ?: mutableListOf()
        val checkboxes = mutableMapOf<String, CheckBox>()

        if (allDevices.isEmpty()) {
            container.addView(TextView(this).apply {
                text = "No devices added yet — add devices first"
                textSize = 13f; setTextColor(Color.parseColor("#333344"))
            })
        } else {
            allDevices.forEach { device ->
                val row = LinearLayout(this).apply {
                    orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL
                    layoutParams = LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT
                    ).apply { bottomMargin = 16 }
                }
                val cb = CheckBox(this).apply {
                    isChecked = checkedIds.contains(device.id)
                    buttonTintList = android.content.res.ColorStateList.valueOf(Color.parseColor("#7B7BFF"))
                }
                val label = TextView(this).apply {
                    text = device.name; textSize = 14f
                    setTextColor(Color.parseColor("#F0F0F5")); setPadding(16, 0, 0, 0)
                }
                cb.setOnCheckedChangeListener { _, checked ->
                    if (checked) checkedIds.add(device.id) else checkedIds.remove(device.id)
                }
                checkboxes[device.id] = cb
                row.addView(cb); row.addView(label)
                container.addView(row)
            }
        }

        // Save button
        container.addView(TextView(this).apply {
            text = if (existing != null) "SAVE CHANGES" else "ADD NETWORK"
            textSize = 14f; setTextColor(Color.parseColor("#0A0A0F"))
            setBackgroundColor(Color.parseColor("#7B7BFF"))
            gravity = Gravity.CENTER; setPadding(0, 40, 0, 40)
            typeface = Typeface.DEFAULT_BOLD
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { topMargin = 40 }
            isClickable = true; isFocusable = true
            setOnClickListener {
                val name = nameField.text.toString().trim()
                val ip = ipField.text.toString().trim()
                val port = portField.text.toString().toIntOrNull() ?: 5051
                val token = tokenField.text.toString().trim()
                val broadcast = broadcastField.text.toString().trim()

                if (name.isEmpty() || ip.isEmpty() || token.isEmpty()) {
                    Toast.makeText(this@AddEditAodActivity, "Fill in all fields", Toast.LENGTH_SHORT).show()
                    return@setOnClickListener
                }

                val aods = DeviceStore.getAods(this@AddEditAodActivity)
                if (existing != null) {
                    val idx = aods.indexOfFirst { it.id == editingId }
                    if (idx >= 0) aods[idx] = aods[idx].copy(
                        name = name, ip = ip, port = port,
                        token = token, broadcast = broadcast,
                        deviceIds = checkedIds
                    )
                } else {
                    aods.add(Aod(
                        id = DeviceStore.newId(), name = name, ip = ip,
                        port = port, token = token, broadcast = broadcast,
                        deviceIds = checkedIds
                    ))
                }
                DeviceStore.saveAods(this@AddEditAodActivity, aods)
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
