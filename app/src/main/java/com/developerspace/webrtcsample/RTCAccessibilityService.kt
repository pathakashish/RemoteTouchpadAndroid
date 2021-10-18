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

const val TAG = "RTCAccessibilityService"

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
        when (event?.eventType) {

            AccessibilityEvent.TYPE_VIEW_CLICKED -> {
                Log.v(TAG, "TYPE_VIEW_CLICKED Text: " + event.source?.text)
            }

            AccessibilityEvent.TYPE_ANNOUNCEMENT -> {
                Log.v(TAG, "TYPE_ANNOUNCEMENT Text: " + event.source?.text)
            }

            AccessibilityEvent.TYPE_ASSIST_READING_CONTEXT -> {
                Log.v(TAG, "TYPE_ASSIST_READING_CONTEXT Text: " + event.source?.text)
            }

            AccessibilityEvent.TYPE_GESTURE_DETECTION_END -> {
                Log.v(TAG, "TYPE_GESTURE_DETECTION_END Text: " + event.source?.text)
            }

            AccessibilityEvent.TYPE_GESTURE_DETECTION_START -> {
                Log.v(TAG, "TYPE_GESTURE_DETECTION_START Text: " + event.source?.text)
            }

            AccessibilityEvent.TYPE_NOTIFICATION_STATE_CHANGED -> {
                Log.v(TAG, "TYPE_NOTIFICATION_STATE_CHANGED Text: " + event.source?.text)
            }

            AccessibilityEvent.TYPE_TOUCH_EXPLORATION_GESTURE_END -> {
                Log.v(TAG, "TYPE_TOUCH_EXPLORATION_GESTURE_END Text: " + event.source?.text)
            }

            AccessibilityEvent.TYPE_TOUCH_EXPLORATION_GESTURE_START -> {
                Log.v(TAG, "TYPE_TOUCH_EXPLORATION_GESTURE_START Text: " + event.source?.text)
            }
            AccessibilityEvent.TYPE_TOUCH_INTERACTION_END -> {
                Log.v(TAG, "TYPE_TOUCH_INTERACTION_END Text: " + event.source?.text)
            }
            AccessibilityEvent.TYPE_TOUCH_INTERACTION_START -> {
                Log.v(TAG, "TYPE_TOUCH_INTERACTION_START Text: " + event.source?.text)
            }
            AccessibilityEvent.TYPE_VIEW_ACCESSIBILITY_FOCUSED -> {
                Log.v(TAG, "TYPE_VIEW_ACCESSIBILITY_FOCUSED Text: " + event.source?.text)
            }
            AccessibilityEvent.TYPE_VIEW_ACCESSIBILITY_FOCUS_CLEARED -> {
                Log.v(TAG, "TYPE_VIEW_ACCESSIBILITY_FOCUS_CLEARED Text: " + event.source?.text)
            }
            AccessibilityEvent.TYPE_VIEW_CONTEXT_CLICKED -> {
                Log.v(TAG, "TYPE_VIEW_CONTEXT_CLICKED Text: " + event.source?.text)
            }
            AccessibilityEvent.TYPE_VIEW_FOCUSED -> {
                Log.v(TAG, "TYPE_VIEW_FOCUSED Text: " + event.source?.text)
            }
            AccessibilityEvent.TYPE_VIEW_HOVER_ENTER -> {
                Log.v(TAG, "TYPE_VIEW_HOVER_ENTER Text: " + event.source?.text)
            }
            AccessibilityEvent.TYPE_VIEW_HOVER_EXIT -> {
                Log.v(TAG, "TYPE_VIEW_HOVER_EXIT Text: " + event.source?.text)
            }
            AccessibilityEvent.TYPE_VIEW_LONG_CLICKED -> {
                Log.v(TAG, "TYPE_VIEW_LONG_CLICKED Text: " + event.source?.text)
            }
            AccessibilityEvent.TYPE_VIEW_SCROLLED -> {
                Log.v(TAG, "TYPE_VIEW_SCROLLED Text: " + event.source?.text)
            }
            AccessibilityEvent.TYPE_VIEW_SELECTED -> {
                Log.v(TAG, "TYPE_VIEW_SELECTED Text: " + event.source?.text)
            }
            AccessibilityEvent.TYPE_VIEW_TEXT_CHANGED -> {
                Log.v(TAG, "TYPE_VIEW_TEXT_CHANGED Text: " + event.source?.text)
            }
            AccessibilityEvent.TYPE_VIEW_TEXT_SELECTION_CHANGED -> {
                Log.v(TAG, "TYPE_VIEW_TEXT_SELECTION_CHANGED Text: " + event.source?.text)
            }
            AccessibilityEvent.TYPE_VIEW_TEXT_TRAVERSED_AT_MOVEMENT_GRANULARITY -> {
                Log.v(
                    TAG,
                    "TYPE_VIEW_TEXT_TRAVERSED_AT_MOVEMENT_GRANULARITY Text: " + event.source?.text
                )
            }
            AccessibilityEvent.TYPE_WINDOWS_CHANGED -> {
                Log.v(TAG, "TYPE_WINDOWS_CHANGED Text: " + event.source?.text)
            }
            AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED -> {
                Log.v(TAG, "TYPE_WINDOW_CONTENT_CHANGED Text: " + event.source?.text)
            }
            AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED -> {
                Log.v(TAG, "TYPE_WINDOW_STATE_CHANGED Text: " + event.source?.text)
            }
        }
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

    var downX: Float = 0f
    var downY: Float = 0f
    var isOnClick: Boolean = false
    val SCROLL_THRESHOLD: Int = 10
    var path: Path? = null
    var inprogress = false
    var xEventTime = 0L

    @RequiresApi(Build.VERSION_CODES.O)
    fun performEvent(hashData: HashMap<*, *>) {
        val x = (hashData["x"] as Double).toFloat()
        val y = (hashData["y"] as Double).toFloat()
        val downTime: Long = hashData["downTime"] as Long
        val eventTime: Long = hashData["eventTime"] as Long
        var duration: Long
        duration = if(xEventTime == 0L || eventTime == xEventTime) {
            10L
        } else {
            abs(eventTime - xEventTime)
        }
        xEventTime = eventTime
        Log.v(TAG, "Event ${hashData["action"]} Duration: $duration X: $x , Y: $y")
        when ((hashData["action"] as Long).toInt() and MotionEvent.ACTION_MASK) {
            MotionEvent.ACTION_DOWN -> {
                downX = x
                downX = y
                isOnClick = true
                Log.v(TAG, "ACTION_DOWN Move To:-  x: $x, y: $y")
                if (path == null) {
                    path = Path()
                    path?.moveTo(x, y)
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
                    path?.lineTo(x, y)
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
                    path?.lineTo(x, y)
                    Log.v(TAG, "ACTION_UP Drag Line To:-  x: $x, y: $y")
                }
                touch = GestureDescription.StrokeDescription(
                    path!!,
                    0L,
                    duration // TODO Adjust this with eventTime from last event
                )
                downX = 0f
                downY = 0f
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