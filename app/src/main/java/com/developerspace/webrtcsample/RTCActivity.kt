package com.developerspace.webrtcsample

import android.Manifest
import android.annotation.SuppressLint
import android.content.*
import android.content.pm.PackageManager
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.text.TextUtils
import android.util.Log
import android.view.*
import android.view.View
import android.widget.Toast
import androidx.annotation.RequiresApi
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.core.view.isGone
import io.ktor.util.*
import kotlinx.android.synthetic.main.activity_main.*
import kotlinx.android.synthetic.main.activity_start.*
import kotlinx.coroutines.ExperimentalCoroutinesApi
import org.slf4j.helpers.Util
import org.webrtc.*
import java.util.*

private const val TAG = "RTCActivity"

@InternalAPI
@ExperimentalCoroutinesApi
class RTCActivity : AppCompatActivity(), GestureDetector.OnGestureListener {

    companion object {
        private const val CAMERA_AUDIO_PERMISSION_REQUEST_CODE = 1
        private const val CAMERA_PERMISSION = Manifest.permission.CAMERA
        private const val AUDIO_PERMISSION = Manifest.permission.RECORD_AUDIO
    }

    private val dataChannel: DataChannel? = null
    private var rtcAccessibilityService: RTCAccessibilityService? = null
    private lateinit var rtcClient: RTCClient
    private lateinit var signallingClient: SignalingClient

    private val audioManager by lazy { RTCAudioManager.create(this) }

    private var meetingID: String = "test-call"

    private var isJoin = false

    private var isMute = false

    private var isScreenShared = false

    private var isVideoPaused = false

    private var inSpeakerMode = true

    private val sdpObserver = object : AppSdpObserver() {
        override fun onCreateSuccess(p0: SessionDescription?) {
            super.onCreateSuccess(p0)
//            signallingClient.send(p0)
        }
    }

    @SuppressLint("ClickableViewAccessibility")
    @RequiresApi(Build.VERSION_CODES.N)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        requestWindowFeature(Window.FEATURE_NO_TITLE);
        this.getWindow().setFlags(
            WindowManager.LayoutParams.FLAG_FULLSCREEN,
            WindowManager.LayoutParams.FLAG_FULLSCREEN
        )

        setContentView(R.layout.activity_main)

        /*val gestureDetector = GestureDetector(applicationContext, this)
        view_whiteboard.setOnTouchListener { _, event ->
            gestureDetector.onTouchEvent(event)
            true
     }*/

        view_whiteboard.setOnTouchListener { _, event ->
            rtcClient.sendLiveEvents(meetingID, event!!)
            true
        }


        if (intent.hasExtra("meetingID"))
            meetingID = intent.getStringExtra("meetingID")!!
        if (intent.hasExtra("isJoin"))
            isJoin = intent.getBooleanExtra("isJoin", false)

        checkCameraAndAudioPermission()
        audioManager.selectAudioDevice(RTCAudioManager.AudioDevice.SPEAKER_PHONE)
        switch_camera_button.setOnClickListener {
            rtcClient.switchCamera()
        }

        audio_output_button.setOnClickListener {
            if (inSpeakerMode) {
                inSpeakerMode = false
                audio_output_button.setImageResource(R.drawable.ic_baseline_hearing_24)
                audioManager.setDefaultAudioDevice(RTCAudioManager.AudioDevice.EARPIECE)
            } else {
                inSpeakerMode = true
                audio_output_button.setImageResource(R.drawable.ic_baseline_speaker_up_24)
                audioManager.setDefaultAudioDevice(RTCAudioManager.AudioDevice.SPEAKER_PHONE)
            }
        }
        video_button.setOnClickListener {
            if (isVideoPaused) {
                isVideoPaused = false
                video_button.setImageResource(R.drawable.ic_baseline_videocam_off_24)
            } else {
                isVideoPaused = true
                video_button.setImageResource(R.drawable.ic_baseline_videocam_24)
            }
            rtcClient.enableVideo(isVideoPaused)
        }
        mic_button.setOnClickListener {
            if (isMute) {
                isMute = false
                mic_button.setImageResource(R.drawable.ic_baseline_mic_off_24)
            } else {
                isMute = true
                mic_button.setImageResource(R.drawable.ic_baseline_mic_24)
            }
            rtcClient.enableAudio(isMute)
        }
        end_call_button.setOnClickListener {
            endCall()
        }

