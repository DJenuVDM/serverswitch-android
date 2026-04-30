package com.servermonitor

import android.content.Intent
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.widget.*
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import kotlin.concurrent.thread

class RunScriptActivity : AppCompatActivity() {

    private lateinit var device: Device
    private lateinit var contentFrame: FrameLayout

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.statusBarColor = Color.parseColor("#0A0A0F")
        window.navigationBarColor = Color.parseColor("#0A0A0F")

        device = intent.getSerializableExtra("device") as Device

        contentFrame = FrameLayout(this).apply {
            setBackgroundColor(Color.parseColor("#0A0A0F"))
        }
        setContentView(contentFrame)

        showScriptList()
    }

    // ── Screen 1: Script list ─────────────────────────────────────────────────

    private fun showScriptList() {
        val scroll = ScrollView(this).apply { setBackgroundColor(Color.parseColor("#0A0A0F")) }
        val container = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(48, 72, 48, 80)
        }

        val headerRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { bottomMargin = 8 }
        }
        headerRow.addView(TextView(this).apply {
            text = "←"; textSize = 24f; setTextColor(Color.parseColor("#7B7BFF"))
            setPadding(0, 0, 24, 0); isClickable = true; isFocusable = true
            setOnClickListener { finish() }
        })
        headerRow.addView(TextView(this).apply {
            text = "RUN SCRIPT"; textSize = 24f; setTextColor(Color.parseColor("#F0F0F5"))
            typeface = Typeface.DEFAULT_BOLD; letterSpacing = 0.1f
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        })
        container.addView(headerRow)
        container.addView(TextView(this).apply {
            text = "Scripts on ${device.name}"; textSize = 12f
            setTextColor(Color.parseColor("#444455")); setPadding(0, 0, 0, 48)
        })

        val loadingText = TextView(this).apply {
            text = "Loading scripts…"; textSize = 14f
            setTextColor(Color.parseColor("#444455"))
            gravity = Gravity.CENTER; setPadding(0, 32, 0, 0)
        }
        container.addView(loadingText)

        scroll.addView(container)
        contentFrame.removeAllViews()
        contentFrame.addView(scroll)

        thread {
            val scripts = try {
                Network.listScripts(device)
            } catch (e: Exception) {
                runOnUiThread {
                    val msg = when {
                        e.message?.contains("failed to connect", ignoreCase = true) == true ||
                        e.message?.contains("connection refused", ignoreCase = true) == true ->
                            "Could not connect to ${device.name} at ${device.ip}:${device.port}.\n\n" +
                            "The device is either offline or the ServerSwitch service is not running. " +
                            "SSH in and check:\n  sudo systemctl status serverswitch"
                        e.message?.contains("timeout", ignoreCase = true) == true ->
                            "Connection to ${device.name} timed out.\n\n" +
                            "The device took too long to respond. Check that Tailscale is running " +
                            "on both devices and that the IP address in the app is correct."
                        else ->
                            "A network error occurred while loading scripts from ${device.name}.\n\n" +
                            "Error detail: ${e.message}"
                    }
                    showError("Could not load scripts", msg)
                }
                return@thread
            }
            runOnUiThread {
                container.removeView(loadingText)
                if (scripts.isEmpty()) {
                    container.addView(makeInfoBox(
                        "No scripts found",
                        "Place executable scripts in:\n${device.ip}:/opt/serverswitch/scripts/\n\nThen tap ⟳ to refresh.",
                        "#444455"
                    ))
                    container.addView(TextView(this).apply {
                        text = "⟳  REFRESH"; textSize = 13f
                        setTextColor(Color.parseColor("#7B7BFF"))
                        gravity = Gravity.CENTER; setPadding(0, 32, 0, 0)
                        isClickable = true; isFocusable = true
                        setOnClickListener { showScriptList() }
                    })
                } else {
                    scripts.forEach { scriptName -> container.addView(makeScriptCard(scriptName)) }
                }
            }
        }
    }

    private fun makeScriptCard(scriptName: String): View {
        return LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.parseColor("#0F0F1A"))
            setPadding(32, 28, 32, 28)
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { bottomMargin = 18 }
            isClickable = true; isFocusable = true
            setOnClickListener { showRunPanel(scriptName) }
            addView(TextView(this@RunScriptActivity).apply {
                text = scriptName; textSize = 17f; setTextColor(Color.parseColor("#F0F0F5"))
                typeface = Typeface.DEFAULT_BOLD; setPadding(0, 0, 0, 8)
            })
            addView(TextView(this@RunScriptActivity).apply {
                text = "Tap to configure and run"; textSize = 12f
                setTextColor(Color.parseColor("#444455"))
            })
        }
    }

    // ── Screen 2: Run panel ───────────────────────────────────────────────────

    private fun showRunPanel(scriptName: String) {
        val scroll = ScrollView(this).apply { setBackgroundColor(Color.parseColor("#0A0A0F")) }
        val container = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL; setPadding(48, 72, 48, 80)
        }

        val headerRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { bottomMargin = 8 }
        }
        headerRow.addView(TextView(this).apply {
            text = "←"; textSize = 24f; setTextColor(Color.parseColor("#7B7BFF"))
            setPadding(0, 0, 24, 0); isClickable = true; isFocusable = true
            setOnClickListener { showScriptList() }
        })
        headerRow.addView(TextView(this).apply {
            text = scriptName; textSize = 20f; setTextColor(Color.parseColor("#F0F0F5"))
            typeface = Typeface.DEFAULT_BOLD; letterSpacing = 0.05f
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        })
        container.addView(headerRow)
        container.addView(TextView(this).apply {
            text = "on ${device.name}"; textSize = 12f
            setTextColor(Color.parseColor("#444455")); setPadding(0, 0, 0, 48)
        })

        // ── Arguments ─────────────────────────────────────────────────────────
        container.addView(sectionLabel("ARGUMENTS (OPTIONAL)"))
        val argsField = editField("e.g. --port 8080 --verbose")
        container.addView(argsField)
        container.addView(hintText("Space-separated arguments passed directly to the script."))

        // ── Divider ───────────────────────────────────────────────────────────
        container.addView(divider())

        // ── Screen toggle ─────────────────────────────────────────────────────
        container.addView(sectionLabel("RUN IN SCREEN SESSION"))
        container.addView(TextView(this).apply {
            text = "Keep the script running in a detached screen session so you can monitor it from the Screens tab."
            textSize = 12f; setTextColor(Color.parseColor("#333344")); setPadding(0, 0, 0, 24)
        })

        val toggleRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { bottomMargin = 16 }
        }
        val screenSwitch = Switch(this).apply {
            isChecked = false
            thumbTintList = android.content.res.ColorStateList.valueOf(Color.parseColor("#7B7BFF"))
            trackTintList = android.content.res.ColorStateList.valueOf(Color.parseColor("#1A1A2E"))
        }
        toggleRow.addView(screenSwitch)
        toggleRow.addView(TextView(this).apply {
            text = "Create a screen session for this script"; textSize = 14f
            setTextColor(Color.parseColor("#F0F0F5")); setPadding(20, 0, 0, 0)
        })
        container.addView(toggleRow)

        // Screen name field — revealed by toggle
        val screenNameLabel = sectionLabel("SCREEN SESSION NAME").also { it.visibility = View.GONE }
        val screenNameField = editField("e.g. myapp").also {
            it.setText(scriptName.replace(Regex("[^a-zA-Z0-9_-]"), "_"))
            it.visibility = View.GONE
        }
        val screenNameHint = hintText("Letters, numbers, - and _ only. You can attach from the Screens tab.").also { it.visibility = View.GONE }

        container.addView(screenNameLabel)
        container.addView(screenNameField)
        container.addView(screenNameHint)

        // Note about short-lived scripts
        val shortScriptNote = makeInfoBox(
            "Note: script exits immediately",
            "This script will likely finish before you can open its log. " +
            "Screen sessions close as soon as the script exits — " +
            "for one-shot scripts like notify.sh there is nothing to monitor.",
            "#333344"
        ).also { it.visibility = View.GONE }
        container.addView(shortScriptNote)

        screenSwitch.setOnCheckedChangeListener { _, checked ->
            val vis = if (checked) View.VISIBLE else View.GONE
            screenNameLabel.visibility = vis
            screenNameField.visibility = vis
            screenNameHint.visibility = vis
            // Show the note for scripts that are obviously one-shot
            if (checked && scriptName.contains("notify", ignoreCase = true)) {
                shortScriptNote.visibility = View.VISIBLE
            } else {
                shortScriptNote.visibility = View.GONE
            }
        }

        // ── Run button ────────────────────────────────────────────────────────
        val runBtn = TextView(this).apply {
            text = "RUN SCRIPT"; textSize = 14f; setTextColor(Color.parseColor("#0A0A0F"))
            background = GradientDrawable().apply {
                setColor(Color.parseColor("#7B7BFF")); cornerRadius = 8f
            }
            gravity = Gravity.CENTER; setPadding(0, 44, 0, 44)
            typeface = Typeface.DEFAULT_BOLD; isClickable = true; isFocusable = true
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { topMargin = 48 }
        }

        runBtn.setOnClickListener {
            val rawArgs = argsField.text.toString().trim()
            val argsList = if (rawArgs.isEmpty()) emptyList() else rawArgs.split("\\s+".toRegex())
            val useScreen = screenSwitch.isChecked
            val screenName = if (useScreen) screenNameField.text.toString().trim() else ""

            if (useScreen && screenName.isEmpty()) {
                showError("Missing screen name", "Enter a name for the screen session.")
                return@setOnClickListener
            }
            if (useScreen && !screenName.matches(Regex("[a-zA-Z0-9_-]+"))) {
                showError("Invalid screen name", "Screen session names can only contain letters, numbers, hyphens and underscores.")
                return@setOnClickListener
            }

            setRunBtnState(runBtn, loading = true)

            thread {
                val result = Network.runScript(device, scriptName, argsList, screenName)
                runOnUiThread {
                    setRunBtnState(runBtn, loading = false)
                    when (result) {
                        is NetworkResult.Error -> {
                            showError("Failed to run $scriptName", result.message)
                        }
                        is NetworkResult.Success -> {
                            if (useScreen) {
                                // Small delay so screen has time to register before we try to list it
                                runBtn.postDelayed({
                                    val resolvedName = resolveScreenName(screenName)
                                    AlertDialog.Builder(this)
                                        .setTitle("Script started in screen")
                                        .setMessage("\"$scriptName\" is running in screen session \"$screenName\".\n\nOpen its logs now?")
                                        .setPositiveButton("Yes, open logs") { _, _ ->
                                            startActivity(
                                                Intent(this, LogActivity::class.java).apply {
                                                    putExtra("device", device)
                                                    putExtra("screen_name", resolvedName)
                                                    putExtra("focus_command", false)
                                                }
                                            )
                                        }
                                        .setNegativeButton("No") { _, _ -> finish() }
                                        .show()
                                }, 800)
                            } else {
                                Toast.makeText(this, "$scriptName started", Toast.LENGTH_SHORT).show()
                                finish()
                            }
                        }
                    }
                }
            }
        }

        container.addView(runBtn)
        container.addView(TextView(this).apply {
            text = "CANCEL"; textSize = 13f; setTextColor(Color.parseColor("#444455"))
            gravity = Gravity.CENTER; setPadding(0, 32, 0, 0)
            isClickable = true; isFocusable = true
            setOnClickListener { showScriptList() }
        })

        scroll.addView(container)
        contentFrame.removeAllViews()
        contentFrame.addView(scroll)
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private fun showError(title: String, message: String) {
        // Wrap the message in a scrollable TextView so long server error
        // explanations are fully readable without the dialog clipping them
        val scrollView = android.widget.ScrollView(this)
        val tv = TextView(this).apply {
            text = message
            textSize = 14f
            setTextColor(Color.parseColor("#F0F0F5"))
            setPadding(64, 32, 64, 16)
            setTextIsSelectable(true)   // lets user copy the error text
        }
        scrollView.addView(tv)

        AlertDialog.Builder(this)
            .setTitle(title)
            .setView(scrollView)
            .setPositiveButton("OK", null)
            .show()
    }

    private fun setRunBtnState(btn: TextView, loading: Boolean) {
        btn.alpha = if (loading) 0.6f else 1f
        btn.isEnabled = !loading
        btn.text = if (loading) "Running…" else "RUN SCRIPT"
    }

    private fun resolveScreenName(sessionName: String): String {
        return try {
            val screens = Network.listScreens(device)
            screens.firstOrNull { it.endsWith("/$sessionName") || it == sessionName } ?: sessionName
        } catch (e: Exception) { sessionName }
    }

    private fun sectionLabel(text: String) = TextView(this).apply {
        this.text = text; textSize = 10f; setTextColor(Color.parseColor("#444455"))
        letterSpacing = 0.2f; setPadding(0, 0, 0, 8)
    }

    private fun hintText(text: String) = TextView(this).apply {
        this.text = text; textSize = 11f; setTextColor(Color.parseColor("#2A2A3E"))
        setPadding(0, 4, 0, 0)
        layoutParams = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT
        ).apply { bottomMargin = 40 }
    }

    private fun editField(hint: String) = EditText(this).apply {
        this.hint = hint
        setHintTextColor(Color.parseColor("#2A2A3E"))
        setTextColor(Color.parseColor("#F0F0F5"))
        setBackgroundColor(Color.parseColor("#0F0F1A"))
        setPadding(24, 24, 24, 24)
        inputType = android.text.InputType.TYPE_CLASS_TEXT
        layoutParams = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT
        ).apply { bottomMargin = 8 }
    }

    private fun divider() = View(this).apply {
        setBackgroundColor(Color.parseColor("#1A1A2E"))
        layoutParams = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, 1
        ).apply { topMargin = 8; bottomMargin = 32 }
    }

    private fun makeInfoBox(title: String, body: String, color: String): LinearLayout {
        return LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.parseColor("#0F0F1A"))
            setPadding(32, 24, 32, 24)
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { bottomMargin = 24 }
            addView(TextView(this@RunScriptActivity).apply {
                text = title; textSize = 13f
                setTextColor(Color.parseColor(color))
                typeface = Typeface.DEFAULT_BOLD; setPadding(0, 0, 0, 8)
            })
            addView(TextView(this@RunScriptActivity).apply {
                text = body; textSize = 12f
                setTextColor(Color.parseColor(color))
            })
        }
    }
}