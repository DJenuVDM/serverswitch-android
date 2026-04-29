package com.servermonitor

import android.content.Intent
import android.graphics.Color
import android.graphics.Typeface
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout
import kotlin.concurrent.thread

class ScreenActivity : AppCompatActivity() {

    private lateinit var device: Device
    private lateinit var contentFrame: FrameLayout

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.statusBarColor = Color.parseColor("#0A0A0F")

        device = intent.getSerializableExtra("device") as Device

        contentFrame = FrameLayout(this).apply {
            setBackgroundColor(Color.parseColor("#0A0A0F"))
        }
        setContentView(contentFrame)

        loadScreens()
    }

    private fun loadScreens() {
        val swipe = SwipeRefreshLayout(this).apply {
            setColorSchemeColors(Color.parseColor("#7B7BFF"))
            setBackgroundColor(Color.parseColor("#0A0A0F"))
        }
        val scroll = ScrollView(this).apply { setBackgroundColor(Color.parseColor("#0A0A0F")) }
        val container = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(24, 24, 24, 24)
        }

        // Top header with back arrow
        val headerRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(0, 0, 0, 24)
        }
        headerRow.addView(TextView(this).apply {
            text = "←"
            textSize = 24f
            setTextColor(Color.parseColor("#7B7BFF"))
            setPadding(0, 0, 18, 0)
            isClickable = true
            isFocusable = true
            setOnClickListener { finish() }
        })
        headerRow.addView(TextView(this).apply {
            text = "Screens on ${device.name}"
            textSize = 20f
            setTextColor(Color.parseColor("#F0F0F5"))
            typeface = Typeface.DEFAULT_BOLD
        })
        container.addView(headerRow)

        container.addView(TextView(this).apply {
            text = "Tap a screen to open its logs and command input"
            textSize = 13f
            setTextColor(Color.parseColor("#A0A0FF"))
            setPadding(0, 0, 0, 20)
        })

        swipe.setOnRefreshListener {
            thread {
                runOnUiThread {
                    swipe.isRefreshing = false
                    loadScreens()
                }
            }
        }

        thread {
            val screens = Network.listScreens(device)
            runOnUiThread {
                if (screens.isEmpty()) {
                    container.addView(TextView(this@ScreenActivity).apply {
                        text = "No active screens found"
                        textSize = 14f
                        setTextColor(Color.parseColor("#8888AA"))
                        gravity = Gravity.CENTER
                        setPadding(0, 64, 0, 0)
                    })
                } else {
                    screens.forEach { screenName ->
                        container.addView(makeScreenCard(screenName))
                    }
                }

                scroll.addView(container)
                swipe.addView(scroll)
                contentFrame.removeAllViews()
                contentFrame.addView(swipe)
            }
        }
    }

    private fun makeScreenCard(screenName: String): View {
        return LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.parseColor("#0F0F1A"))
            setPadding(32, 28, 32, 28)
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { bottomMargin = 18 }
            isClickable = true
            isFocusable = true
            setOnClickListener {
                startActivity(Intent(this@ScreenActivity, LogActivity::class.java).apply {
                    putExtra("device", device)
                    putExtra("screen_name", screenName)
                    putExtra("focus_command", false)
                })
            }
            addView(TextView(this@ScreenActivity).apply {
                text = screenName
                textSize = 18f
                setTextColor(Color.parseColor("#F0F0F5"))
                typeface = Typeface.DEFAULT_BOLD
                setPadding(0, 0, 0, 10)
            })
            addView(TextView(this@ScreenActivity).apply {
                text = "Tap to open logs and send commands"
                textSize = 12f
                setTextColor(Color.parseColor("#A0A0FF"))
            })
        }
    }
}
