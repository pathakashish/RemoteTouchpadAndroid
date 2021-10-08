package com.developerspace.webrtcsample

import android.accessibilityservice.AccessibilityService
import android.content.Intent
import android.graphics.Rect
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityManager
import android.view.accessibility.AccessibilityNodeInfo

const val TAG = "AccessibilityService"

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
                Log.v(TAG, "TYPE_VIEW_TEXT_TRAVERSED_AT_MOVEMENT_GRANULARITY Text: " + event.source?.text)
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

    fun performClickEventOnNodeAtGivenCoordinates(
        rootNode: AccessibilityNodeInfo? = rootInActiveWindow,
        x: Int,
        y: Int
    ) {

        rootNode?.let { accessibilityNodeInfo ->
            for (i in 0 until accessibilityNodeInfo.childCount) {
                accessibilityNodeInfo.getChild(i)?.let { nodeInfo ->
                    if (nodeInfo.childCount > 0) {
                        performClickEventOnNodeAtGivenCoordinates(nodeInfo, x, y)
                    }
                    if (nodeInfo.isClickable) {
                        val rect = Rect()
                        nodeInfo.getBoundsInScreen(rect)
                        Log.v(
                            "EVENT",
                            "Node " + nodeInfo.text + " Rect = Left: " + rect.left + ", Top: " + rect.top + ", Right: " + rect.right + ", Bottom: " + rect.bottom
                        )
                        if (rect.contains(x, y)) {
                            nodeInfo.performAction(AccessibilityNodeInfo.ACTION_CLICK)
                        }
                    }
                }
            }
        }
    }

    companion object {

        private var sharedServiceInstance: RTCAccessibilityService? = null

        fun getSharedAccessibilityServiceInstance(): RTCAccessibilityService? {
            return sharedServiceInstance
        }
    }
}