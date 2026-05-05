package dev.serge.chatapplication

import android.content.Context
import android.util.Log
import androidx.compose.runtime.mutableStateListOf
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ValueEventListener
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.webrtc.AudioSource
import org.webrtc.AudioTrack
import org.webrtc.Camera1Enumerator
import org.webrtc.Camera2Enumerator
import org.webrtc.CameraVideoCapturer
import org.webrtc.DataChannel
import org.webrtc.DefaultVideoDecoderFactory
import org.webrtc.DefaultVideoEncoderFactory
import org.webrtc.EglBase
import org.webrtc.IceCandidate
import org.webrtc.MediaConstraints
import org.webrtc.MediaStream
import org.webrtc.PeerConnection
import org.webrtc.PeerConnectionFactory
import org.webrtc.RtpReceiver
import org.webrtc.SdpObserver
import org.webrtc.SessionDescription
import org.webrtc.SurfaceTextureHelper
import org.webrtc.VideoCapturer
import org.webrtc.VideoSource
import org.webrtc.VideoTrack

class WebRTCManager(
    context: Context,
    private val currentUserId: String,
    private val otherUserId: String
) {
    private val applicationContext = context
    private var peerConnectionFactory: PeerConnectionFactory? = null
    private var peerConnection: PeerConnection? = null
    private var audioSource: AudioSource? = null
    private var audioTrack: AudioTrack? = null
    private var videoSource: VideoSource? = null
    private var videoTrack = MutableStateFlow<VideoTrack?>(null)
    private var remoteVideoTrack = MutableStateFlow<VideoTrack?>(null)
    private var localVideoTrack: VideoTrack? = null
    var _videoTrack = videoTrack.asStateFlow()
    var _remoteVideoTrack = remoteVideoTrack.asStateFlow()

    //    private var cameraCapture: CameraVideoCapturer? = null
    private var videoCapturer: VideoCapturer? = null
    private var surfaceTexture: SurfaceTextureHelper? = null
    lateinit var eglBase: EglBase
    private var eglBaseContext = MutableStateFlow<EglBase.Context?>(null)
    var _eglBaseContext = eglBaseContext.asStateFlow()
    private var localMediaStream: MediaStream? = null
    private val db = FirebaseDatabase.getInstance().reference

    private var signalingValueEventListener: ValueEventListener? = null
    private var callEndValueEventListener: ValueEventListener? = null

    private var isDisposed = false
    private var isOfferHandled = false
    private var isAnswerHandled = false
    private var addedIceCandidates = mutableSetOf<String>()
    private val pendingIceCandidates = mutableListOf<IceCandidate>()
    private var callerName: String = ""
    private var pendingIncomingCall = false
    private var pendingOffer: String? = null
    private var _onIncomingCall: (() -> Unit)? = null
    var onIncomingCall: (() -> Unit)?
        get() = _onIncomingCall
        set(value) {
            _onIncomingCall = value
            if (pendingIncomingCall && pendingOffer != null) {
                pendingIncomingCall = false
                val offer = pendingOffer!!
                pendingOffer = null
                value?.invoke()
                handleIncomingCall(offer)
            }
        }

    private var callStarted = false
    private var isHandleIncomingCall = false
    private var isFrontCamera = true

    var onRemoteStreamAdded: (MediaStream) -> Unit = {}
    var onRemoteStreamRemoved: (MediaStream) -> Unit = {}
    var onConnectionStateChanged: (PeerConnection.IceConnectionState) -> Unit = {}
    var onCallEnded: (() -> Unit)? = null

    private fun generateCallId(user1: String, user2: String): String {
        return listOf(user1, user2).sorted().joinToString("_")
    }

    private val callId = generateCallId(currentUserId, otherUserId)

    init {
        initializePeerConnectionFactory()
        connectToSignalingServer()
        listenForCallEnd()
        Log.d("INSAF", "Current: $currentUserId Other: $otherUserId")
    }

    private fun initializePeerConnectionFactory() {
        try {
            val options = PeerConnectionFactory.InitializationOptions.builder(applicationContext)
                .setEnableInternalTracer(true)
                .createInitializationOptions()

            PeerConnectionFactory.initialize(options)

            eglBase = EglBase.create()
            eglBaseContext.value = eglBase.eglBaseContext

            peerConnectionFactory = PeerConnectionFactory.builder()
                .setOptions(PeerConnectionFactory.Options())
                .setVideoEncoderFactory(
                    DefaultVideoEncoderFactory(
                        eglBaseContext.value,
                        true,
                        true
                    )
                )
                .setVideoDecoderFactory(DefaultVideoDecoderFactory(eglBaseContext.value))
                .createPeerConnectionFactory()
            Log.d("WebRTC", "PeerConnectionFactory Initialized")
        } catch (e: Exception) {
            Log.e("WebRTC", "Error establishing connection ${e.message}")
        }
    }

    private fun connectToSignalingServer() {
        if (isDisposed) return
        try {
            val listener = object : ValueEventListener {
                override fun onDataChange(snapshot: DataSnapshot) {
                    if (isDisposed) return
                    val callData = snapshot.value as? Map<*, *> ?: return
                    Log.d("WebRTC", "currentUserId: $currentUserId")
                    Log.d("WebRTC", "offerFrom: ${callData["offerFrom"]}")
                    Log.d("WebRTC", "answerFrom: ${callData["answerFrom"]}")

                    val status = callData["status"] as? Boolean
                    if (status == true) {
                        onCallEnded?.invoke()
                        return
                    }

                    val offerFrom = callData["offerFrom"] as? String
                    val answerFrom = callData["answerFrom"] as? String

                    val offer = callData["offer"] as? String
                    if (offer != null && !isOfferHandled &&
                        peerConnection == null &&
                        offerFrom == otherUserId
                    ) {
                        isOfferHandled = true
                        isHandleIncomingCall = true
                        Log.d("WebRTC", "Offer received")
                        if (_onIncomingCall != null) {
                            _onIncomingCall?.invoke()
                            handleIncomingCall(offer)
                        } else {
                            Log.d("WebRTC", "No callback yet")
                            pendingIncomingCall = true
                            pendingOffer = offer
                        }
                    }

                    val answer = callData["answer"] as? String
                    if (answer != null && !isAnswerHandled &&
                        peerConnection != null &&
                        answerFrom == otherUserId
                    ) {
                        isAnswerHandled = true
                        Log.d("WebRTC", "Answer from $answerFrom - handling answer")
                        handleCallAnswered(answer)
                    }

                    val iceCandidates = callData["iceCandidates_$otherUserId"] as? Map<*, *>
                    Log.d("WebRTC", "ICE Count: ${iceCandidates?.size ?: 0}")
                    iceCandidates?.forEach { (key, candidate) ->
                        if (candidate is Map<*, *> && peerConnection != null) {
                            val candidateKey = key.toString()
                            if (!addedIceCandidates.contains(candidateKey)) {
                                try {
                                    val sdp = candidate["candidate"] as? String
                                    val sdpMid = candidate["sdpMid"] as? String
                                    val sdpMLineIndex =
                                        (candidate["sdpMLineIndex"] as? Long)?.toInt()
                                    if (sdp != null && sdpMid != null && sdpMLineIndex != null) {
                                        val iceCandidate = IceCandidate(sdpMid, sdpMLineIndex, sdp)
                                        if (peerConnection?.remoteDescription != null) {
                                            peerConnection?.addIceCandidate(iceCandidate)
                                        } else {
                                            pendingIceCandidates.add(iceCandidate)
                                        }
                                        addedIceCandidates.add(candidateKey)
                                    }
                                    Log.d("WebRTC", "ICE Candidate added")
                                } catch (e: Exception) {
                                    Log.e("WebRTC", "Error adding ICE: ${e.message}")
                                }
                            }
                        }
                    }
                }

                override fun onCancelled(error: DatabaseError) {
                    Log.e("WebRTC", "Firebase error: ${error.message}")
                }
            }
            signalingValueEventListener = listener
            db.child("webrtc_calls").child(callId).addValueEventListener(listener)
        } catch (e: Exception) {
            Log.e("WebRTC", "Error: ${e.message}")
        }
    }

    fun createLocalStream(): MediaStream {
        if (isDisposed) throw IllegalStateException("WebRTCManager is disposed")
        try {
            val audioConstraints = MediaConstraints().apply {
                mandatory.add(MediaConstraints.KeyValuePair("echoCancellation", "true"))
                mandatory.add(MediaConstraints.KeyValuePair("noiseSuppression", "true"))
                mandatory.add(MediaConstraints.KeyValuePair("autoGainControl", "true"))
            }

            audioSource = peerConnectionFactory?.createAudioSource(audioConstraints)
            audioTrack = peerConnectionFactory?.createAudioTrack("audio", audioSource)
            audioTrack?.setEnabled(true)

            val videoTrack = startLocalVideo()

            localMediaStream = peerConnectionFactory?.createLocalMediaStream("localStream")
            localMediaStream?.addTrack(audioTrack)

            if (videoTrack != null) {
                localMediaStream?.addTrack(videoTrack)
            }
            Log.d("WebRTC", "Local stream created")
            return localMediaStream!!
        } catch (e: Exception) {
            Log.e("WebRTC", "Error creating local stream ${e.message}")
            throw e
        }
    }

    fun createPeerConnection() {
        if (isDisposed) return
        val iceServers = listOf(
            PeerConnection.IceServer.builder("stun:stun.l.google.com:19302").createIceServer(),
            PeerConnection.IceServer.builder("stun:stun1.l.google.com:19302").createIceServer()
        )
        peerConnection = peerConnectionFactory?.createPeerConnection(
            iceServers,
            object : PeerConnection.Observer {
                override fun onSignalingChange(signalingState: PeerConnection.SignalingState) {
                    Log.d("WebRTC", "Signaling state: $signalingState")
                }

                override fun onIceConnectionChange(iceConnectionState: PeerConnection.IceConnectionState) {
                    Log.d("WebRTC", "ICE Connection State: $iceConnectionState")
                    onConnectionStateChanged(iceConnectionState)
                }

                override fun onIceGatheringChange(iceGatheringState: PeerConnection.IceGatheringState) {
                    Log.d("WebRTC", "New ICE Candidate $iceGatheringState")
                }

                override fun onIceCandidate(iceCandidate: IceCandidate?) {
                    Log.d("WebRTC", "New ICE Candidate")
                    iceCandidate?.let {
                        sendIceCandidate(iceCandidate)
                    }
                }

                override fun onAddStream(mediaStream: MediaStream) {
                    Log.d("WebRTC", "Remote stream added")
                    if (mediaStream.videoTracks.isNotEmpty()) {
                        remoteVideoTrack.value = mediaStream.videoTracks[0]
                    }
                    onRemoteStreamAdded(mediaStream)
                }

                override fun onRemoveStream(mediaStream: MediaStream) {
                    Log.d("WebRTC", "Media stream removed")
                    onRemoteStreamRemoved(mediaStream)
                }

                override fun onDataChannel(dataChannel: DataChannel) {
                    Log.d("WebRTC", "Data received channel: ${dataChannel.label()}")
                }

                override fun onRenegotiationNeeded() {
                    Log.d("WebRTC", "Renegotiation needed")
                }

                override fun onAddTrack(
                    rtpReceiver: RtpReceiver,
                    mediaStreams: Array<MediaStream>
                ) {
                    Log.d("WebRTC", "Track added: ${rtpReceiver.track()?.kind()}")
                    if (rtpReceiver.track()?.kind() == "video") {
                        remoteVideoTrack.value = rtpReceiver.track() as? VideoTrack
                    }
                }

                override fun onIceConnectionReceivingChange(receiving: Boolean) {
                    Log.d("WebRTC", "ICE Connection receiving: $receiving")
                }

                override fun onIceCandidatesRemoved(candidates: Array<IceCandidate>) {
                    Log.d("WebRTC", "ICE Candidate removed: ${candidates.size}")
                }
            }
        ) ?: run {
            Log.d("WebRTC", "peer connection not created")
            return
        }
    }

    fun initiateCall() {
        if (isDisposed) return
        try {
            if (peerConnection != null) {
                Log.w("WebRTC", "Peer connection already exists")
                return
            }
            createPeerConnection()

            audioTrack?.let { peerConnection?.addTrack(it) }
            localVideoTrack?.let { peerConnection?.addTrack(it) }

            peerConnection?.createOffer(
                object : SdpObserver {
                    override fun onCreateSuccess(sessionDescription: SessionDescription) {
                        peerConnection?.setLocalDescription(this, sessionDescription)
                    }

                    override fun onSetSuccess() {
                        val offer = peerConnection?.localDescription?.description ?: return
                        sendOffer(offer)
                    }

                    override fun onCreateFailure(s: String) {
                        Log.e("WebRTC", "Create offer failed $s")
                    }

                    override fun onSetFailure(s: String) {
                        Log.e("WebRTC", "Set local description failed: $s")
                    }
                },
                MediaConstraints()
            )
            Log.d("WebRTC", "Call initiated")
        } catch (e: Exception) {
            Log.e("WebRTC", "Error initiating call: ${e.message}")
        }
    }

    private fun handleIncomingCall(offer: String) {
        if (isDisposed) return
        try {
            Log.d("WebRTC", "Handle incoming call")
            if (peerConnection != null) {
                Log.w("WebRTC", "Peer connection already exists")
                return
            }

            createPeerConnection()
            Log.d("WebRTC", "Connection created for receiver")

            audioTrack?.let { peerConnection?.addTrack(it) }
            localVideoTrack?.let { peerConnection?.addTrack(it) }

            val sessionDescription = SessionDescription(
                SessionDescription.Type.OFFER,
                offer
            )

            Log.d("WebRTC", "remote offer received")

            peerConnection?.setRemoteDescription(
                object : SdpObserver {
                    override fun onSetSuccess() {
                        drainIceCandidates()
                    }

                    override fun onCreateFailure(s: String) {
                        Log.e("WebRTC", "Set remote description failed $s")
                    }

                    override fun onSetFailure(s: String) {
                        Log.e("WebRTC", "Set remote description failed $s")
                    }

                    override fun onCreateSuccess(p0: SessionDescription?) {}
                },
                sessionDescription
            )
            Log.d("WebRTC", "Incoming call handled")
        } catch (e: Exception) {
            Log.e("WebRTC", "Error handling incoming call: ${e.message}")
        }
    }

    fun acceptCall() {
        if (isDisposed) return
        Log.d("WebRTC", "Call accepted creating answer")
        createAnswer()
    }

    private fun listenForCallEnd() {
        if (isDisposed) return
        val listener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                if (isDisposed) return
                if ((callStarted || isHandleIncomingCall) && !snapshot.exists()) {
                    Log.d("WebRTC", "Call ended by other user")
                    onCallEnded?.invoke()
                }
            }

            override fun onCancelled(error: DatabaseError) {}
        }
        callEndValueEventListener = listener
        db.child("webrtc_calls").child(callId).addValueEventListener(listener)
    }

    private fun createAnswer() {
        if (isDisposed) return
        try {
            Log.d("WebRTC", "Creating answer..")
            peerConnection?.createAnswer(
                object : SdpObserver {
                    override fun onCreateSuccess(sessionDescription: SessionDescription) {
                        peerConnection?.setLocalDescription(this, sessionDescription)
                    }

                    override fun onSetSuccess() {
                        val answer = peerConnection?.localDescription?.description ?: return
                        Log.d("WebRTC", "answer created")
                        sendAnswer(answer)
                    }

                    override fun onCreateFailure(s: String) {
                        Log.e("WebRTC", "Create answer failed: $s")
                    }

                    override fun onSetFailure(s: String) {
                        Log.d("WebRTC", "Set local description failed: $s")
                    }
                },
                MediaConstraints()
            )
        } catch (e: Exception) {
            Log.e("WebRTC", "Error creating answer${e.message}")
        }
    }

    private fun handleCallAnswered(answer: String) {
        if (isDisposed) return
        try {
            val sessionDescription = SessionDescription(
                SessionDescription.Type.ANSWER,
                answer
            )
            peerConnection?.setRemoteDescription(
                object : SdpObserver {
                    override fun onSetSuccess() {
                        Log.d("WebRTC", "Remote description set successfully")
                        drainIceCandidates()
                    }

                    override fun onCreateSuccess(p0: SessionDescription?) {}
                    override fun onCreateFailure(s: String) {}
                    override fun onSetFailure(s: String) {
                        Log.e("WebRTC", "Set remote description failed: $s")
                    }
                },
                sessionDescription
            )
        } catch (e: Exception) {
            Log.e("WebRTC", "Error handling answer: ${e.message}")
        }
    }

    private fun drainIceCandidates() {
        pendingIceCandidates.forEach {
            peerConnection?.addIceCandidate(it)
        }
        pendingIceCandidates.clear()
    }

    private fun sendOffer(offer: String) {
        if (isDisposed) return
        callStarted = true
        val updates = mapOf(
            "offer" to offer,
            "offerFrom" to currentUserId,
            "timestamp" to System.currentTimeMillis()
        )
        val callRef = db.child("webrtc_calls").child(callId)
        callRef.updateChildren(updates)
        callRef.onDisconnect().removeValue()

        val notification = mapOf(
            "callerId" to currentUserId,
            "callerName" to callerName,
            "chatId" to callId,
            "timestamp" to System.currentTimeMillis()
        )
        db.child("incoming_calls")
            .child(otherUserId).setValue(notification)
            .addOnSuccessListener {
                Log.d("WebRTC", "Incoming call from $otherUserId")
            }
            .addOnFailureListener {
                Log.e("WebRTC", "Failed to send notification ${it.message}")
            }
        Log.d("WebRTC", "Offer sent to $otherUserId")
    }

    private fun sendAnswer(answer: String) {
        if (isDisposed) return
        callStarted = true
        val updates = mapOf(
            "answer" to answer,
            "answerFrom" to currentUserId,
            "timestamp" to System.currentTimeMillis()
        )
        val callRef = db.child("webrtc_calls").child(callId)
        callRef.updateChildren(updates)
        callRef.onDisconnect().removeValue()
        Log.d("WebRTC", "Answer sent to $otherUserId")
    }

    private fun sendIceCandidate(candidate: IceCandidate) {
        if (isDisposed) return
        val candidateData = mapOf(
            "candidate" to candidate.sdp,
            "sdpMid" to candidate.sdpMid,
            "sdpMLineIndex" to candidate.sdpMLineIndex
        )
        db.child("webrtc_calls").child(callId)
            .child("iceCandidates_$currentUserId")
            .push()
            .setValue(candidateData)
    }

    fun startCameraCapturer(): CameraVideoCapturer? {
        val enumerator = Camera2Enumerator(applicationContext)
//            Camera1Enumerator(true)
        for (devices in enumerator.deviceNames) {
            if (enumerator.isFrontFacing(devices)) {
                return enumerator.createCapturer(devices, null)
            }
        }
        return null
    }

    fun startLocalVideo(): VideoTrack? {
        if (peerConnectionFactory == null) {
            return null
        }
        if (localVideoTrack != null) {
            videoCapturer?.startCapture(720, 1280, 30)
            return localVideoTrack
        }
        if (videoCapturer == null) {
            videoCapturer = startCameraCapturer()
        }
        if (surfaceTexture == null) {
            surfaceTexture = SurfaceTextureHelper.create(
                "VideoCall",
                eglBase.eglBaseContext
            )
        }

        if (videoSource == null) {
            videoSource = peerConnectionFactory?.createVideoSource(false)
        }

        videoCapturer?.initialize(surfaceTexture, applicationContext, videoSource?.capturerObserver)
        videoCapturer?.startCapture(720, 1280, 30)

        localVideoTrack = peerConnectionFactory?.createVideoTrack("videoTrack", videoSource)
        localVideoTrack?.setEnabled(true)

        videoTrack.value = localVideoTrack!!

        return localVideoTrack
    }

    fun setCallerName(name: String) {
        callerName = name
    }

    fun setMicrophoneEnabled(enabled: Boolean) {
        if (isDisposed) return
        audioTrack?.setEnabled(enabled)
        Log.d("WebRTC", "Microphone ${if (enabled) "enabled" else "disabled"}")
    }

    fun endCall(rejected: Boolean = false) {
        if (isDisposed) return
        try {
            if (rejected) {
                db.child("webrtc_calls").child(callId).child("status").setValue(true)
            } else {
                db.child("webrtc_calls").child(callId).removeValue()
            }
            db.child("incoming_calls").child(otherUserId).removeValue()
            disconnect()
            Log.d("WebRTC", "Call ended")
        } catch (e: Exception) {
            Log.e("WebRTC", "Error handling call: ${e.message}")
        }
    }

    fun disconnect() {
        if (isDisposed) return
        isDisposed = true
        try {
            signalingValueEventListener?.let {
                db.child("webrtc_calls").child(callId).removeEventListener(it)
            }
            callEndValueEventListener?.let {
                db.child("webrtc_calls").child(callId).removeEventListener(it)
            }

            isAnswerHandled = false
            isOfferHandled = false
            isHandleIncomingCall = false
            callStarted = false
            addedIceCandidates.clear()
            pendingIceCandidates.clear()

            peerConnection?.close()
            peerConnection?.dispose()
            peerConnection = null

            audioTrack?.let { track ->
                localMediaStream?.removeTrack(track)
                track.setEnabled(false)
                track.dispose()
            }
            audioTrack = null

            audioSource?.dispose()
            audioSource = null

            videoCapturer?.stopCapture()
            videoCapturer?.dispose()
            videoCapturer = null

            surfaceTexture?.dispose()
            surfaceTexture = null

            localVideoTrack?.let { track ->
                localMediaStream?.removeTrack(track)
                track.setEnabled(false)
                track.dispose()
            }
            localVideoTrack = null

            videoSource?.dispose()
            videoSource = null

            localMediaStream?.dispose()
            localMediaStream = null

            peerConnectionFactory?.dispose()
            peerConnectionFactory = null

            if (::eglBase.isInitialized) {
                eglBase.release()
            }

            Log.d("WebRTC", "Disconnected and disposed")
        } catch (e: Exception) {
            Log.e("WebRTC", "Error disconnecting: ${e.message}")
        }
    }
}