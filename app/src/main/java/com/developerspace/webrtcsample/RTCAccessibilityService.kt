package com.developerspace.webrtcsample

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.content.Intent
import android.graphics.Path
import android.os.Build
import android.util.Log
import android.view.MotionEvent
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityManager
import androidx.annotation.RequiresApi
import java.util.*
import kotlin.math.abs

private const val TAG = "RTCAccessibilityService"

class RTCAccessibilityService : AccessibilityService(),
    AccessibilityManager.TouchExplorationStateChangeListener {

    private lateinit var accessibilityManager: AccessibilityManager

    override fun onServiceConnected() {
        super.onServiceConnected()
        sharedServiceInstance = this
        initAccessibilityManager()
        initTouchExplorationCapability()
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
    }

    override fun onInterrupt() {
        // No implementation
    }

    override fun onUnbind(intent: Intent?): Boolean {
        sharedServiceInstance = null
        return super.onUnbind(intent)
    }


    private fun initTouchExplorationCapability() {
        accessibilityManager.addTouchExplorationStateChangeListener(this)
        if (accessibilityManager.isTouchExplorationEnabled) {
            requestTouchExploration()
        }
    }

    private fun initAccessibilityManager() {
        accessibilityManager = getSystemService(ACCESSIBILITY_SERVICE) as AccessibilityManager
    }

    override fun onTouchExplorationStateChanged(enabled: Boolean) {
        if (!enabled) {
            requestTouchExploration()
        }
    }

    private fun requestTouchExploration() {
        // Start an Intent to enable this service and touch exploration
    }

    private var downX: Int = 0
    private var downY: Int = 0
    private var xEventTime = 0L
    private var stroke: GestureDescription.StrokeDescription? = null

    @RequiresApi(Build.VERSION_CODES.O)
    fun performEvent(hashData: HashMap<*, *>) {
        Log.v(TAG, "hashData: $hashData")
        val x = hashData["x"] as Int
        val y = hashData["y"] as Int
        val eventTime: Long = (hashData["eventTime"] as Int).toLong()
        val duration =
            if (xEventTime == 0L || eventTime == xEventTime) 1L else abs(eventTime - xEventTime)
        Log.v(
            TAG,
            "Event ${hashData["action"]} Duration: $duration X: $x , Y: $y, downX: $downX, downY:: $downY eventTime: $eventTime"
        )
        xEventTime = eventTime
        when (hashData["action"] as Int and MotionEvent.ACTION_MASK) {
            MotionEvent.ACTION_DOWN -> {
                val path = Path()
                path.moveTo(x.toFloat(), y.toFloat())
                stroke = GestureDescription.StrokeDescription(path, 0, duration, true)
            }
            MotionEvent.ACTION_MOVE -> {
                val path = Path()
                path.moveTo(downX.toFloat(), downY.toFloat())
                path.lineTo(x.toFloat(), y.toFloat())
                stroke = stroke?.continueStroke(path, 0, duration, true)
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                val path = Path()
                path.moveTo(x.toFloat(), y.toFloat())
                stroke = stroke?.continueStroke(path, 0, duration, false)
                xEventTime = 0
            }
        }
        applyStroke(stroke!!)
        downX = x
        downY = y
    }

    @RequiresApi(Build.VERSION_CODES.N)
    private fun applyStroke(stroke: GestureDescription.StrokeDescription) {
        val gestureBuilder = GestureDescription.Builder()
        gestureBuilder.addStroke(stroke)
        dispatchGesture(
            gestureBuilder.build(), object : GestureResultCallback() {
                override fun onCompleted(gestureDescription: GestureDescription?) {
                    super.onCompleted(gestureDescription)
                    Log.v(TAG, "onCompleted")
                }

                override fun onCancelled(gestureDescription: GestureDescription?) {
                    super.onCancelled(gestureDescription)
                    Log.v(TAG, "onCancelled")
                }
            },
            null
        )
    }

    companion object {

        private var sharedServiceInstance: RTCAccessibilityService? = null

        fun getSharedAccessibilityServiceInstance(): RTCAccessibilityService? {
            return sharedServiceInstance
        }
    }
}