package com.example.bettingarbitragecalculator

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.graphics.PixelFormat
import android.os.Build
import android.os.IBinder
import android.text.Editable
import android.text.TextWatcher
import android.view.ContextThemeWrapper
import android.view.Gravity
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.EditText
import android.widget.ImageButton
import android.widget.TextView
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat

class OverlayCalculatorService : Service() {
    private lateinit var windowManager: WindowManager
    private var overlayView: View? = null
    private lateinit var layoutParams: WindowManager.LayoutParams

    override fun onCreate() {
        super.onCreate()
        windowManager = getSystemService(Context.WINDOW_SERVICE) as WindowManager
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            stopSelf()
            return START_NOT_STICKY
        }

        createNotificationChannel()
        startForeground(NOTIFICATION_ID, buildNotification())

        if (overlayView == null) {
            val shown = showOverlay()
            isRunning = shown
            if (!shown) {
                stopSelf()
                return START_NOT_STICKY
            }
        }

        return START_STICKY
    }

    override fun onDestroy() {
        overlayView?.let {
            runCatching {
                windowManager.removeView(it)
            }
        }
        overlayView = null
        isRunning = false
        stopForeground(STOP_FOREGROUND_REMOVE)
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun showOverlay(): Boolean {
        val themedContext = ContextThemeWrapper(this, R.style.Theme_BettingArbitrageCalculator)
        val inflater = LayoutInflater.from(themedContext)
        overlayView = inflater.inflate(R.layout.view_overlay, null)

        layoutParams = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
            } else {
                @Suppress("DEPRECATION")
                WindowManager.LayoutParams.TYPE_PHONE
            },
            WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = 32
            y = 120
        }

        val rootView = requireNotNull(overlayView)
        val header = rootView.findViewById<View>(R.id.overlayHeader)
        val closeButton = rootView.findViewById<ImageButton>(R.id.overlayCloseButton)
        val backOddsInput = rootView.findViewById<EditText>(R.id.overlayBackOddsInput)
        val layOddsInput = rootView.findViewById<EditText>(R.id.overlayLayOddsInput)
        val backStakeInput = rootView.findViewById<EditText>(R.id.overlayBackStakeInput)
        val layStakeInput = rootView.findViewById<EditText>(R.id.overlayLayStakeInput)
        val statusText = rootView.findViewById<TextView>(R.id.overlayStatusText)
        val backStakeValue = rootView.findViewById<TextView>(R.id.overlayBackStakeValue)
        val layStakeValue = rootView.findViewById<TextView>(R.id.overlayLayStakeValue)
        val profitValue = rootView.findViewById<TextView>(R.id.overlayProfitValue)

        val render = {
            when (
                val result = ArbitrageCalculator.calculate(
                    backOddsInput.text?.toString().orEmpty(),
                    layOddsInput.text?.toString().orEmpty(),
                    backStakeInput.text?.toString().orEmpty(),
                    layStakeInput.text?.toString().orEmpty()
                )
            ) {
                is CalculationResult.Success -> {
                    statusText.text = getString(R.string.overlay_ready_status)
                    statusText.setTextColor(ContextCompat.getColor(this, R.color.textPrimary))
                    statusText.background = ContextCompat.getDrawable(this, R.drawable.bg_status_neutral)

                    backStakeValue.text = ArbitrageCalculator.format(result.backStake)
                    layStakeValue.text = ArbitrageCalculator.format(result.layStake)
                    profitValue.text = ArbitrageCalculator.format(result.profit)

                    val profitColor = when {
                        result.profit.signum() > 0 -> R.color.profitGreen
                        result.profit.signum() < 0 -> R.color.lossRed
                        else -> R.color.textPrimary
                    }
                    profitValue.setTextColor(ContextCompat.getColor(this, profitColor))
                }

                is CalculationResult.Error -> {
                    statusText.text = result.message
                    statusText.setTextColor(ContextCompat.getColor(this, R.color.lossRed))
                    statusText.background = ContextCompat.getDrawable(this, R.drawable.bg_status_error)
                    backStakeValue.text = getString(R.string.placeholder_value)
                    layStakeValue.text = getString(R.string.placeholder_value)
                    profitValue.text = getString(R.string.placeholder_value)
                    profitValue.setTextColor(ContextCompat.getColor(this, R.color.textPrimary))
                }
            }
        }

        listOf(backOddsInput, layOddsInput, backStakeInput, layStakeInput).forEach { editText ->
            editText.addTextChangedListener(object : TextWatcher {
                override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit

                override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) = Unit

                override fun afterTextChanged(s: Editable?) {
                    render()
                }
            })
        }

        closeButton.setOnClickListener {
            stopSelf()
        }

        attachDragListener(header)
        render()
        return runCatching {
            windowManager.addView(rootView, layoutParams)
            true
        }.getOrDefault(false)
    }

    private fun attachDragListener(header: View) {
        header.setOnTouchListener(object : View.OnTouchListener {
            private var initialX = 0
            private var initialY = 0
            private var initialTouchX = 0f
            private var initialTouchY = 0f

            override fun onTouch(v: View, event: MotionEvent): Boolean {
                when (event.action) {
                    MotionEvent.ACTION_DOWN -> {
                        initialX = layoutParams.x
                        initialY = layoutParams.y
                        initialTouchX = event.rawX
                        initialTouchY = event.rawY
                        return true
                    }

                    MotionEvent.ACTION_MOVE -> {
                        layoutParams.x = initialX + (event.rawX - initialTouchX).toInt()
                        layoutParams.y = initialY + (event.rawY - initialTouchY).toInt()
                        overlayView?.let { windowManager.updateViewLayout(it, layoutParams) }
                        return true
                    }
                }
                return false
            }
        })
    }

    private fun buildNotification(): Notification {
        val openIntent = Intent(this, MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            openIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(getString(R.string.notification_title))
            .setContentText(getString(R.string.notification_text))
            .setSmallIcon(android.R.drawable.ic_menu_manage)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .build()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val manager = getSystemService(NotificationManager::class.java)
            val channel = NotificationChannel(
                CHANNEL_ID,
                getString(R.string.notification_channel_name),
                NotificationManager.IMPORTANCE_LOW
            )
            manager.createNotificationChannel(channel)
        }
    }

    companion object {
        const val ACTION_START = "com.example.bettingarbitragecalculator.action.START"
        const val ACTION_STOP = "com.example.bettingarbitragecalculator.action.STOP"
        private const val CHANNEL_ID = "overlay_calculator_channel"
        private const val NOTIFICATION_ID = 3001

        @Volatile
        var isRunning: Boolean = false
    }
}
