package dev.serge.chatapplication

import android.content.Context
import android.util.Log
import com.google.firebase.database.ChildEventListener
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ValueEventListener
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.webrtc.AudioSource
import org.webrtc.AudioTrack
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

class GroupRTCManager(
    context: Context,
    private val currentUserId: String,
    private val roomId: String
) {
    private val db = FirebaseDatabase.getInstance().reference

    private var factory: PeerConnectionFactory? = null
    private val peerConnections = mutableMapOf<String, PeerConnection>()
    private val audioTracks = mutableMapOf<String, AudioTrack>()

    private var localAudioTrack: AudioTrack? = null

    private val iceServers = listOf(
        PeerConnection.IceServer.builder("stun:stun.l.google.com:19302").createIceServer(),
        PeerConnection.IceServer.builder("stun:stun1.l.google.com:19302").createIceServer()
    )

    init {
        val options = PeerConnectionFactory.InitializationOptions.builder(context)
            .createInitializationOptions()

        PeerConnectionFactory.initialize(options)

        factory = PeerConnectionFactory.builder().createPeerConnectionFactory()

        createLocalAudio()
        listenForSignals()
    }

    private fun createLocalAudio() {
        val constraints = MediaConstraints().apply {
            mandatory.add(MediaConstraints.KeyValuePair("echoCancellation","true"))
            mandatory.add(MediaConstraints.KeyValuePair("noiseSuppression","true"))
        }

        val source = factory?.createAudioSource(constraints)
        localAudioTrack = factory?.createAudioTrack("audio",source)
        localAudioTrack?.setEnabled(true)
    }

    fun joinRoom() {
        val participantRef = db.child("groupCalls/$roomId/participants/$currentUserId")
        participantRef.setValue(true)

        db.child("groupCalls/$roomId/participants")
            .get()
            .addOnSuccessListener { snapshot ->
                snapshot.children.forEach {
                    val userId = it.key ?: return@forEach
                    if (userId != currentUserId) {
                        createConnection(userId)
                        createOffer(userId)
                    }
                }
            }
    }

    private fun createConnection(userId: String) {
        if (peerConnections.containsKey(userId)) return

        val pc = factory?.createPeerConnection(
            iceServers,
            object : PeerConnection.Observer {

                override fun onIceCandidate(candidate: IceCandidate) {
                    sendIceCandidate(userId, candidate)
                }

                override fun onAddTrack(
                    receiver: RtpReceiver,
                    mediaStreams: Array<out MediaStream>
                ) {
                    val track = receiver.track() as? AudioTrack ?: return
                    audioTracks[userId] = track
                }
                override fun onSignalingChange(state: PeerConnection.SignalingState) {}

                override fun onIceConnectionChange(state: PeerConnection.IceConnectionState) {}

                override fun onIceConnectionReceivingChange(p0: Boolean) {}

                override fun onIceGatheringChange(state: PeerConnection.IceGatheringState) {}

                override fun onIceCandidatesRemoved(p0: Array<out IceCandidate?>?) {}

                override fun onAddStream(p0: MediaStream?) {}

                override fun onRemoveStream(p0: MediaStream?) {}

                override fun onDataChannel(p0: DataChannel?) {}

                override fun onRenegotiationNeeded() {}
            }
        )

        localAudioTrack?.let { pc?.addTrack(it) }
        peerConnections[userId] = pc!!
    }

    private fun createOffer(targetUser: String) {
        val pc = peerConnections[targetUser] ?: return

        pc.createOffer(object : SdpObserver{
            override fun onCreateSuccess(sdp: SessionDescription) {
                pc.setLocalDescription(this, sdp)
            }

            override fun onSetSuccess() {
                val offer = pc.localDescription?.description ?: return
                db.child("groupCalls/$roomId/offers/${currentUserId}_$targetUser")
                    .setValue(offer)
            }

            override fun onCreateFailure(p0: String) {}
            override fun onSetFailure(p0: String) {}
        }, MediaConstraints())
    }

    private fun createAnswer(fromUser: String) {
        val pc = peerConnections[fromUser] ?: return

        pc.createAnswer(object : SdpObserver {
            override fun onCreateSuccess(sdp: SessionDescription) {
                pc.setLocalDescription(this, sdp)
            }

            override fun onSetSuccess() {
                val answer = pc.localDescription?.description ?: return
                db.child("groupCalls/$roomId/answers/${currentUserId}_$fromUser")
                    .setValue(answer)
            }

            override fun onCreateFailure(p0: String) {}
            override fun onSetFailure(p0: String) {}
        }, MediaConstraints())
    }

    private fun listenForSignals() {

        db.child("groupCalls/$roomId/offers")
            .addChildEventListener(object : ChildEventListener{
                override fun onChildAdded(snapshot: DataSnapshot, prev: String?) {
                    val key = snapshot.key ?: return
                    val (from, to) = key.split("_")

                    if (to != currentUserId) return

                    val offer = snapshot.getValue(String::class.java) ?: return

                    createConnection(from)
                    val pc = peerConnections[from]!!

                    pc.setRemoteDescription(object : SdpObserver{
                        override fun onSetSuccess() {
                            createAnswer(from)
                        }

                        override fun onCreateFailure(p0: String) {}
                        override fun onCreateSuccess(p0: SessionDescription) {}
                        override fun onSetFailure(p0: String) {}
                    }, SessionDescription(SessionDescription.Type.OFFER, offer))
                }

                override fun onChildChanged(p0: DataSnapshot, p1: String?) {}
                override fun onChildRemoved(p0: DataSnapshot) {}
                override fun onChildMoved(p0: DataSnapshot, p1: String?) {}
                override fun onCancelled(p0: DatabaseError) {}
            })

        db.child("groupCalls/$roomId/answers")
            .addChildEventListener(object : ChildEventListener{
                override fun onChildAdded(snapshot: DataSnapshot, prev: String?) {
                    val key = snapshot.key ?: return
                    val (from,to) = key.split("_")

                    if (to != currentUserId) return

                    val answer = snapshot.getValue(String::class.java) ?: return

                    val pc = peerConnections[from] ?: return

                    pc.setRemoteDescription(object : SdpObserver{
                        override fun onCreateSuccess(sdp: SessionDescription) {}
                        override fun onCreateFailure(p0: String) {}
                        override fun onSetSuccess() {}
                        override fun onSetFailure(p0: String) {}
                    }, SessionDescription(SessionDescription.Type.ANSWER, answer))
                }

                override fun onChildChanged(p0: DataSnapshot, p1: String?) {}
                override fun onChildMoved(p0: DataSnapshot, p1: String?) {}
                override fun onChildRemoved(p0: DataSnapshot) {}
                override fun onCancelled(p0: DatabaseError) {}
            })

        db.child("groupCalls/$roomId/ice")
            .addChildEventListener(object : ChildEventListener {
                override fun onChildAdded(snapshot: DataSnapshot, prev: String?) {
                    val key = snapshot.key ?: return
                    val (from,to) = key.split("_")

                    if (to != currentUserId) return

                    val data = snapshot.value as? Map<*,*> ?: return

                    val candidate = IceCandidate(
                        data["sdpMidp"] as String,
                        (data["sdpMLineIndex"] as Long).toInt(),
                        data["candidate"] as String
                    )

                    peerConnections[from]?.addIceCandidate(candidate)
                }

                override fun onChildChanged(p0: DataSnapshot, p1: String?) {}
                override fun onChildMoved(p0: DataSnapshot, p1: String?) {}
                override fun onChildRemoved(p0: DataSnapshot) {}
                override fun onCancelled(p0: DatabaseError) {}
            })
    }

    private fun sendIceCandidate(target: String, candidate: IceCandidate) {
        val data = mapOf(
            "candidate" to candidate.sdp,
            "sdpMid" to candidate.sdpMid,
            "sdpMLineIndex" to candidate.sdpMLineIndex
        )

        db.child("groupCalls/$roomId/ice/${currentUserId}_$target")
            .push()
            .setValue(data)
    }

    fun leaveRoom() {
        peerConnections.values.forEach {
            it.close()
            it.dispose()
        }
        peerConnections.clear()

        db.child("groupCalls/$roomId/participants/$currentUserId").removeValue()
    }
//    private val applicationContext = context
//    private var peerConnectionFactory: PeerConnectionFactory? = null
//    private var peerConnections =  mutableMapOf<String, PeerConnection>()
//    private var audioSource: AudioSource? = null
//    private var videoSource: VideoSource? = null
//    private var audioTrack: AudioTrack? = null
//    private var _videoTrack = MutableStateFlow<VideoTrack?>(null)
//    var videoTrack = _videoTrack.asStateFlow()
//    private var _remoteVideoTrack = MutableStateFlow<VideoTrack?>(null)
//    var remoteVideoTrack = _remoteVideoTrack.asStateFlow()
//    private var localVideoTrack: VideoTrack? = null
//    private var videoCapturer: VideoCapturer? = null
//    private var surfaceTexture: SurfaceTextureHelper? = null
//    lateinit var eglBase: EglBase
//    private var _eglBaseContext = MutableStateFlow<EglBase.Context?>(null)
//    var eglBaseContext = _eglBaseContext.asStateFlow()
//    private var localMediaStream: MediaStream? = null
//    private var signalingValueEventList: ValueEventListener? = null
//    private var callEndedEventListener: ValueEventListener? = null
//
//    private var isDisposed = false
//    private var isOfferHandled = false
//    private var isAnswerHandled = false
//    private var addedIceCandidates = mutableSetOf<String>()
//    private val pendingIceCandidate = mutableListOf<IceCandidate>()
//    private var callerName: String? = null
//    private var pendingIncomingCall = false
//    private var pendingOffer: String? = null
//    private var _onIncomingCall: (() -> Unit)? = null
//    var onIncomingCall: (() -> Unit)?
//        get() = _onIncomingCall
//        set(value) {
//            _onIncomingCall = value
//            if (pendingIncomingCall && pendingOffer != null) {
//                pendingIncomingCall = false
//                val offer = pendingOffer!!
//                pendingOffer = null
//                value?.invoke()
//                handleIncomingCall(offer)
//            }
//        }
//    private var callStarted = false
//    private var isHandleIncomingCall = false
//    private var isFrontCamera = true
//
//    var onRemoteStreamAdded: (MediaStream) -> Unit = {}
//    var onRemoteStreamRemoved: (MediaStream) -> Unit = {}
//    var onConnectionStateChanged: (PeerConnection.IceConnectionState) -> Unit = {}
//    var onCallEnded: (() -> Unit)? = null
//
//
//    init {
//        initializePeerConnection()
//        connectToSignalingServer()
//    }
//
//    private fun initializePeerConnection() {
//        try {
//            val options = PeerConnectionFactory.InitializationOptions.builder(applicationContext)
//                .setEnableInternalTracer(true)
//                .createInitializationOptions()
//
//            PeerConnectionFactory.initialize(options)
//
//            eglBase = EglBase.create()
//            _eglBaseContext.value = eglBase.eglBaseContext
//
//            peerConnectionFactory = PeerConnectionFactory.builder()
//                .setOptions(PeerConnectionFactory.Options())
//                .setVideoEncoderFactory(DefaultVideoEncoderFactory(_eglBaseContext.value, true, true))
//                .setVideoDecoderFactory(DefaultVideoDecoderFactory(_eglBaseContext.value))
//                .createPeerConnectionFactory()
//            Log.d("GroupRTC","PeerConnectionFactory initialized")
//        } catch (e: Exception) {
//            Log.e("GroupRTC","Error establishing connection: ${e.message}")
//        }
//    }
//
//    private fun connectToSignalingServer() {
//        if (isDisposed) return
//        try {
//            val listener = object: ValueEventListener {
//                override fun onDataChange(snapshot: DataSnapshot) {
//                    if (isDisposed) return
//                    val groupCallData = snapshot.value as? Map<*,*> ?: return
//                    Log.d("GroupRTC","Answer from: ${groupCallData["answerFrom"]}")
//
//                    val status = groupCallData["status"] as? Boolean
//                    if (status == true) {
//                        onCallEnded?.invoke()
//                        return
//                    }
//                    val offerFrom = groupCallData["offerFrom"] as? String
//                    val answerFrom = groupCallData["answerFrom"] as? String
//
//                    val offer = groupCallData["offer"] as? String
//                    if (offer != null && !isOfferHandled &&
//                        peerConnection == null && offerFrom == otherUserId) {
//                        isOfferHandled = true
//                        isHandleIncomingCall = true
//                        Log.d("GroupRTC","Offer received")
//                        if (onIncomingCall != null) {
//                            onIncomingCall?.invoke()
//                            handleIncomingCall(offer)
//                        } else {
//                            Log.d("GroupRTC","No callback yet")
//                            pendingIncomingCall = true
//                            pendingOffer = offer
//                        }
//                    }
//
//                    val answer = groupCallData["answer"] as? String
//                    if (answer != null && !isAnswerHandled &&
//                        peerConnection == null && offerFrom == otherUserId) {
//                        isAnswerHandled = true
//                        Log.d("GroupRTC","Answer from $answerFrom - handling answer")
//                        handleCallAnswered(answer)
//                    }
//
//                    val iceCandidates = groupCallData["iceCandidates_$otherUserId"] as? Map<*,*>
//                    Log.d("GroupRTC","ICE Count: ${iceCandidates?.size ?: 0}")
//                    iceCandidates?.forEach { (key,candidate) ->
//                        if (candidate is Map<*,*> && peerConnection != null) {
//                            val candidateKey = key.toString()
//                            if (!addedIceCandidates.contains(candidateKey)) {
//                                try {
//                                    val sdp = candidate["candidate"] as? String
//                                    val sdpMid = candidate["sdpMid"] as? String
//                                    val sdpMLineIndex = (candidate["sdpMLineIndex"] as? Long)?.toInt()
//                                    if (sdp != null && sdpMid != null && sdpMLineIndex != null) {
//                                        val iceCandidate = IceCandidate(sdpMid,sdpMLineIndex,sdp)
//                                        if (peerConnection?.remoteDescription != null) {
//                                            peerConnection?.addIceCandidate(iceCandidate)
//                                        } else {
//                                            pendingIceCandidate.add(iceCandidate)
//                                        }
//                                        addedIceCandidates.add(candidateKey)
//                                    }
//                                    Log.d("GroupRTC","ICE candidate added")
//                                } catch (e: Exception) {
//                                    Log.e("GroupRTC","Error handling ICE candidates: ${e.message}")
//                                }
//                            }
//                        }
//                    }
//                }
//
//                override fun onCancelled(error: DatabaseError) {
//                    Log.e("GroupRTC","Firebase error: ${error.message}")
//                }
//            }
//            signalingValueEventList = listener
//        } catch (e: Exception) {
//            Log.e("GroupRTC","Error connecting to server: ${e.message}")
//        }
//    }
//
//    fun joinRoom(roomId: String) {
//        addSelfToParticipants()
//
//        val existingUsers = getParticipants()
//
//        existingUsers.forEach { userId ->
//            if (userId != currentUserId) {
//                createConnectionForUser(currentUserId)
//                createAndSendOffer(currentUserId)
//            }
//        }
//    }
//
//    fun createConnectionForUser(userId: String) {
//        val peerConnection = peerConnectionFactory?.createPeerConnection()
//    }
//
//    fun startCameraCapturer(): CameraVideoCapturer? {
//        val enumerator = Camera2Enumerator(applicationContext)
//        for (devices in enumerator.deviceNames) {
//            if (enumerator.isFrontFacing(devices)) {
//                return enumerator.createCapturer(devices, null)
//            }
//        }
//        return null
//    }
//
//    fun createLocalStream(): MediaStream? {
//        if (isDisposed) throw IllegalStateException("WebRTC is disposed")
//        try {
//            val audioConstraints = MediaConstraints().apply {
//                mandatory.add(MediaConstraints.KeyValuePair("echoCancellation","true"))
//                mandatory.add(MediaConstraints.KeyValuePair("noiseSuppression","true"))
//                mandatory.add(MediaConstraints.KeyValuePair("autoGainControl","true"))
//            }
//
//            audioSource = peerConnectionFactory?.createAudioSource(audioConstraints)
//            audioTrack = peerConnectionFactory?.createAudioTrack("audio",audioSource)
//            audioTrack?.setEnabled(true)
//
//            videoCapturer = startCameraCapturer()
//
//            if (videoSource == null) {
//                peerConnectionFactory?.createVideoSource(false)
//            }
//
//            surfaceTexture = SurfaceTextureHelper.create(
//                "VideoCall",
//                eglBase.eglBaseContext
//            )
//
//            videoCapturer?.initialize(surfaceTexture, applicationContext, videoSource?.capturerObserver)
//            videoCapturer?.startCapture(720,1280,30)
//
//            localVideoTrack = peerConnectionFactory?.createVideoTrack("videoTrack",videoSource)
//            localVideoTrack?.setEnabled(true)
//
//            _videoTrack.value = localVideoTrack!!
//
//            localMediaStream = peerConnectionFactory?.createLocalMediaStream("localStream")
//            localMediaStream?.addTrack(audioTrack)
//            localMediaStream?.addTrack(localVideoTrack)
//        } catch (e: Exception) {
//            Log.e("GroupRTC","Error creating local stream: ${e.message}")
//        }
//        return localMediaStream
//    }
//
//    fun createPeerConnection() {
//        if (isDisposed) return
//        val iceServers = listOf(
//            PeerConnection.IceServer.builder("stun:stun.l.google.com:19302").createIceServer(),
//            PeerConnection.IceServer.builder("stun:stun1.l.google.com:19302").createIceServer()
//        )
//
//        val peerConnection = peerConnectionFactory?.createPeerConnection(
//            iceServers,
//            object : PeerConnection.Observer {
//                override fun onSignalingChange(signalingState: PeerConnection.SignalingState) {
//                    Log.d("GroupRTC","Signaling stat: $signalingState")
//                }
//
//                override fun onIceConnectionChange(iceConnectionState: PeerConnection.IceConnectionState) {
//                    Log.d("GroupRTC","ICE Connection State: $iceConnectionState")
//                    onConnectionStateChanged(iceConnectionState)
//                }
//
//                override fun onAddStream(mediaStream: MediaStream) {
//                    Log.d("GroupRTC","Remote stream added")
//                    if (mediaStream.videoTracks.isNotEmpty()) {
//                        _remoteVideoTrack.value = mediaStream.videoTracks[0]
//                    }
//                    onRemoteStreamAdded(mediaStream)
//                }
//
//                override fun onRemoveStream(mediaStream: MediaStream) {
//                    Log.d("GroupRTC","Remote stream removed")
//                    onRemoteStreamRemoved(mediaStream)
//                }
//
//                override fun onDataChannel(dataChannel: DataChannel) {
//                    Log.d("GroupRTC","Data received channel: ${dataChannel.label()}")
//                }
//
//                override fun onRenegotiationNeeded() {
//                    Log.d("GroupRTC","Renegotiation needed")
//                }
//
//                override fun onAddTrack(
//                    receiver: RtpReceiver,
//                    mediaStreams: Array<out MediaStream>
//                ) {
//                    Log.d("GroupRTC","Track added: ${receiver.track()?.kind()}")
//                    if (receiver.track()?.kind() == "video") {
//                        _remoteVideoTrack.value = receiver.track() as? VideoTrack
//                    }
//                }
//
//                override fun onIceConnectionReceivingChange(p0: Boolean) {
//                    Log.d("GroupRTC","ICE Connection receiving: $p0")
//                }
//
//                override fun onIceGatheringChange(p0: PeerConnection.IceGatheringState?) {
//                }
//
//                override fun onIceCandidate(p0: IceCandidate?) {
//                }
//
//                override fun onIceCandidatesRemoved(candidate: Array<out IceCandidate>) {
//                    Log.d("WebRTC","ICE Candidate removed: ${candidate.size}")
//                }
//            }
//        ) ?: run {
//            Log.d("GroupRTC","Peer connection not created")
//            return
//        }
//    }
//
//    fun initiateCall() {
//        if (isDisposed) return
//        try {
//            if (peerConnection != null) {
//                Log.w("GroupRTC","Connection already exists")
//                return
//            }
//            createPeerConnection()
//
//            audioTrack?.let { peerConnection?.addTrack(it) }
//            localVideoTrack?.let { peerConnection?.addTrack(it) }
//
//            peerConnection?.createOffer(
//                object : SdpObserver {
//                    override fun onCreateSuccess(sessionDescription: SessionDescription) {
//                        peerConnection?.setLocalDescription(this, sessionDescription)
//                    }
//
//                    override fun onSetSuccess() {
//                        val offer = peerConnection?.localDescription?.description ?: return
//                        sendOffer(offer)
//                    }
//
//                    override fun onCreateFailure(p0: String) {
//                        Log.e("GroupRTC","Create offer failed $p0")
//                    }
//
//                    override fun onSetFailure(p0: String) {
//                        Log.e("GroupRTC","Set local description failed: $p0")
//                    }
//                },
//                MediaConstraints()
//            )
//            Log.d("GroupRTC","Call initiated")
//        } catch (e: Exception) {
//            Log.e("GroupRTC","Error initaiting call: ${e.message}")
//        }
//    }
//
//    fun handleIncomingCall(offer: String) {
//        if (isDisposed) return
//        try {
//            Log.d("GroupRTC","Handle incoming call")
//            if (peerConnection != null) {
//                Log.w("GroupRTC","Peer connection already exists")
//                return
//            }
//
//            createPeerConnection()
//            Log.d("GroupRTC","Created connection for receiver")
//
//            audioTrack?.let { peerConnection?.addTrack(it) }
//            localVideoTrack?.let { peerConnection?.addTrack(it) }
//
//            val sessionDescription = SessionDescription(
//                SessionDescription.Type.OFFER,
//                offer
//            )
//            Log.d("GroupRTC","Remote offer received")
//
//            peerConnection?.setRemoteDescription(
//                object : SdpObserver {
//                    override fun onSetSuccess() {
//                        drainIceCandidates()
//                    }
//
//                    override fun onCreateFailure(s: String) {
//                        Log.e("GroupRTC","Set remote description failed $s")
//                    }
//
//                    override fun onSetFailure(s: String) {
//                        Log.e("GroupRTC","Set remote description failed $s")
//                    }
//
//                    override fun onCreateSuccess(p0: SessionDescription) {}
//                },
//                sessionDescription
//            )
//            Log.d("GroupRTC","Incoming call handled")
//        } catch (e: Exception) {
//            Log.e("GroupRTC","Error handling incoming call: ${e.message}")
//        }
//    }
//
//    fun acceptCall() {
//        if (isDisposed) return
//        Log.d("GroupRTC","Call accepted creating answer")
//        createAnswer()
//    }
//
//    private fun listenForCallEnd() {
//        if (isDisposed) return
//        val listener = object : ValueEventListener {
//            override fun onDataChange(snapshot: DataSnapshot) {
//                if (isDisposed) return
//                if ((callStarted || isHandleIncomingCall) && !snapshot.exists()) {
//                    Log.d("GroupRTC","Call ended by other user")
//                    onCallEnded?.invoke()
//                }
//            }
//            override fun onCancelled(error: DatabaseError) {}
//        }
//        callEndedEventListener = listener
//
//    }
//
//    private fun createAnswer() {
//        if (isDisposed) return
//        try {
//            Log.d("GroupRTC","Creating answer..")
//            peerConnection?.createAnswer(
//                object : SdpObserver {
//                    override fun onCreateSuccess(sessionDescription: SessionDescription) {
//                        peerConnection?.setLocalDescription(this,sessionDescription)
//                    }
//
//                    override fun onSetSuccess() {
//                        val answer = peerConnection?.localDescription?.description ?: return
//                        Log.d("GroupRTC","Answer created")
//                        sendAnswer(answer)
//                    }
//
//                    override fun onCreateFailure(p0: String) {
//                        Log.e("GroupRTC","Create answer failed $p0")
//                    }
//
//                    override fun onSetFailure(p0: String) {
//                        Log.d("GroupRTC","Set local description failed $p0")
//                    }
//                },
//                MediaConstraints()
//            )
//        } catch (e: Exception) {
//            Log.e("GroupRTC","Error creating answer: ${e.message}")
//        }
//    }
//
//    private fun handleCallAnswered(answer: String) {
//        if (isDisposed) return
//        try {
//            val sessionDescription = SessionDescription(
//                SessionDescription.Type.ANSWER,
//                answer
//            )
//
//            peerConnection?.setRemoteDescription(
//                object : SdpObserver {
//                    override fun onSetSuccess() {
//                        Log.d("GroupRTC","Remote description set successfully")
//                        drainIceCandidates()
//                    }
//
//                    override fun onCreateSuccess(p0: SessionDescription?) {}
//                    override fun onCreateFailure(p0: String?) {}
//                    override fun onSetFailure(p0: String) {
//                        Log.e("GroupRTC","Set remote description failed: $p0")
//                    }
//                },
//                sessionDescription
//            )
//        } catch (e: Exception) {
//            Log.e("GroupRTC","Error handling message: ${e.message}")
//        }
//    }
//
//    private fun drainIceCandidates() {
//        pendingIceCandidate.forEach {
//            peerConnection?.addIceCandidate(it)
//        }
//        pendingIceCandidate.clear()
//    }
//
//    private fun sendOffer(offer: String) {
//        if (isDisposed) return
//        callStarted = true
//
//    }
//
//    private fun sendAnswer(answer: String) {
//        if (isDisposed) return
//        callStarted = true
//    }
//
//    private fun sendIceCandidate(candidate: IceCandidate) {
//        if (isDisposed) return
//        val candidateData = mapOf(
//            "candidate" to candidate.sdp,
//            "sdpMid" to candidate.sdpMid,
//            "sdpMLineIndex" to candidate.sdpMLineIndex
//        )
//
//
//    }
//
//    fun setMicrophoneEnabled(enabled: Boolean) {
//        if (isDisposed) return
//        audioTrack?.setEnabled(enabled)
//        Log.d("GroupRTC","Microphone ${if (enabled) "enabled" else "disabled"}")
//    }
//
//    fun endCall(rejected: Boolean = true) {
//        if (isDisposed) return
//        try {
//            if (rejected) {
//
//            }
//            else {
//
//            }
//            disconnect()
//        } catch (e: Exception) {
//            Log.e("GroupRTC","Error handling call: ${e.message}")
//        }
//    }
//
//    fun disconnect() {
//        if (isDisposed) return
//        try {
//            signalingValueEventList?.let { }
//            callEndedEventListener?.let { }
//
//            isAnswerHandled = false
//            isOfferHandled = false
//            isHandleIncomingCall = false
//            callStarted = false
//            addedIceCandidates.clear()
//
//            peerConnection?.close()
//            peerConnection?.dispose()
//            peerConnection = null
//
//            audioTrack?.let { track ->
//                localMediaStream?.removeTrack(track)
//                track.setEnabled(false)
//                track.dispose()
//            }
//            localVideoTrack = null
//
//            videoSource?.dispose()
//            videoSource = null
//
//            localMediaStream?.dispose()
//            localMediaStream = null
//
//            if (::eglBase.isInitialized) {
//                eglBase.release()
//            }
//            Log.d("GroupRTC","Disconnecting..")
//        } catch (e: Exception) {
//            Log.e("GroupRTC", "Error disconnecting ${e.message}")
//        }
//    }
}