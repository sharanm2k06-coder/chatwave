package com.chatwave.app.feature.call

import android.content.Context
import org.json.JSONObject
import org.webrtc.AudioSource
import org.webrtc.AudioTrack
import org.webrtc.IceCandidate
import org.webrtc.MediaConstraints
import org.webrtc.PeerConnection
import org.webrtc.PeerConnectionFactory
import org.webrtc.SessionDescription

class WebRtcAudioClient(
    context: Context,
    private val onSignal: (String, JSONObject) -> Unit,
    private val onState: (String) -> Unit,
) {
    private val factory: PeerConnectionFactory
    private val source: AudioSource
    private val peer: PeerConnection by lazy {
        val config = PeerConnection.RTCConfiguration(
            listOf(PeerConnection.IceServer.builder("stun:stun.l.google.com:19302").createIceServer()),
        )
        requireNotNull(factory.createPeerConnection(config, observer))
    }
    private val track: AudioTrack

    init {
        PeerConnectionFactory.initialize(
            PeerConnectionFactory.InitializationOptions.builder(context.applicationContext).createInitializationOptions(),
        )
        factory = PeerConnectionFactory.builder().createPeerConnectionFactory()
        source = factory.createAudioSource(MediaConstraints())
        track = factory.createAudioTrack("chatwave-audio", source)
        peer.addTrack(track)
    }

    fun createOffer() {
        peer.createOffer(sdpObserver { description ->
            peer.setLocalDescription(sdpObserver {
                onSignal("offer", JSONObject().put("type", description.type.canonicalForm()).put("sdp", description.description))
            }, description)
        }, MediaConstraints())
    }

    fun acceptOffer(data: JSONObject) {
        val remote = SessionDescription(
            SessionDescription.Type.fromCanonicalForm(data.getString("type")),
            data.getString("sdp"),
        )
        peer.setRemoteDescription(sdpObserver {
            peer.createAnswer(sdpObserver { answer ->
                peer.setLocalDescription(sdpObserver {
                    onSignal("answer", JSONObject().put("type", answer.type.canonicalForm()).put("sdp", answer.description))
                }, answer)
            }, MediaConstraints())
        }, remote)
    }

    fun acceptAnswer(data: JSONObject) {
        peer.setRemoteDescription(sdpObserver {}, SessionDescription(
            SessionDescription.Type.fromCanonicalForm(data.getString("type")), data.getString("sdp"),
        ))
    }

    fun addIce(data: JSONObject) {
        peer.addIceCandidate(IceCandidate(data.getString("sdpMid"), data.getInt("sdpMLineIndex"), data.getString("candidate")))
    }

    fun setMuted(value: Boolean) {
        track.setEnabled(!value)
    }

    fun close() {
        peer.close()
        track.dispose()
        source.dispose()
        factory.dispose()
    }

    private val observer = object : PeerConnection.Observer {
        override fun onIceCandidate(candidate: IceCandidate) {
            onSignal("ice", JSONObject().put("sdpMid", candidate.sdpMid).put("sdpMLineIndex", candidate.sdpMLineIndex).put("candidate", candidate.sdp))
        }
        override fun onConnectionChange(newState: PeerConnection.PeerConnectionState) { onState(newState.name) }
        override fun onSignalingChange(p0: PeerConnection.SignalingState?) = Unit
        override fun onIceConnectionChange(p0: PeerConnection.IceConnectionState?) = Unit
        override fun onIceConnectionReceivingChange(p0: Boolean) = Unit
        override fun onIceGatheringChange(p0: PeerConnection.IceGatheringState?) = Unit
        override fun onIceCandidatesRemoved(p0: Array<out IceCandidate>?) = Unit
        override fun onAddStream(p0: org.webrtc.MediaStream?) = Unit
        override fun onRemoveStream(p0: org.webrtc.MediaStream?) = Unit
        override fun onDataChannel(p0: org.webrtc.DataChannel?) = Unit
        override fun onRenegotiationNeeded() = Unit
        override fun onAddTrack(p0: org.webrtc.RtpReceiver?, p1: Array<out org.webrtc.MediaStream>?) = Unit
    }

    private fun sdpObserver(onCreate: (SessionDescription) -> Unit = {}) = object : org.webrtc.SdpObserver {
        override fun onCreateSuccess(description: SessionDescription) = onCreate(description)
        override fun onSetSuccess() = Unit
        override fun onCreateFailure(error: String?) { onState("failed") }
        override fun onSetFailure(error: String?) { onState("failed") }
    }
}
