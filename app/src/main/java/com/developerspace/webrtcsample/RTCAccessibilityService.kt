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

    private var touch: GestureDescription.StrokeDescription? = null
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

    var downX: Int = 0
    var downY: Int = 0
    var isOnClick: Boolean = false
    val SCROLL_THRESHOLD: Int = 10
    var path: Path? = null
    var inprogress = false
    var xEventTime = 0L

    @RequiresApi(Build.VERSION_CODES.O)
    fun performEvent(hashData: HashMap<*, *>) {
        Log.v(TAG, "hashData: $hashData")
        val x = hashData["x"] as Int
        val y = hashData["y"] as Int
        val eventTime: Long = (hashData["eventTime"] as Int).toLong()
        var duration: Long
        duration = if(xEventTime == 0L || eventTime == xEventTime) {
            10L
        } else {
            abs(eventTime - xEventTime)
        }
        xEventTime = eventTime
        Log.v(TAG, "Event ${hashData["action"]} Duration: $duration X: $x , Y: $y")
        when (hashData["action"] as Int and MotionEvent.ACTION_MASK) {
            MotionEvent.ACTION_DOWN -> {
                downX = x
                downX = y
                isOnClick = true
                Log.v(TAG, "ACTION_DOWN Move To:-  x: $x, y: $y")
                if (path == null) {
                    path = Path()
                    path?.moveTo(x.toFloat(), y.toFloat())
                }
            }
            MotionEvent.ACTION_MOVE -> {
                // For MotionEvent.ACTION_MOVE
                // Gesture will always be true
                // Send XY and when received by phone under control send lineTo
                if (isOnClick && (Math.abs(downX - x) > SCROLL_THRESHOLD || Math.abs(downY - y) > SCROLL_THRESHOLD)) {
                    isOnClick = false
                }
                if(!isOnClick) {
                    path?.lineTo(x.toFloat(), y.toFloat())
                    touch = GestureDescription.StrokeDescription(
                        path!!,
                        0L,
                        duration // TODO Adjust this with eventTime from last event
                    )
                }
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                // For MotionEvent.ACTION_MOVE
                // Gesture will always be true
                // Send XY and when received by phone under control send lineTo
                //touch?.continueStroke(Path().apply { lineTo(x, y) }, 0, 12L, false)
                if (isOnClick) {
                    duration = 10L
                    Log.v(TAG, "ACTION_UP Click:-  x: $x, y: $y")
                } else {
                    path?.lineTo(x.toFloat(), y.toFloat())
                    Log.v(TAG, "ACTION_UP Drag Line To:-  x: $x, y: $y")
                }
                touch = GestureDescription.StrokeDescription(
                    path!!,
                    0L,
                    duration // TODO Adjust this with eventTime from last event
                )
                downX = 0
                downY = 0
                path?.close()
                path = null
                xEventTime = 0L
            }
        }
        touch?.let {
            val gestureBuilder = GestureDescription.Builder()
            gestureBuilder.addStroke(it)
            inprogress = true
            dispatchGesture(
                gestureBuilder.build(), object : GestureResultCallback() {
                    override fun onCompleted(gestureDescription: GestureDescription?) {
                        super.onCompleted(gestureDescription)
                        Log.v(TAG, "onCompleted")
                        inprogress = false
                        touch = null
                    }

                    override fun onCancelled(gestureDescription: GestureDescription?) {
                        super.onCancelled(gestureDescription)
                        Log.v(TAG, "onCancelled")
                        inprogress = false
                        touch = null
                    }
                },
                null
            )
        }
    }

    companion object {

        private var sharedServiceInstance: RTCAccessibilityService? = null

        fun getSharedAccessibilityServiceInstance(): RTCAccessibilityService? {
            return sharedServiceInstance
        }
    }
}