        share_screen_button.setOnClickListener {
            if (isScreenShared) {
                share_screen_button.setImageResource(R.drawable.ic_baseline_screen_share_24)
                remote_control_button.visibility = View.GONE
                isScreenShared = false
                isVideoPaused = true
                rtcClient.enableVideo(isVideoPaused)
                rtcClient.updateShareScreen(meetingID, isScreenShared)
                stopScreenSharing()
            } else {
                share_screen_button.setImageResource(R.drawable.ic_baseline_stop_screen_share_24)
                isVideoPaused = true
                isScreenShared = true

                startScreenCapture()
                rtcClient.isClient = true
                rtcClient.listenToLiveEvents()
                remote_control_button.visibility = View.VISIBLE
            }
        }

        remote_control_button.setOnClickListener {

            if (!isMyAccessibilityServiceEnabled()) {
                val intent = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)
                intent.addCategory(Intent.CATEGORY_DEFAULT)
                startActivityForResult(intent, 90)
            } else {
                rtcAccessibilityService =
                    RTCAccessibilityService.getSharedAccessibilityServiceInstance()
            }
        }
    }

    private fun checkCameraAndAudioPermission() {
        if ((ContextCompat.checkSelfPermission(this, CAMERA_PERMISSION)
                    != PackageManager.PERMISSION_GRANTED) &&
            (ContextCompat.checkSelfPermission(this, AUDIO_PERMISSION)
                    != PackageManager.PERMISSION_GRANTED)
        ) {
            requestCameraAndAudioPermission()
        } else {
            onCameraAndAudioPermissionGranted()
        }
    }

    override fun onBackPressed() {
        AlertDialog.Builder(this)
            .setTitle("Are you sure to close the screen?")
            .setMessage("Your ongoing call will be disconnected!")
            .setPositiveButton("Yes", object : DialogInterface.OnClickListener {
                @RequiresApi(Build.VERSION_CODES.N)
                override fun onClick(p0: DialogInterface?, p1: Int) {
                    endCall()
                }
            })
            .setNegativeButton("No", object : DialogInterface.OnClickListener {
                override fun onClick(p0: DialogInterface?, p1: Int) {
                }
            })
            .show()
    }

    @RequiresApi(Build.VERSION_CODES.N)
    private fun endCall() {
        rtcClient.endCall(meetingID)
        remote_view.isGone = false
        Constants.isCallEnded = true
        finish()
        startActivity(Intent(this@RTCActivity, MainActivity::class.java))
    }

    private fun startScreenCapture() {
        if (Build.VERSION.SDK_INT >= 28) {
            // Start a foreground service and post notification regarding the screen share
            val intent = Intent(this, BackgroundService::class.java)
            intent.setAction(BackgroundService.ACTION_START_FOREGROUND_SERVICE)
            startService(intent)
            //bindService(intent,serviceConnection,Context.BIND_AUTO_CREATE)
        }
        val mediaProjectionManager =
            getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
        startActivityForResult(mediaProjectionManager.createScreenCaptureIntent(), 29)
    }

    private fun onCameraAndAudioPermissionGranted() {
        rtcClient = RTCClient(
            application,
            object : PeerConnectionObserver() {
                override fun onIceCandidate(p0: IceCandidate?) {
                    super.onIceCandidate(p0)
                    signallingClient.sendIceCandidate(p0, isJoin)
                    rtcClient.addIceCandidate(p0)
                }

                override fun onAddStream(mediaStream: MediaStream?) {
                    super.onAddStream(mediaStream)
                    Log.d(TAG, "onAddStream: $mediaStream")
                    mediaStream?.videoTracks?.get(0)?.addSink(remote_view)
                }

                override fun onIceConnectionChange(newState: PeerConnection.IceConnectionState?) {
                    Log.d(TAG, "onIceConnectionChange: $newState")
                }

                override fun onIceConnectionReceivingChange(p0: Boolean) {
                    Log.d(TAG, "onIceConnectionReceivingChange: $p0")
                }

                override fun onConnectionChange(newState: PeerConnection.PeerConnectionState?) {
                    Log.d(TAG, "onConnectionChange: $newState")
                }

                override fun onDataChannel(dataChannel: DataChannel?) {
                    Log.d(TAG, "onDataChannel: $dataChannel")
                    rtcClient.setClientDataChannel(dataChannel)
                }

                override fun onStandardizedIceConnectionChange(newState: PeerConnection.IceConnectionState?) {
                    Log.d(TAG, "onStandardizedIceConnectionChange: $newState")
                }

                override fun onAddTrack(p0: RtpReceiver?, p1: Array<out MediaStream>?) {
                    Log.d(TAG, "onAddTrack: $p0 \n $p1")
                }

                override fun onTrack(transceiver: RtpTransceiver?) {
                    Log.d(TAG, "onTrack: $transceiver")
                }
            }
        )

        rtcClient.initSurfaceView(remote_view)
        rtcClient.initSurfaceView(local_view)
        rtcClient.startLocalVideoCapture(local_view)
        //startScreenCapture()
        signallingClient = SignalingClient(meetingID, createSignallingClientListener())
        if (!isJoin)
            rtcClient.call(sdpObserver, meetingID)

        rtcClient.rtcClientListener = object : RTCClientListener {
            @RequiresApi(Build.VERSION_CODES.O)
            override fun onEventReceive(hashData: HashMap<*, *>) {
                Log.v(TAG, "x : " + hashData.get("x") + ", Y: " + hashData.get("y"))
                if (rtcAccessibilityService == null) rtcAccessibilityService =
                    RTCAccessibilityService.getSharedAccessibilityServiceInstance()

                rtcAccessibilityService?.performEvent(hashData)
            }
        }
    }

    private fun createSignallingClientListener() = object : SignalingClientListener {
        override fun onConnectionEstablished() {
            end_call_button.isClickable = true
        }

        override fun onOfferReceived(description: SessionDescription, sharingScreen: Boolean) {
            isScreenShared = sharingScreen
            rtcClient.onRemoteSessionReceived(description)
            Constants.isIntiatedNow = false
            rtcClient.answer(sdpObserver, meetingID, isScreenShared)
            remote_view_loading.isGone = true

            toggleLocalView()
            Log.v(TAG, "onOfferReceived :$isScreenShared")

        }

        override fun onAnswerReceived(description: SessionDescription, sharingScreen: Boolean) {
            isScreenShared = sharingScreen

            rtcClient.onRemoteSessionReceived(description)
            Constants.isIntiatedNow = false
            remote_view_loading.isGone = true

            toggleLocalView()
            Log.v(TAG, "onAnswerReceived :$isScreenShared")

        }

        override fun onIceCandidateReceived(iceCandidate: IceCandidate) {
            rtcClient.addIceCandidate(iceCandidate)
        }

        @RequiresApi(Build.VERSION_CODES.N)
        override fun onCallEnded() {
            // if (!Constants.isCallEnded) {
            Constants.isCallEnded = true
            rtcClient.endCall(meetingID)
            finish()
            startActivity(Intent(this@RTCActivity, MainActivity::class.java))
            //  }
        }

        override fun onScreenShared(boolean: Boolean) {
            Log.v(TAG, "onScreenShared: $boolean")

        }
    }

    private fun toggleLocalView(){
        if (isScreenShared) local_view.visibility = View.GONE
        else local_view.visibility = View.VISIBLE
    }

    private fun requestCameraAndAudioPermission(dialogShown: Boolean = false) {
        if (ActivityCompat.shouldShowRequestPermissionRationale(this, CAMERA_PERMISSION) &&
            ActivityCompat.shouldShowRequestPermissionRationale(this, AUDIO_PERMISSION) &&
            !dialogShown
        ) {
            showPermissionRationaleDialog()
        } else {
            ActivityCompat.requestPermissions(
                this,
                arrayOf(CAMERA_PERMISSION, AUDIO_PERMISSION),
                CAMERA_AUDIO_PERMISSION_REQUEST_CODE
            )
        }
    }

    private fun showPermissionRationaleDialog() {
        AlertDialog.Builder(this)
            .setTitle("Camera And Audio Permission Required")
            .setMessage("This app need the camera and audio to function")
            .setPositiveButton("Grant") { dialog, _ ->
                dialog.dismiss()
                requestCameraAndAudioPermission(true)
            }
            .setNegativeButton("Deny") { dialog, _ ->
                dialog.dismiss()
                onCameraPermissionDenied()
            }
            .show()
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == CAMERA_AUDIO_PERMISSION_REQUEST_CODE && grantResults.all { it == PackageManager.PERMISSION_GRANTED }) {
            onCameraAndAudioPermissionGranted()
        } else {
            onCameraPermissionDenied()
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == 29) {
            rtcClient.startScreenSharing(data!!, local_view, createScreenCapturer(data))
            rtcClient.call(sdpObserver, meetingID, isScreenShared)
        } else if (requestCode == 90) {
            rtcClient.call(sdpObserver, meetingID, isScreenShared)
            rtcAccessibilityService =
                RTCAccessibilityService.getSharedAccessibilityServiceInstance()
        }
    }

    private fun stopScreenSharing() {
        rtcClient.startLocalVideoCapture(local_view)
        // rtcClient.call(sdpObserver,meetingID)
    }

    private fun createScreenCapturer(data: Intent): VideoCapturer {
        return ScreenCapturerAndroid(
            data, object : MediaProjection.Callback() {
                override fun onStop() {
                    Util.report("User revoked permission to capture the screen.")
                }
            })
    }


    private fun onCameraPermissionDenied() {
        Toast.makeText(this, "Camera and Audio Permission Denied", Toast.LENGTH_LONG).show()
    }

    override fun onDestroy() {
        signallingClient.destroy()
        val intent = Intent(this, BackgroundService::class.java)
        intent.action = BackgroundService.ACTION_STOP_FOREGROUND_SERVICE
        startService(intent)
        super.onDestroy()
    }

    override fun onDown(e: MotionEvent?): Boolean {
        //rtcClient.sendLiveEvents(meetingID, e!!)
        return true
    }

    override fun onShowPress(e: MotionEvent?) {
        //rtcClient.sendLiveEvents(meetingID, e!!)
    }

    override fun onSingleTapUp(e: MotionEvent?): Boolean {
        //rtcClient.sendLiveEvents(meetingID, e!!)
        return true
    }

    override fun onScroll(
        e1: MotionEvent?,
        e2: MotionEvent?,
        distanceX: Float,
        distanceY: Float
    ): Boolean {
        return true
    }

    override fun onLongPress(e: MotionEvent?) {
        //rtcClient.sendLiveEvents(meetingID, e!!)
    }

    override fun onFling(
        e1: MotionEvent?,
        e2: MotionEvent?,
        velocityX: Float,
        velocityY: Float
    ): Boolean {
        return true
    }

    private fun isMyAccessibilityServiceEnabled(): Boolean {
        var accessibilityEnabled = 0
        val service = packageName + "/" + RTCAccessibilityService::class.java.canonicalName
        try {
            accessibilityEnabled = Settings.Secure.getInt(
                contentResolver,
                Settings.Secure.ACCESSIBILITY_ENABLED
            )
        } catch (e: Settings.SettingNotFoundException) {
            Log.e(
                MainActivity::class.java.simpleName,
                "Error finding setting, default accessibility to not found: ",
                e
            )
        }
        val mStringColonSplitter = TextUtils.SimpleStringSplitter(':')
        if (accessibilityEnabled == 1) {
            val settingValue: String = Settings.Secure.getString(
                contentResolver,
                Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
            )
            mStringColonSplitter.setString(settingValue)
            while (mStringColonSplitter.hasNext()) {
                val accessibilityService = mStringColonSplitter.next()
                if (accessibilityService.equals(service, ignoreCase = true)) {
                    return true
                }
            }
        } else {
            Log.v(MainActivity::class.java.simpleName, "***ACCESSIBILITY IS DISABLED***")
        }
        return false
    }
}