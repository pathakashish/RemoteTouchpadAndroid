package com.developerspace.webrtcsample

import org.webrtc.IceCandidate
import org.webrtc.SessionDescription

interface SignalingClientListener {
    fun onConnectionEstablished()
    fun onOfferReceived(description: SessionDescription,boolean: Boolean)
    fun onAnswerReceived(description: SessionDescription,boolean: Boolean)
    fun onIceCandidateReceived(iceCandidate: IceCandidate)
    fun onCallEnded()
    fun onScreenShared(boolean: Boolean)

}