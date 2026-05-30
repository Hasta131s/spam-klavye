package com.example

import android.accessibilityservice.AccessibilityService
import android.annotation.SuppressLint
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.os.Bundle
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.app.NotificationCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class AutoTyperService : AccessibilityService() {

    private var windowManager: WindowManager? = null
    private var floatingView: View? = null
    private val scope = CoroutineScope(Dispatchers.Main + Job())
    private var typingJob: Job? = null

    // Main Floating UI components
    private var tvStatus: TextView? = null
    private var btnPlay: Button? = null
    private var btnRec: Button? = null

    private var isLearningMode = false
    private var learnedText = ""

    companion object {
        const val ACTION_STOP_SERVICE = "STOP_SERVICE"
        const val NOTIFICATION_ID = 1001
        const val CHANNEL_ID = "AutoTyperChannel"
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP_SERVICE) {
            stopSelf()
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                disableSelf()
            }
        }
        return super.onStartCommand(intent, flags, startId)
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        createNotificationChannel()
        startForeground(NOTIFICATION_ID, createNotification())
        showFloatingWindow()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Auto Typer Service",
                NotificationManager.IMPORTANCE_LOW
            )
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(channel)
        }
    }

    private fun createNotification(): Notification {
        val stopIntent = Intent(this, AutoTyperService::class.java).apply {
            action = ACTION_STOP_SERVICE
        }
        val pendingStopIntent = PendingIntent.getService(
            this, 0, stopIntent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Auto Typer Active")
            .setContentText("Tap to stop and close floating menu")
            .setSmallIcon(android.R.drawable.ic_menu_edit)
            .addAction(android.R.drawable.ic_delete, "Stop & Close", pendingStopIntent)
            .setContentIntent(pendingStopIntent)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    }

    @SuppressLint("ClickableViewAccessibility", "SetTextI18n")
    private fun showFloatingWindow() {
        if (floatingView != null) return
        
        windowManager = getSystemService(Context.WINDOW_SERVICE) as WindowManager
        
        val layoutParams = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY else WindowManager.LayoutParams.TYPE_PHONE,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.TRANSLUCENT
        )
        layoutParams.gravity = Gravity.CENTER_VERTICAL or Gravity.END
        layoutParams.x = 0
        layoutParams.y = 100

        // Parent layout is a sleek vertical bar resembling the Spec
        val parentLayout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            setPadding(12, 16, 12, 16)
            
            // Sophisticated Dark translucent background shape
            background = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                setColor(Color.parseColor("#4F378B")) // Royal deep purple base
                // Rounded drawer left border
                cornerRadii = floatArrayOf(
                    36f, 36f, // Top-Left
                    0f, 0f,   // Top-Right
                    0f, 0f,   // Bottom-Right
                    36f, 36f  // Bottom-Left
                )
                setStroke(1, Color.parseColor("#44D0BCFF")) // Subtle glowing neon edge
            }
        }

        // Status Label Box
        tvStatus = TextView(this).apply {
            text = "HAZIR"
            textSize = 9f
            setTextColor(Color.parseColor("#E6E1E5"))
            gravity = Gravity.CENTER
            setPadding(4, 2, 4, 8)
            typeface = android.graphics.Typeface.DEFAULT_BOLD
        }
        parentLayout.addView(tvStatus)

        // Helper function to create stylish rounded buttons
        fun createCircularButton(label: String, tintColorHex: String): Button {
            return Button(this).apply {
                text = label
                setTextColor(Color.WHITE)
                textSize = 9f
                setPadding(0, 0, 0, 0)
                gravity = Gravity.CENTER
                typeface = android.graphics.Typeface.DEFAULT_BOLD
                
                // Circle button background
                background = GradientDrawable().apply {
                    shape = GradientDrawable.OVAL
                    setColor(Color.parseColor(tintColorHex))
                }
                
                // Force size limits
                this.setLayoutParams(LinearLayout.LayoutParams(92, 92).apply {
                    setMargins(0, 6, 0, 6)
                })
            }
        }

        // STOP / PLAY button
        btnPlay = createCircularButton("PLAY", "#1C1B1F")
        // REC (Ezberle) button
        btnRec = createCircularButton("REC", "#1C1B1F")
        // X button
        val btnClose = createCircularButton("X", "#93000A")

        parentLayout.addView(btnPlay)
        parentLayout.addView(btnRec)
        parentLayout.addView(btnClose)

        // Add a micro drag grip line at the bottom
        val gripLine = View(this).apply {
            background = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                setColor(Color.parseColor("#33FFFFFF"))
                cornerRadius = 4f
            }
            this.setLayoutParams(LinearLayout.LayoutParams(24, 4).apply {
                setMargins(0, 10, 0, 0)
            })
        }
        parentLayout.addView(gripLine)

        floatingView = parentLayout

        // PLAY button action
        btnPlay?.setOnClickListener {
            if (typingJob?.isActive == true) {
                stopTyping()
                btnPlay?.text = "PLAY"
                btnPlay?.background = GradientDrawable().apply {
                    shape = GradientDrawable.OVAL
                    setColor(Color.parseColor("#1C1B1F"))
                }
                tvStatus?.text = "HAZIR"
            } else {
                startTyping()
            }
        }
        
        // REC button action
        btnRec?.setOnClickListener {
            isLearningMode = !isLearningMode
            if (isLearningMode) {
                btnRec?.text = "LISN"
                btnRec?.background = GradientDrawable().apply {
                    shape = GradientDrawable.OVAL
                    setColor(Color.parseColor("#FFB4AB")) // Active warn color
                }
                tvStatus?.text = "EZBERLE..."
                learnedText = ""
            } else {
                btnRec?.text = "REC"
                btnRec?.background = GradientDrawable().apply {
                    shape = GradientDrawable.OVAL
                    setColor(Color.parseColor("#1C1B1F"))
                }
                tvStatus?.text = "HAZIR"
            }
        }
        
        // CLOSE button action
        btnClose.setOnClickListener {
            stopSelf()
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                disableSelf()
            }
        }

        // Beautiful dragging logic
        var initialX = 0
        var initialY = 0
        var initialTouchX = 0f
        var initialTouchY = 0f

        floatingView?.setOnTouchListener { view, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    initialX = layoutParams.x
                    initialY = layoutParams.y
                    initialTouchX = event.rawX
                    initialTouchY = event.rawY
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    // Update layout parameter coordinates
                    // Screen layout parameters are relative to the gravity settings
                    val diffX = (event.rawX - initialTouchX).toInt()
                    val diffY = (event.rawY - initialTouchY).toInt()
                    
                    // Since gravity includes Gravity.END, moving left on overlay decreases X
                    layoutParams.x = initialX - diffX
                    layoutParams.y = initialY + diffY
                    windowManager?.updateViewLayout(floatingView, layoutParams)
                    true
                }
                MotionEvent.ACTION_UP -> {
                    val diffX = (event.rawX - initialTouchX).toInt()
                    val diffY = (event.rawY - initialTouchY).toInt()
                    if (Math.abs(diffX) < 10 && Math.abs(diffY) < 10) {
                        view.performClick()
                    }
                    true
                }
                else -> false
            }
        }

        windowManager?.addView(floatingView, layoutParams)
    }

    private fun startTyping() {
        val phrase = TyperState.activePhrase.value
        if (phrase == null) {
            tvStatus?.text = "YAZI SEÇ!"
            return
        }

        btnPlay?.text = "STOP"
        btnPlay?.background = GradientDrawable().apply {
            shape = GradientDrawable.OVAL
            setColor(Color.parseColor("#FFB4AB")) // Distinct coral stop style
        }
        tvStatus?.text = "YAZIYOR..."
        TyperState.isRunning.value = true

        typingJob = scope.launch(Dispatchers.IO) {
            val root = rootInActiveWindow
            val focusedNode = findFocusedNode(root)
            
            if (focusedNode != null && focusedNode.isEditable) {
                for (i in 1..phrase.repeatCount) {
                    val bundle = Bundle()
                    // Set typing text
                    bundle.putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, phrase.text)
                    focusedNode.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, bundle)
                    
                    // Update status label count progress
                    launch(Dispatchers.Main) {
                        tvStatus?.text = "$i/${phrase.repeatCount}"
                    }
                    delay(phrase.intervalMs)
                }
            }
            
            launch(Dispatchers.Main) {
                TyperState.isRunning.value = false
                btnPlay?.text = "PLAY"
                btnPlay?.background = GradientDrawable().apply {
                    shape = GradientDrawable.OVAL
                    setColor(Color.parseColor("#1C1B1F"))
                }
                tvStatus?.text = "TAMAM"
            }
        }
    }

    private fun findFocusedNode(node: AccessibilityNodeInfo?): AccessibilityNodeInfo? {
        if (node == null) return null
        if (node.isFocused && node.isEditable) return node
        for (i in 0 until node.childCount) {
            val child = node.getChild(i)
            val result = findFocusedNode(child)
            if (result != null) return result
        }
        return null
    }

    private fun stopTyping() {
        typingJob?.cancel()
        TyperState.isRunning.value = false
        scope.launch(Dispatchers.Main) {
            tvStatus?.text = "DURDU"
            btnPlay?.text = "PLAY"
            btnPlay?.background = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(Color.parseColor("#1C1B1F"))
            }
        }
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event == null) return
        
        // Real-time automatic learning feature! 
        // If learning mode is active, any text written on screen is memorized instantly.
        if (isLearningMode) {
            if (event.eventType == AccessibilityEvent.TYPE_VIEW_TEXT_CHANGED) {
                val text = event.text?.joinToString("") ?: ""
                if (text.isNotBlank() && text != "null") {
                    learnedText = text
                    
                    // Pre-fill active system configuration
                    val count = TyperState.activePhrase.value?.repeatCount ?: 10
                    val interval = TyperState.activePhrase.value?.intervalMs ?: 500
                    TyperState.activePhrase.value = Phrase(text = text, repeatCount = count, intervalMs = interval)
                    
                    scope.launch(Dispatchers.Main) {
                        tvStatus?.text = "EZBER: $text"
                    }
                }
            }
        }
    }

    override fun onInterrupt() {
        stopTyping()
    }

    override fun onDestroy() {
        super.onDestroy()
        stopTyping()
        floatingView?.let {
            windowManager?.removeView(it)
        }
    }
}
