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
import android.view.WindowManager
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import android.widget.Button
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.app.NotificationCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class AutoTyperService : AccessibilityService() {

    private var lastInteractedNode: AccessibilityNodeInfo? = null
    private var windowManager: WindowManager? = null
    
    // Transparent overlay container holding our views
    private var containerView: FrameLayout? = null
    private var expandedView: View? = null
    private var minimizedView: View? = null
    
    private val scope = CoroutineScope(Dispatchers.Main + Job())
    private var typingJob: Job? = null

    // Layout configuration parameters
    private lateinit var layoutParams: WindowManager.LayoutParams

    // Service status labels
    private var tvStatus: TextView? = null
    private var btnPlay: Button? = null
    private var btnRec: Button? = null

    companion object {
        const val ACTION_STOP_SERVICE = "STOP_SERVICE"
        const val ACTION_SHOW_OVERLAY = "SHOW_OVERLAY"
        const val NOTIFICATION_ID = 1001
        const val CHANNEL_ID = "AutoTyperChannel"
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP_SERVICE -> {
                stopSelf()
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                    disableSelf()
                }
            }
            ACTION_SHOW_OVERLAY -> {
                TyperState.isOverlayHidden.value = false
            }
        }
        return super.onStartCommand(intent, flags, startId)
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        createNotificationChannel()
        startForeground(NOTIFICATION_ID, createNotification())
        setupFloatingLayout()
        
        // Listen to active model state changes to adjust transparency, layouts dynamically
        scope.launch {
            TyperState.floatingAlpha.collect { alpha ->
                expandedView?.alpha = alpha
                minimizedView?.alpha = alpha
            }
        }
        scope.launch {
            TyperState.isMinimized.collect { min ->
                updateViewStates(min)
            }
        }
        scope.launch {
            TyperState.isOverlayHidden.collect { hidden ->
                containerView?.visibility = if (hidden) View.GONE else View.VISIBLE
            }
        }
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

        val showIntent = Intent(this, AutoTyperService::class.java).apply {
            action = ACTION_SHOW_OVERLAY
        }
        val pendingShowIntent = PendingIntent.getService(
            this, 1, showIntent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Auto Typer Aktif (Yazıcı)")
            .setContentText("Menüyü öne çıkarmak için tıklayın")
            .setSmallIcon(android.R.drawable.ic_menu_edit)
            .setContentIntent(pendingShowIntent)
            .addAction(android.R.drawable.ic_menu_view, "Menüyü Göster", pendingShowIntent)
            .addAction(android.R.drawable.ic_delete, "Durdur", pendingStopIntent)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    }

    @SuppressLint("ClickableViewAccessibility", "SetTextI18n")
    private fun setupFloatingLayout() {
        if (containerView != null) return
        
        windowManager = getSystemService(Context.WINDOW_SERVICE) as WindowManager
        
        layoutParams = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY else WindowManager.LayoutParams.TYPE_PHONE,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.TRANSLUCENT
        )
        layoutParams.gravity = Gravity.CENTER_VERTICAL or Gravity.END
        layoutParams.x = 0
        layoutParams.y = 100

        containerView = FrameLayout(this)

        // --- 1. EXPANDED VIEW SETUP ---
        val expandedLayout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            setPadding(12, 16, 12, 16)
            background = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                setColor(Color.parseColor("#E62B2930")) // Sophisticated dark background with subtle transparency
                cornerRadii = floatArrayOf(24f, 24f, 0f, 0f, 0f, 0f, 24f, 24f) // Left side curves only
                setStroke(2, Color.parseColor("#44D0BCFF")) // Dreamy soft purple neon stroke
            }
        }

        tvStatus = TextView(this).apply {
            text = "HAZIR"
            textSize = 9.5f
            setTextColor(Color.parseColor("#D0BCFF"))
            gravity = Gravity.CENTER
            setPadding(4, 2, 4, 8)
            typeface = android.graphics.Typeface.DEFAULT_BOLD
        }
        expandedLayout.addView(tvStatus)

        // Helper button builder
        fun createStyleButton(label: String, colorHex: String): Button {
            return Button(this).apply {
                text = label
                setTextColor(Color.WHITE)
                textSize = 9f
                setPadding(0, 0, 0, 0)
                gravity = Gravity.CENTER
                typeface = android.graphics.Typeface.DEFAULT_BOLD
                background = GradientDrawable().apply {
                    shape = GradientDrawable.OVAL
                    setColor(Color.parseColor(colorHex))
                }
                layoutParams = LinearLayout.LayoutParams(96, 96).apply {
                    setMargins(0, 6, 0, 6)
                }
            }
        }

        btnPlay = createStyleButton("PLAY", "#1C1B1F")
        btnRec = createStyleButton("REC", "#1C1B1F")
        
        // Minimize toggle trigger
        val btnMin = createStyleButton("MIN", "#E631111D")

        // Hide overlay trigger
        val btnHide = createStyleButton("GİZL", "#625B71")
        
        // Direct close app service trigger
        val btnClose = createStyleButton("X", "#93000A")

        expandedLayout.addView(btnPlay)
        expandedLayout.addView(btnRec)
        expandedLayout.addView(btnMin)
        expandedLayout.addView(btnHide)
        expandedLayout.addView(btnClose)

        // Simple touch draggable accent lines helper
        val linesLayout = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
            layoutParams = LinearLayout.LayoutParams(40, 10).apply {
                setMargins(0, 10, 0, 0)
            }
            addView(View(this@AutoTyperService).apply {
                background = Color.parseColor("#66D0BCFF").toDrawable()
                layoutParams = LinearLayout.LayoutParams(6, 6).apply { setMargins(2, 0, 2, 0) }
            })
            addView(View(this@AutoTyperService).apply {
                background = Color.parseColor("#66D0BCFF").toDrawable()
                layoutParams = LinearLayout.LayoutParams(6, 6).apply { setMargins(2, 0, 2, 0) }
            })
            addView(View(this@AutoTyperService).apply {
                background = Color.parseColor("#66D0BCFF").toDrawable()
                layoutParams = LinearLayout.LayoutParams(6, 6).apply { setMargins(2, 0, 2, 0) }
            })
        }
        expandedLayout.addView(linesLayout)

        expandedView = expandedLayout

        // --- 2. MINIMIZED VIEW SETUP ---
        val minLayout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setPadding(8, 8, 8, 8)
            background = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(Color.parseColor("#FF4F378B")) // Energetic visual layout color
                setStroke(3, Color.parseColor("#D0BCFF"))
            }
            layoutParams = FrameLayout.LayoutParams(110, 110)
        }

        minLayout.addView(TextView(this).apply {
            text = "◀"
            textSize = 14f
            setTextColor(Color.WHITE)
            gravity = Gravity.CENTER
            typeface = android.graphics.Typeface.DEFAULT_BOLD
        })

        minimizedView = minLayout

        // Bundle clicks
        btnPlay?.setOnClickListener {
            if (typingJob?.isActive == true) {
                stopTyping()
            } else {
                startTyping()
            }
        }

        btnRec?.setOnClickListener {
            val activeLearn = !TyperState.learnMode.value
            TyperState.learnMode.value = activeLearn
            updateRecButtonIndicator(activeLearn)
        }

        btnMin?.setOnClickListener {
            TyperState.isMinimized.value = true
        }

        btnHide.setOnClickListener {
            TyperState.isOverlayHidden.value = true
        }

        minimizedView?.setOnClickListener {
            TyperState.isMinimized.value = false
        }

        btnClose.setOnClickListener {
            stopSelf()
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                disableSelf()
            }
        }

        // Add both views into parent container
        containerView?.addView(expandedView)
        containerView?.addView(minimizedView)
        
        // Initially show based on current State
        updateViewStates(TyperState.isMinimized.value)

        // Smooth Floating Drag Listener support
        setupDraggableHandler()

        windowManager?.addView(containerView, layoutParams)
    }

    private fun updateRecButtonIndicator(isLearning: Boolean) {
        if (isLearning) {
            btnRec?.text = "LISN"
            btnRec?.background = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(Color.parseColor("#FF625B71")) // Glow color
            }
            tvStatus?.text = "DİNLİYOR"
        } else {
            btnRec?.text = "REC"
            btnRec?.background = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(Color.parseColor("#1C1B1F"))
            }
            tvStatus?.text = "HAZIR"
        }
    }

    private fun updateViewStates(minimized: Boolean) {
        expandedView?.visibility = if (minimized) View.GONE else View.VISIBLE
        minimizedView?.visibility = if (minimized) View.VISIBLE else View.GONE
    }

    @SuppressLint("ClickableViewAccessibility")
    private fun setupDraggableHandler() {
        var initialX = 0
        var initialY = 0
        var initialTouchX = 0f
        var initialTouchY = 0f

        val dragListener = View.OnTouchListener { view, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    initialX = layoutParams.x
                    initialY = layoutParams.y
                    initialTouchX = event.rawX
                    initialTouchY = event.rawY
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    val diffX = (event.rawX - initialTouchX).toInt()
                    val diffY = (event.rawY - initialTouchY).toInt()
                    
                    // Since dynamic orientation is END aligned
                    layoutParams.x = initialX - diffX
                    layoutParams.y = initialY + diffY
                    windowManager?.updateViewLayout(containerView, layoutParams)
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
        
        expandedView?.setOnTouchListener(dragListener)
        minimizedView?.setOnTouchListener(dragListener)
    }

    private fun findTargetInputNode(): AccessibilityNodeInfo? {
        // Option 1: Last interacted node (the exact one the user typed on or clicked)
        val lastNode = lastInteractedNode
        if (lastNode != null) {
            try {
                if (lastNode.refresh() && lastNode.isEnabled && lastNode.isEditable) {
                    return lastNode
                }
            } catch (e: Exception) {
                // Ignore refresh/access errors
            }
        }
        
        // Option 2: Active focused node in the active window
        val rootNode = rootInActiveWindow
        val focusedNode = findFocusedNode(rootNode)
        if (focusedNode != null) {
            return focusedNode
        }
        
        // Option 3: Any editable node on the active screen
        val editableNode = findEditableInputNode(rootNode)
        if (editableNode != null) {
            return editableNode
        }
        
        return null
    }

    private fun findEditableInputNode(node: AccessibilityNodeInfo?): AccessibilityNodeInfo? {
        if (node == null) return null
        if (node.isEditable) return node
        for (i in 0 until node.childCount) {
            val child = node.getChild(i)
            val result = findEditableInputNode(child)
            if (result != null) return result
        }
        return null
    }

    private fun findSendButtonInSiblings(node: AccessibilityNodeInfo?): Boolean {
        if (node == null) return false
        val parent = node.parent ?: return false
        for (i in 0 until parent.childCount) {
            val sibling = parent.getChild(i) ?: continue
            if (sibling.isClickable) {
                val txt = sibling.text?.toString()?.lowercase() ?: ""
                val desc = sibling.contentDescription?.toString()?.lowercase() ?: ""
                val viewId = sibling.viewIdResourceName?.lowercase() ?: ""
                
                if (txt.contains("gönder") || txt.contains("gonder") || txt.contains("send") || txt.contains("yolla") || txt.contains("ilet") || txt.contains("submit") ||
                    desc.contains("gönder") || desc.contains("gonder") || desc.contains("send") || desc.contains("yolla") || desc.contains("ilet") || desc.contains("submit") ||
                    viewId.contains("send") || viewId.contains("gonder") || viewId.contains("submit") || viewId.contains("btn")
                ) {
                    sibling.performAction(AccessibilityNodeInfo.ACTION_CLICK)
                    return true
                }
            }
        }
        return false
    }

    private fun startTyping() {
        val phrase = TyperState.activePhrase.value
        if (phrase == null) {
            tvStatus?.text = "YAZI YOK"
            return
        }

        btnPlay?.text = "STOP"
        btnPlay?.background = GradientDrawable().apply {
            shape = GradientDrawable.OVAL
            setColor(Color.parseColor("#FFB4AB"))
        }
        tvStatus?.text = "BAŞLADI"
        TyperState.isRunning.value = true

        typingJob = scope.launch(Dispatchers.IO) {
            val autoSendState = TyperState.autoSend.value
            
            for (i in 1..phrase.repeatCount) {
                val targetInput = findTargetInputNode()
                
                if (targetInput != null) {
                    val bundle = Bundle()
                    bundle.putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, phrase.text)
                    targetInput.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, bundle)

                    // Auto Send click action
                    if (autoSendState) {
                        delay(120) // Brief delay to let the input register
                        val rootNode = rootInActiveWindow
                        findAndClickSendButton(rootNode) || findSendButtonInSiblings(targetInput)
                    }

                    launch(Dispatchers.Main) {
                        tvStatus?.text = "$i/${phrase.repeatCount}"
                    }
                } else {
                    launch(Dispatchers.Main) {
                        tvStatus?.text = "ODAK YOK"
                    }
                }
                
                delay(phrase.intervalMs)
            }
            
            // Record run summary in database log
            try {
                AppDatabase.getDatabase(this@AutoTyperService).phraseDao().insertLog(
                    TyperLog(phraseText = phrase.text, count = phrase.repeatCount)
                )
            } catch (e: Exception) {
                // Ignore silent DB crashes
            }

            launch(Dispatchers.Main) {
                TyperState.isRunning.value = false
                btnPlay?.text = "PLAY"
                btnPlay?.background = GradientDrawable().apply {
                    shape = GradientDrawable.OVAL
                    setColor(Color.parseColor("#1C1B1F"))
                }
                tvStatus?.text = "BİTTİ"
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

    private fun findAndClickSendButton(node: AccessibilityNodeInfo?): Boolean {
        if (node == null) return false
        
        if (node.isClickable) {
            val txt = node.text?.toString()?.lowercase() ?: ""
            val desc = node.contentDescription?.toString()?.lowercase() ?: ""
            val viewId = node.viewIdResourceName?.lowercase() ?: ""
            
            if (txt.contains("gönder") || txt.contains("gonder") || txt.contains("send") || txt.contains("yolla") || txt.contains("ilet") || txt.contains("submit") || txt.contains("paylaş") ||
                desc.contains("gönder") || desc.contains("gonder") || desc.contains("send") || desc.contains("yolla") || desc.contains("ilet") || desc.contains("submit") || desc.contains("paylaş") ||
                viewId.contains("send") || viewId.contains("gonder") || viewId.contains("submit") || viewId.contains("buttonsend") || viewId.contains("btn_send") || viewId.contains("btnsend")
            ) {
                node.performAction(AccessibilityNodeInfo.ACTION_CLICK)
                return true
            }
        }
        
        for (i in 0 until node.childCount) {
            val child = node.getChild(i)
            if (findAndClickSendButton(child)) {
                return true
            }
        }
        return false
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

        // Cache the last interacted node immediately if it is editable
        val source = event.source
        if (source != null) {
            try {
                if (source.isEditable) {
                    lastInteractedNode?.recycle()
                    lastInteractedNode = AccessibilityNodeInfo.obtain(source)
                }
            } catch (e: Exception) {
                // Ignore silent tracker issues
            }
        }
        
        // Listen to text inputs if user is typing on normal fields in other apps
        if (TyperState.learnMode.value) {
            if (event.eventType == AccessibilityEvent.TYPE_VIEW_TEXT_CHANGED) {
                val text = event.text?.joinToString("") ?: ""
                if (text.isNotBlank() && text != "null" && text.length > 2) {
                    TyperState.lastLearnedText.value = text
                    
                    // Create/update active phrase configuration settings
                    val count = TyperState.activePhrase.value?.repeatCount ?: 10
                    val interval = TyperState.activePhrase.value?.intervalMs ?: 500
                    TyperState.activePhrase.value = Phrase(text = text, repeatCount = count, intervalMs = interval)
                    
                    scope.launch(Dispatchers.Main) {
                        tvStatus?.text = "KOPYALANDI"
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
        lastInteractedNode?.recycle()
        lastInteractedNode = null
        containerView?.let {
            windowManager?.removeView(it)
        }
    }

    // Direct helper to create background resources inside accessible context
    private fun Int.toDrawable(): GradientDrawable {
        return GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            setColor(this@toDrawable)
            cornerRadius = 10f
        }
    }
}
