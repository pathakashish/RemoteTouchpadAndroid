package com.developerspace.webrtcsample

import android.app.Application
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import android.view.MotionEvent
import androidx.annotation.RequiresApi
import com.google.firebase.firestore.ktx.firestore
import com.google.firebase.ktx.Firebase
import io.ktor.util.*
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject
import org.webrtc.*
import org.webrtc.PeerConnection.RTCConfiguration
import java.nio.ByteBuffer

private const val TAG = "RTCClient"

@InternalAPI
class RTCClient(
    context: Application,
    observer: PeerConnection.Observer
) {

    companion object {
        private const val LOCAL_TRACK_ID = "local_track"
        private const val LOCAL_STREAM_ID = "local_track"
    }

    private var clientDataChannel: DataChannel? = null
    private var senderDataChannel: DataChannel? = null
    private var screenShareCapturer: VideoCapturer? = null
    private val rootEglBase: EglBase = EglBase.create()

    private var localAudioTrack: AudioTrack? = null
    private var localVideoTrack: VideoTrack? = null
    private lateinit var localVideoStream: MediaStream
    var isClient: Boolean = false

    var remoteSessionDescription: SessionDescription? = null
    var rtcClientListener: RTCClientListener? = null

    val db = Firebase.firestore

    init {
        initPeerConnectionFactory(context)
    }

    private val iceServer = listOf(
        PeerConnection.IceServer.builder("stun:stun.l.google.com:19302")
            .createIceServer(),
        PeerConnection.IceServer.builder("stun:stun2.l.google.com:19302")
            .createIceServer(),
        PeerConnection.IceServer.builder("stun:stun3.l.google.com:19302")
            .createIceServer(),
        PeerConnection.IceServer.builder("stun:stun4.l.google.com:19302")
            .createIceServer()
    )

    private val peerConnectionFactory by lazy { buildPeerConnectionFactory() }
    private val videoCapturer by lazy { getVideoCapturer(context) }

    private val audioSource by lazy {
        peerConnectionFactory.createAudioSource(MediaConstraints().apply {
            optional.add(MediaConstraints.KeyValuePair("RtpDataChannels", "false"))
            optional.add(MediaConstraints.KeyValuePair("DtlsSrtpKeyAgreement", "true"))
        })
    }
    private val localVideoSource by lazy { peerConnectionFactory.createVideoSource(false) }
    private val localVideoForSharingSource by lazy { peerConnectionFactory.createVideoSource(true) }
    private val peerConnection by lazy { buildPeerConnection(observer) }

    private fun initPeerConnectionFactory(context: Application) {
        val options = PeerConnectionFactory.InitializationOptions.builder(context)
            .setEnableInternalTracer(false)
            .setFieldTrials("WebRTC-IntelVP8/Enabled/")
            .createInitializationOptions()
        PeerConnectionFactory.initialize(options)
    }

    private fun buildPeerConnectionFactory(): PeerConnectionFactory {
        return PeerConnectionFactory
            .builder()
            .setVideoDecoderFactory(DefaultVideoDecoderFactory(rootEglBase.eglBaseContext))
            .setVideoEncoderFactory(
                DefaultVideoEncoderFactory(
                    rootEglBase.eglBaseContext,
                    true,
                    true
                )
            )
            .setOptions(PeerConnectionFactory.Options().apply {
                networkIgnoreMask = 0
            })
            .createPeerConnectionFactory()
    }

    private fun buildPeerConnection(observer: PeerConnection.Observer): PeerConnection? {
        val rtcConfig = RTCConfiguration(iceServer)
        // TCP candidates are only useful when connecting to a server that supports
        // ICE-TCP.
        rtcConfig.tcpCandidatePolicy = PeerConnection.TcpCandidatePolicy.DISABLED
        rtcConfig.bundlePolicy = PeerConnection.BundlePolicy.MAXBUNDLE
        rtcConfig.rtcpMuxPolicy = PeerConnection.RtcpMuxPolicy.REQUIRE
        rtcConfig.continualGatheringPolicy =
            PeerConnection.ContinualGatheringPolicy.GATHER_CONTINUALLY
        // Use ECDSA encryption.
        rtcConfig.keyType = PeerConnection.KeyType.ECDSA
        // Enable DTLS for normal calls and disable for loopback calls.
        rtcConfig.enableDtlsSrtp = true
        val peerConnection = peerConnectionFactory.createPeerConnection(
            rtcConfig,
            observer
        )
        createDataChannel(peerConnection!!)
        return peerConnection
    }

    private fun getVideoCapturer(context: Context) =
        Camera2Enumerator(context).run {
            deviceNames.find {
                isFrontFacing(it)
            }?.let {
                createCapturer(it, null)
            } ?: throw IllegalStateException()
        }

    fun initSurfaceView(view: SurfaceViewRenderer) = view.run {
        setMirror(false)
        setEnableHardwareScaler(true)
        init(rootEglBase.eglBaseContext, null)
    }

    fun startLocalVideoCapture(localVideoOutput: SurfaceViewRenderer) {
        //stopScreenShare(screenShareCaptuer)
        if (screenShareCapturer != null) {
            screenShareCapturer?.stopCapture()
        }

        val surfaceTextureHelper =
            SurfaceTextureHelper.create(Thread.currentThread().name, rootEglBase.eglBaseContext)
        (videoCapturer as VideoCapturer).initialize(
            surfaceTextureHelper,
            localVideoOutput.context,
            localVideoSource.capturerObserver
        )
        videoCapturer.startCapture(1280, 720, 30)

        localAudioTrack =
            peerConnectionFactory.createAudioTrack(LOCAL_TRACK_ID + "_audio", audioSource);
        localVideoTrack = peerConnectionFactory.createVideoTrack(LOCAL_TRACK_ID, localVideoSource)
        localVideoStream = peerConnectionFactory.createLocalMediaStream(LOCAL_STREAM_ID)

        localVideoTrack?.addSink(localVideoOutput)
        localVideoStream.addTrack(localVideoTrack)
        localVideoStream.addTrack(localAudioTrack)
        peerConnection?.addStream(localVideoStream)
    }

    fun startScreenSharing(
        data: Intent,
        localVideoOutput: SurfaceViewRenderer,
        capturer: VideoCapturer
    ) {
        Log.d("Rtc", "startScreenSharing()")
        //stopCameraShare(localVideoOutput)
        videoCapturer.stopCapture()
        screenShareCapturer = capturer
        val surfaceTextureHelper =
            SurfaceTextureHelper.create(Thread.currentThread().name, rootEglBase.eglBaseContext)
        screenShareCapturer?.initialize(
            surfaceTextureHelper,
            localVideoOutput.context,
            localVideoForSharingSource.capturerObserver
        )
        screenShareCapturer?.startCapture(1920, 1080, 60)
        localAudioTrack = peerConnectionFactory.createAudioTrack("ARDAMSa0" + "_audio", audioSource)
        localVideoTrack =
            peerConnectionFactory.createVideoTrack("ARDAMSv0", localVideoForSharingSource)
        localVideoTrack?.addSink(localVideoOutput)
        val localStream = peerConnectionFactory.createLocalMediaStream("ARDAMS")
        localStream.addTrack(localVideoTrack)
        localStream.addTrack(localAudioTrack)
        peerConnection?.addStream(localStream)
    }


    private fun PeerConnection.call(
        sdpObserver: SdpObserver,
        meetingID: String,
        screenShare: Boolean? = false
    ) {
        val constraints = MediaConstraints().apply {
            optional.add(MediaConstraints.KeyValuePair("RtpDataChannels", "false"))
            optional.add(MediaConstraints.KeyValuePair("DtlsSrtpKeyAgreement", "true"))
        }
        Log.v(TAG, "call screenshare:$screenShare")

        createOffer(object : SdpObserver by sdpObserver {
            override fun onCreateSuccess(desc: SessionDescription?) {
                setLocalDescription(object : SdpObserver {
                    override fun onSetFailure(p0: String?) {
                        Log.e(TAG, "onSetFailure789: $p0")
                    }

                    override fun onSetSuccess() {
                        val offer = hashMapOf(
                            "sdp" to desc?.description,
                            "type" to desc?.type,
                            "sharingScreen" to screenShare
                        )
                        db.collection("calls").document(meetingID)
                            .set(offer)
                            .addOnSuccessListener {
                                Log.e(TAG, "DocumentSnapshot added")
                            }
                            .addOnFailureListener { e ->
                                Log.e(TAG, "Error adding document", e)
                            }
                        Log.e(TAG, "onSetSuccess")
                    }

                    override fun onCreateSuccess(p0: SessionDescription?) {
                        Log.e(TAG, "createOffer - onCreateSuccess: Description $p0")
                    }

                    override fun onCreateFailure(p0: String?) {
                        Log.e(TAG, "createOffer - onCreateFailure: $p0")
                    }
                }, desc)
                sdpObserver.onCreateSuccess(desc)
            }

            override fun onSetFailure(p0: String?) {
                Log.e(TAG, "createOffer - onSetFailure456: $p0")
            }

            override fun onCreateFailure(p0: String?) {
                Log.e(TAG, "createOffer - onCreateFailure: $p0")
            }
        }, constraints)
    }

    private fun PeerConnection.answer(
        sdpObserver: SdpObserver,
        meetingID: String,
        shareScreen: Boolean? = false
    ) {
        val constraints = MediaConstraints().apply {
            optional.add(MediaConstraints.KeyValuePair("RtpDataChannels", "false"))
            optional.add(MediaConstraints.KeyValuePair("DtlsSrtpKeyAgreement", "true"))
        }
        Log.v(TAG, "answer screenshare:$shareScreen")

        createAnswer(object : SdpObserver by sdpObserver {
            override fun onCreateSuccess(desc: SessionDescription?) {
                val answer = hashMapOf(
                    "sdp" to desc?.description,
                    "type" to desc?.type,
                    "sharingScreen" to shareScreen

                )
                db.collection("calls").document(meetingID)
                    .set(answer)
                    .addOnSuccessListener {
                        Log.e(TAG, "DocumentSnapshot added")
                    }
                    .addOnFailureListener { e ->
                        Log.e(TAG, "Error adding document", e)
                    }
                setLocalDescription(object : SdpObserver {
                    override fun onSetFailure(p0: String?) {
                        Log.e(TAG, "createAnswer - onSetFailure123: $p0")
                    }

                    override fun onSetSuccess() {
                        Log.e(TAG, "createAnswer - onSetSuccess")
                    }

                    override fun onCreateSuccess(p0: SessionDescription?) {
                        Log.e(TAG, "createAnswer - onCreateSuccess: Description $p0")
                    }

                    override fun onCreateFailure(p0: String?) {
                        Log.e(TAG, "createAnswer - onCreateFailure: $p0")
                    }
                }, desc)
                sdpObserver.onCreateSuccess(desc)
            }

            override fun onCreateFailure(p0: String?) {
                Log.e(TAG, "onCreateFailureRemote: $p0")
            }
        }, constraints)
    }

    fun call(sdpObserver: SdpObserver, meetingID: String, screenShare: Boolean? = false) =
        peerConnection?.call(sdpObserver, meetingID, screenShare)

    fun answer(sdpObserver: SdpObserver, meetingID: String, screenShare: Boolean? = false) =
        peerConnection?.answer(sdpObserver, meetingID, screenShare)

    fun onRemoteSessionReceived(sessionDescription: SessionDescription) {
        remoteSessionDescription = sessionDescription
        peerConnection?.setRemoteDescription(object : SdpObserver {
            override fun onSetFailure(p0: String?) {
                Log.e(TAG, "onRemoteSessionReceived - onSetFailure: $p0")
            }

            override fun onSetSuccess() {
                Log.e(TAG, "onRemoteSessionReceived - onSetSuccess")

            }

            override fun onCreateSuccess(p0: SessionDescription?) {
                Log.e(TAG, "onRemoteSessionReceived - onCreateSuccess: Description $p0")
            }

            override fun onCreateFailure(p0: String?) {
                Log.e(TAG, "onRemoteSessionReceived - onCreateFailure")
            }
        }, sessionDescription)

    }

    fun addIceCandidate(iceCandidate: IceCandidate?) {
        peerConnection?.addIceCandidate(iceCandidate)
    }

    @RequiresApi(Build.VERSION_CODES.N)
    fun endCall(meetingID: String) {
        db.collection("calls").document(meetingID).collection("candidates")
            .get().addOnSuccessListener {
                val iceCandidateArray: MutableList<IceCandidate> = mutableListOf()
                for (dataSnapshot in it) {
                    if (dataSnapshot.contains("type") && dataSnapshot["type"] == "offerCandidate") {
                        val offerCandidate = dataSnapshot
                        iceCandidateArray.add(
                            IceCandidate(
                                offerCandidate["sdpMid"].toString(),
                                Math.toIntExact(offerCandidate["sdpMLineIndex"] as Long),
                                offerCandidate["sdp"].toString()
                            )
                        )
                    } else if (dataSnapshot.contains("type") && dataSnapshot["type"] == "answerCandidate") {
                        val answerCandidate = dataSnapshot
                        iceCandidateArray.add(
                            IceCandidate(
                                answerCandidate["sdpMid"].toString(),
                                Math.toIntExact(answerCandidate["sdpMLineIndex"] as Long),
                                answerCandidate["sdp"].toString()
                            )
                        )
                    }
                }
                peerConnection?.removeIceCandidates(iceCandidateArray.toTypedArray())
            }
        val endCall = hashMapOf(
            "type" to "END_CALL"
        )
        db.collection("calls").document(meetingID)
            .set(endCall)
            .addOnSuccessListener {
                Log.e(TAG, "DocumentSnapshot added")
            }
            .addOnFailureListener { e ->
                Log.e(TAG, "Error adding document", e)
            }

        peerConnection?.close()
    }

    fun updateShareScreen(meetingID: String, isSharingScree: Boolean) {

        db.collection("calls").document(meetingID)
            .update(
                mapOf(
                    "sharingScreen" to isSharingScree
                )
            )
    }

    fun enableVideo(videoEnabled: Boolean) {
        if (localVideoTrack != null)
            localVideoTrack?.setEnabled(videoEnabled)
    }

    private fun stopCameraShare(view: SurfaceViewRenderer) {
        videoCapturer.stopCapture()
        localVideoTrack?.removeSink(view)
        localVideoStream.removeTrack(localVideoTrack)
        localVideoStream.removeTrack(localAudioTrack)
        localVideoTrack?.dispose()
        localAudioTrack?.dispose()
        senderDataChannel?.dispose()
    }


    /*private fun stopScreenShare(view: VideoCapturer){
        videoCapturer.stopCapture()
        localVideoTrack?.removeSink(view)
        localVideoStream.removeTrack(localVideoTrack)
        localVideoStream.removeTrack(localAudioTrack)
        localVideoTrack?.dispose()
        localAudioTrack?.dispose()
    }*/

    fun enableAudio(audioEnabled: Boolean) {
        if (localAudioTrack != null)
            localAudioTrack?.setEnabled(audioEnabled)
    }

    fun switchCamera() {
        videoCapturer.switchCamera(null)
    }

    fun sendLiveEvents(meetingID: String, event: MotionEvent) {
        val eventHash = mutableMapOf(
            "x" to event.x.toInt(),
            "y" to event.y.toInt(),
            "eventTime" to event.eventTime,
            "action" to event.action,
        ) as java.util.HashMap<*, *>
        if(false == senderDataChannel?.send(
            DataChannel.Buffer(
                ByteBuffer.wrap(
                    JSONObject(eventHash).toString().toByteArray()
                ), false
            )
        )) {
            Log.w(TAG, "Failed to send data on commandsDataChannel")
        }
    }

    @Throws(JSONException::class)
    private fun toMap(jsonobj: JSONObject): Map<String, Any> {
        val map: MutableMap<String, Any> = HashMap()
        val keys = jsonobj.keys()
        while (keys.hasNext()) {
            val key = keys.next()
            var value = jsonobj[key]
            if (value is JSONArray) {
                value = toList(value)
            } else if (value is JSONObject) {
                value = toMap(value)
            }
            map[key] = value
        }
        return map
    }

    @Throws(JSONException::class)
    private fun toList(array: JSONArray): List<Any> {
        val list: MutableList<Any> = ArrayList()
        for (i in 0 until array.length()) {
            var value = array[i]
            if (value is JSONArray) {
                value = toList(value)
            } else if (value is JSONObject) {
                value = toMap(value)
            }
            list.add(value)
        }
        return list
    }

    fun listenToLiveEvents() {
        if (!this.isClient) return

        clientDataChannel?.registerObserver(object : DataChannel.Observer {
            override fun onBufferedAmountChange(size: Long) {
                Log.v(TAG, "Client.onBufferedAmountChange - size: $size")
            }

            override fun onStateChange() {
                Log.v(TAG, "Client.onStateChange")
            }

            override fun onMessage(message: DataChannel.Buffer?) {
                Log.v(
                    TAG,
                    "Client.onMessage - message: ${String(message?.data!!.moveToByteArray())}"
                )
                message.data?.rewind()
                val eventHash =
                    toMap(JSONObject(String(message.data!!.moveToByteArray()))) as HashMap<*, *>
                rtcClientListener?.onEventReceive(eventHash)
            }

        })

    }

    private fun createDataChannel(peerConnection: PeerConnection) {
        Log.v(TAG, "Creating data channel... peerConnection: $peerConnection")
        senderDataChannel =
            peerConnection.createDataChannel("commands data", DataChannel.Init())
        Log.v(TAG, "Created data channel for commands: $senderDataChannel")
        senderDataChannel?.registerObserver(object : DataChannel.Observer {
            override fun onBufferedAmountChange(size: Long) {
                Log.v(TAG, "Server.onBufferedAmountChange - size: $size")
            }

            override fun onStateChange() {
                Log.v(TAG, "Server.onStateChange")
            }

            override fun onMessage(message: DataChannel.Buffer?) {
                Log.v(
                    TAG,
                    "Server.onBufferedAmountChange - size: ${String(message!!.data!!.moveToByteArray())}"
                )
            }
        })
    }

    fun setClientDataChannel(dataChannel: DataChannel?) {
        clientDataChannel = dataChannel
    }

}

interface RTCClientListener {

    fun onEventReceive(hashData: HashMap<*, *>)

}