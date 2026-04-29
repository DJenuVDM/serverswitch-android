package com.servermonitor

import android.content.Intent
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.content.Context
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.view.inputmethod.InputMethodManager
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout
import kotlin.concurrent.thread

class LogActivity : AppCompatActivity() {

    private lateinit var device: Device
    private lateinit var screenName: String
    private lateinit var swipeRefresh: SwipeRefreshLayout
    private lateinit var logContent: TextView
    private lateinit var logScroll: ScrollView
    private lateinit var newLogHint: TextView
    private lateinit var inputCommand: EditText
    private var previousLogText = ""
    private var logOffset = 0
    private var userScrolledUp = false
    private var initialized = false
    private val pollHandler = android.os.Handler(android.os.Looper.getMainLooper())
    private val pollInterval = 4000L

    private val pollRunnable = object : Runnable {
        override fun run() {
            thread {
                val result = Network.tailScreenLog(device, screenName, logOffset)
                runOnUiThread {
                    if (result != null) {
                        val (newLines, nextOffset) = result
                        // Trim trailing spaces per line, drop blank lines from screen hardcopy padding
                        val meaningful = newLines.map { it.trimEnd() }.filter { it.isNotBlank() }
                        if (meaningful.isNotEmpty()) {
                            val toAppend = meaningful.joinToString("\n")
                            if (logContent.text.isNotEmpty()) {
                                logContent.append("\n$toAppend")
                            } else {
                                logContent.text = toAppend
                            }
                            if (!userScrolledUp) smoothScrollToBottom()
                            newLogHint.text = "Live — updating automatically"
                        }
                        logOffset = nextOffset
                    }
                    pollHandler.postDelayed(this, pollInterval)
                }
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.statusBarColor = Color.parseColor("#0A0A0F")
        window.navigationBarColor = Color.parseColor("#0A0A0F")

        device = intent.getSerializableExtra("device") as Device
        screenName = intent.getStringExtra("screen_name") ?: ""
        val focusCommand = intent.getBooleanExtra("focus_command", false)

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.parseColor("#0A0A0F"))
            setPadding(24, 24, 24, 24)
        }

        // Header row: back arrow | title (weight 1) | refresh button
        val headerRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        headerRow.addView(TextView(this).apply {
            text = "←"
            textSize = 24f
            setTextColor(Color.parseColor("#7B7BFF"))
            setPadding(0, 0, 24, 0)
            isClickable = true
            isFocusable = true
            setOnClickListener { finish() }
        })
        headerRow.addView(TextView(this).apply {
            text = "Logs: $screenName"
            textSize = 18f
            setTextColor(Color.parseColor("#F0F0F5"))
            typeface = Typeface.DEFAULT_BOLD
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        })
        headerRow.addView(TextView(this).apply {
            text = "⟳"
            textSize = 22f
            setTextColor(Color.parseColor("#7B7BFF"))
            setPadding(24, 0, 0, 0)
            isClickable = true
            isFocusable = true
            setOnClickListener { fetchLatestLogs(true) }
        })

        newLogHint = TextView(this).apply {
            text = "Loading..."
            textSize = 12f
            setTextColor(Color.parseColor("#A0A0FF"))
            setPadding(0, 14, 0, 14)
        }

        logContent = TextView(this).apply {
            text = ""
            textSize = 12f
            setTextColor(Color.parseColor("#F0F0F5"))
            setPadding(24, 24, 24, 24)
            setTextIsSelectable(true)
        }

        logScroll = ScrollView(this).apply {
            addView(logContent)
            setFillViewport(true)
            setBackgroundColor(Color.parseColor("#0F0F1A"))
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                0,
                1f
            ).apply { topMargin = 12; bottomMargin = 12 }
            setOnScrollChangeListener { _, _, scrollY, _, _ ->
                val atBottom = scrollY >= (logContent.height - height - 10)
                userScrolledUp = !atBottom
            }
        }

        swipeRefresh = SwipeRefreshLayout(this).apply {
            setColorSchemeColors(Color.parseColor("#7B7BFF"))
            setOnRefreshListener { fetchLatestLogs(true) }
            addView(logScroll)
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                0,
                1f
            )
        }

        val commandLabel = TextView(this).apply {
            text = "Command"
            textSize = 12f
            setTextColor(Color.parseColor("#A0A0FF"))
            setPadding(0, 10, 0, 8)
        }

        val commandRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(16, 14, 16, 14)
            setBackgroundColor(Color.parseColor("#0A1120"))
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { topMargin = 18 }
        }
        inputCommand = EditText(this).apply {
            hint = "Click here to type a command"
            textSize = 14f
            setHintTextColor(Color.parseColor("#BBBBBB"))
            setTextColor(Color.parseColor("#FFFFFF"))
            inputType = android.text.InputType.TYPE_CLASS_TEXT
            background = GradientDrawable().apply {
                setColor(Color.parseColor("#141B2E"))
                cornerRadius = 28f
                setStroke(2, Color.parseColor("#4E5EFF"))
            }
            setPadding(26, 22, 26, 22)
            isSingleLine = true
            isFocusable = true
            isFocusableInTouchMode = true
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
            minHeight = (56 * resources.displayMetrics.density).toInt()
            setOnClickListener {
                requestFocus()
                val imm = getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
                imm.showSoftInput(this, InputMethodManager.SHOW_IMPLICIT)
            }
        }
        val sendButton = TextView(this).apply {
            text = "SEND"
            textSize = 14f
            setTextColor(Color.parseColor("#F0F0F5"))
            background = GradientDrawable().apply {
                setColor(Color.parseColor("#1DB954"))
                cornerRadius = 28f
            }
            setPadding(28, 22, 28, 22)
            gravity = Gravity.CENTER
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                leftMargin = 12
                gravity = Gravity.CENTER_VERTICAL
            }
            isClickable = true
            isFocusable = true
            setOnClickListener { sendCommand() }
        }
        commandRow.addView(inputCommand)
        commandRow.addView(sendButton)

        root.addView(headerRow)
        root.addView(newLogHint)
        root.addView(swipeRefresh)
        root.addView(commandLabel)
        root.addView(commandRow)
        setContentView(root)

        if (focusCommand) {
            inputCommand.requestFocus()
            val imm = getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
            imm.showSoftInput(inputCommand, InputMethodManager.SHOW_IMPLICIT)
        }

        // Kick off the initial load — this will schedule the first poll when done
        fetchLatestLogs(false)
        initialized = true
    }

    private fun smoothScrollToBottom() {
        logScroll.post {
            logScroll.smoothScrollTo(0, logContent.height)
        }
    }

    private fun fetchLatestLogs(userRefresh: Boolean) {
        // Stop any in-flight poll so we don't have two loops running
        pollHandler.removeCallbacks(pollRunnable)
        if (userRefresh) swipeRefresh.isRefreshing = true

        thread {
            val latest = Network.getScreenLog(device, screenName)
            runOnUiThread {
                if (userRefresh) swipeRefresh.isRefreshing = false
                if (latest == null) {
                    if (userRefresh) Toast.makeText(this, "Unable to load logs", Toast.LENGTH_SHORT).show()
                } else {
                    updateLogText(latest)
                }
                // Schedule the poll exactly once here — this is the single source of truth
                pollHandler.postDelayed(pollRunnable, pollInterval)
            }
        }
    }

    private fun updateLogText(latest: String) {
        // Trim trailing spaces per line to remove screen hardcopy padding
        val cleanedLines = latest.lines().map { it.trimEnd() }
        val trimmedLatest = cleanedLines.joinToString("\n").trimEnd()

        logContent.text = trimmedLatest.ifEmpty { "(no recent logs)" }
        userScrolledUp = false
        smoothScrollToBottom()
        newLogHint.text = "Live — updating automatically"

        previousLogText = trimmedLatest
        logOffset = cleanedLines.size
    }

    override fun onResume() {
        super.onResume()
        // Only restart polling when returning to this screen (not on first launch —
        // onCreate's fetchLatestLogs already handles that case via initialized flag)
        if (initialized) {
            pollHandler.removeCallbacks(pollRunnable)
            pollHandler.postDelayed(pollRunnable, pollInterval)
        }
    }

    override fun onPause() {
        super.onPause()
        pollHandler.removeCallbacks(pollRunnable)
    }

    private fun sendCommand() {
        val command = inputCommand.text.toString().trim()
        if (command.isEmpty()) {
            Toast.makeText(this, "Enter a command first", Toast.LENGTH_SHORT).show()
            return
        }

        inputCommand.setText("")
        Toast.makeText(this, "Sending command...", Toast.LENGTH_SHORT).show()

        thread {
            val success = Network.sendScreenCommand(device, screenName, command)
            runOnUiThread {
                if (success) {
                    Toast.makeText(this, "Command sent", Toast.LENGTH_SHORT).show()
                    fetchLatestLogs(false)
                    newLogHint.text = "Command sent — waiting for output"
                    inputCommand.postDelayed({
                        inputCommand.requestFocus()
                        val imm = getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
                        imm.showSoftInput(inputCommand, InputMethodManager.SHOW_FORCED)
                    }, 200)
                } else {
                    Toast.makeText(this, "Failed to send command", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }
}