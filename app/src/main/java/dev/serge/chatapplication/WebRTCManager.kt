package dev.serge.chatapplication

import android.content.Context
import android.util.Log
import androidx.compose.runtime.mutableStateListOf
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ValueEventListener
import org.webrtc.DataChannel
import org.webrtc.IceCandidate
import org.webrtc.MediaConstraints
import org.webrtc.MediaStream
import org.webrtc.PeerConnection
import org.webrtc.PeerConnectionFactory
import org.webrtc.RtpReceiver
import org.webrtc.SdpObserver
import org.webrtc.SessionDescription

class WebRTCManager(
    private val context: Context,
    private val currentUserId: String,
    private val otherUserId: String
) {
    private val applicationContext = context.applicationContext
    lateinit var onSignalingStateChanged: Any
    private lateinit var peerConnectionFactory: PeerConnectionFactory
    private var peerConnection: PeerConnection? = null
    private var audioTrack: org.webrtc.AudioTrack? = null
    private var localMediaStream: MediaStream? = null
    private val db = FirebaseDatabase.getInstance().reference

    private var isOfferHandled = false
    private var isAnswerHandled = false
    private var addedIceCandidates = mutableStateListOf<String>()
    private var callerName: String = ""
    private var pendingIncomingCall = false
    private var pendingOffer: String? = null
    private var _onIncomingCall: (() -> Unit)? = null
    var onIncomingCall: (() -> Unit)?
        get() = _onIncomingCall
        set(value)  {
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


    var onRemoteStreamAdded: (MediaStream) -> Unit = {}
    var onRemoteStreamRemoved: (MediaStream) -> Unit = {}
    var onConnectionStateChanged: (PeerConnection.IceConnectionState) -> Unit = {}
    var onCallEnded: (() -> Unit)? = null

    private fun generateCallId(user1: String, user2: String): String {
        return listOf(user1,user2).sorted().joinToString("_")
    }
    private val callId = generateCallId(currentUserId,otherUserId)

    init {
        initializePeerConnectionFactory()
        connectToSignalingServer()
        listenForCallEnd()
        Log.d("INSAAF","Current: $currentUserId Other: $otherUserId")
    }

    private fun initializePeerConnectionFactory() {
        try {
            val options = PeerConnectionFactory.InitializationOptions.builder(applicationContext)
                .setEnableInternalTracer(true)
                .createInitializationOptions()

            PeerConnectionFactory.initialize(options)

            peerConnectionFactory = PeerConnectionFactory.builder()
                .setOptions(PeerConnectionFactory.Options())
                .createPeerConnectionFactory()
            Log.d("WebRTC","PeerConnectionFactory Initialized")
        } catch (e: Exception) {
            Log.e("WebRTC","Error establishing connection ${e.message}")
        }
    }

    private fun connectToSignalingServer() {
        try {
            db.child("webrtc_calls").child(callId).addValueEventListener(
                object : ValueEventListener {
                    override fun onDataChange(snapshot: DataSnapshot) {
                        val callData = snapshot.value as? Map<*, *> ?: return

                        Log.d("WebRTC", "currentUserId: $currentUserId")
                        Log.d("WebRTC", "offerFrom: ${callData["offerFrom"]}")
                        Log.d("WebRTC", "answerFrom: ${callData["answerFrom"]}")

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
                                Log.d("WebRTC","No callback yet")
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
                        Log.d("WebRTC","ICE Count: ${iceCandidates?.size ?: 0}")
                        iceCandidates?.forEach { (key, candidate) ->
                            if (candidate is Map<*, *> && peerConnection != null) {
                                val candidateKey = key.toString()
                                if (!addedIceCandidates.contains(candidateKey)) {
                                    try {
                                        val sdp = candidate["candidate"] as? String
                                        val sdpMid = candidate["sdpMid"] as? String
                                        val sdpMLineIndex = (candidate["sdpMLineIndex"] as? Long)?.toInt()
                                        if (sdp != null && sdpMid != null && sdpMLineIndex != null) {
                                            val iceCandidate = IceCandidate(sdpMid,sdpMLineIndex,sdp)
                                            peerConnection?.addIceCandidate(iceCandidate)
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
            )
        } catch (e: Exception) {
            Log.e("WebRTC", "Error: ${e.message}")
        }
    }

    fun createLocalStream(): MediaStream {
        try {
            val audioConstraints = MediaConstraints().apply {
                mandatory.add(MediaConstraints.KeyValuePair("echoCancellation","true"))
                mandatory.add(MediaConstraints.KeyValuePair("noiseSuppression","true"))
                mandatory.add(MediaConstraints.KeyValuePair("autoGainControl","true"))
            }

            val audioSource = peerConnectionFactory.createAudioSource(audioConstraints)
            audioTrack = peerConnectionFactory.createAudioTrack("audio",audioSource)
            audioTrack?.setEnabled(true)

            localMediaStream = peerConnectionFactory.createLocalMediaStream("localStream")
            localMediaStream?.addTrack(audioTrack)
            Log.d("WebRTC","Local stream created")
            return localMediaStream!!
        } catch (e: Exception) {
            Log.e("WebRTC","Error creating local stream ${e.message}")
            throw e
        }
    }

    fun createPeerConnection() {
        val iceServers = listOf(
            PeerConnection.IceServer.builder("stun:stun.l.google.com:19302").createIceServer(),
            PeerConnection.IceServer.builder("stun:stun1.l.google.com:19302").createIceServer()
        )
        peerConnection = peerConnectionFactory.createPeerConnection(
            iceServers,
            object : PeerConnection.Observer {
                override fun onSignalingChange(signalingState: PeerConnection.SignalingState) {
                    Log.d("WebRTC","Signaling state: $signalingState")
//                    onSignalStateChanged(signalingState)
                }

                override fun onIceConnectionChange(iceConnectionState: PeerConnection.IceConnectionState) {
                    Log.d("WebRTC","ICE Connection State: $iceConnectionState")
                    onConnectionStateChanged(iceConnectionState)
                }

                override fun onIceGatheringChange(iceGatheringState: PeerConnection.IceGatheringState) {
                    Log.d("WebRTC","New ICE Candidate $iceGatheringState")
                }

                override fun onIceCandidate(iceCandidate: IceCandidate?) {
                    Log.d("WebRTC","New ICE Candidate")
                    iceCandidate?.let {
                        sendIceCandidate(iceCandidate)
                    }
                }

                override fun onAddStream(mediaStream: MediaStream) {
                    Log.d("WebRTC","Remote stream added")
                    onRemoteStreamAdded(mediaStream)
                }

                override fun onRemoveStream(mediaStream: MediaStream) {
                    Log.d("WebRTC","Media stream removed")
                    onRemoteStreamRemoved(mediaStream)
                }

                override fun onDataChannel(dataChannel: DataChannel) {
                    Log.d("WebRTC","Data received channel: ${dataChannel.label()}")
                }

                override fun onRenegotiationNeeded() {
                    Log.d("WebRTC","Renegotiation needed")
                }

                override fun onAddTrack(
                    rtpReceiver: RtpReceiver,
                    mediaStreams: Array<MediaStream>
                ) {
                    Log.d("WebRTC","Track added: ${rtpReceiver.track()?.kind()}")
                }

                override fun onIceConnectionReceivingChange(receiving: Boolean) {
                    Log.d("WebRTC","ICE Connection receiving: $receiving")
                }

                override fun onIceCandidatesRemoved(candidates: Array<IceCandidate>) {
                    Log.d("WebRTC","ICE Candidate removed: ${candidates.size}")
                }
            }
        ) ?: run {
            Log.d("WebRTC", "peer connection not created")
            return
        }
    }

    fun initiateCall() {
        try {
            if (peerConnection != null) {
                Log.w("WebRTC","Peer connection already exists")
                return
            }
            createPeerConnection()

            audioTrack?.let { track ->
                val result = peerConnection?.addTrack(track)
            }

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
                        Log.e("WebRTC","Create offer failed $s")
                    }

                    override fun onSetFailure(s: String) {
                        Log.e("WebRTC","Set local description failed: $s")
                    }
                },
                MediaConstraints()
            )
            Log.d("WebRTC","Call initiated")
        } catch (e: Exception) {
            Log.e("WebRTC","Error initiating call: ${e.message}")
        }
    }

    private fun handleIncomingCall(offer: String) {
        try {
            Log.d("WebRTC","Handle incoming call")
            if (peerConnection != null) {
                Log.w("WebRTC","Peer connection already exists")
                return
            }

            createPeerConnection()
            Log.d("WebRTC","Connection created for receiver")

//            localMediaStream?.audioTracks?.forEach { audioTrack ->
//                peerConnection?.addTrack(audioTrack)
//                Log.d("WebRTC","Audio track added")
//            }

            audioTrack?.let { track ->
                val result = peerConnection?.addTrack(track)
            }

            val sessionDescription = SessionDescription(
                SessionDescription.Type.OFFER,
                offer
            )

            Log.d("WebRTC", "remote offer received")

            peerConnection?.setRemoteDescription(
                object : SdpObserver {
                    override fun onSetSuccess() {
                    }

                    override fun onCreateFailure(s: String) {
                        Log.e("WebRTC","Set remote description failed $s")
                    }

                    override fun onSetFailure(s: String) {
                        Log.e("WebRTC","Set remote description failed $s")
                    }

                    override fun onCreateSuccess(p0: SessionDescription?) {}
                },
                sessionDescription
            )
            Log.d("WebRTC","Incoming call handled")
        } catch (e: Exception) {
            Log.e("WebRTC","Error handling incoming call: ${e.message}")
        }
    }

    fun acceptCall() {
        Log.d("WebRTC","Call accepted creating answer")
        createAnswer()
    }

    private fun listenForCallEnd() {
        db.child("webrtc_calls").child(callId)
            .addValueEventListener(object : ValueEventListener{
                override fun onDataChange(snapshot: DataSnapshot) {
                    if (callStarted && !snapshot.exists()) {
                        Log.d("WebRTC","Call ended by other user")
                        onCallEnded?.invoke()
                    }
                }
                override fun onCancelled(error: DatabaseError) {}
            })
    }

    private fun createAnswer() {
        try {
            Log.d("WebRTC","Creating answer..")
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
                        Log.e("WebRTC","Create answer failed: $s")
                    }

                    override fun onSetFailure(s: String) {
                        Log.d("WebRTC","Set local description failed: $s")
                    }
                },
                MediaConstraints()
            )
        } catch (e: Exception) {
            Log.e("WebRTC","Error creating answer${e.message}")
        }
    }

    private fun handleCallAnswered(answer: String) {
        try {
            val sessionDescription = SessionDescription(
                SessionDescription.Type.ANSWER,
                answer
            )
            peerConnection?.setRemoteDescription(
                object : SdpObserver {
                    override fun onSetSuccess() {
                        Log.d("WebRTC","Remote description set successfully")
                    }

                    override fun onCreateSuccess(p0: SessionDescription?) {}
                    override fun onCreateFailure(s: String) {}
                    override fun onSetFailure(s: String) {
                        Log.e("WebRTC","Set remote description failed: $s")
                    }
                },
                sessionDescription
            )
        } catch (e: Exception) {
            Log.e("WebRTC","Error handling answer: ${e.message}")
        }
    }

    private fun sendOffer(offer: String) {
        callStarted = true
        val updates = mapOf(
            "offer" to offer,
            "offerFrom" to currentUserId,
            "timestamp" to System.currentTimeMillis()
        )
        db.child("webrtc_calls").child(callId).updateChildren(updates)

        val notification = mapOf(
            "callerId" to currentUserId,
            "callerName" to callerName,
            "chatId" to callId,
            "timestamp" to System.currentTimeMillis()
        )
        db.child("incoming_calls")
            .child(otherUserId).setValue(notification)
            .addOnSuccessListener {
                Log.d("WebRTC","Incoming call from $otherUserId")
            }
            .addOnFailureListener {
                Log.e("WebRTC","Failed to send notification ${it.message}")
            }
        Log.d("WebRTC","Offer sent to $otherUserId")
    }

    private fun sendAnswer(answer: String) {
        callStarted = true
        val updates = mapOf(
            "answer" to answer,
            "answerFrom" to currentUserId,
            "timestamp" to System.currentTimeMillis()
        )
        db.child("webrtc_calls").child(callId).updateChildren(updates)
        Log.d("WebRTC","Answer sent to $otherUserId")
    }

    private fun sendIceCandidate(candidate: IceCandidate) {
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

    fun setCallerName(name: String) {
        callerName = name
    }

    fun setMicrophoneEnabled(enabled: Boolean) {
        audioTrack?.setEnabled(enabled)
        Log.d("WebRTC","Microphone ${if (enabled) "enabled" else "disabled"}")
    }

    fun endCall() {
        try {
            db.child("webrtc_calls").child(callId).removeValue()
            db.child("incoming_calls").child(otherUserId).removeValue()
            disconnect()
            Log.d("WebRTC","Call ended")
        } catch (e: Exception) {
            Log.e("WebRTC","Error handling call: ${e.message}")
        }
    }

    fun disconnect() {
        try {
            isAnswerHandled = false
            isOfferHandled = false
            addedIceCandidates.clear()

            peerConnection?.close()
            peerConnection = null

            audioTrack?.let { track ->
                localMediaStream?.removeTrack(track)
                track.setEnabled(false)
                track.dispose()
            }
            audioTrack = null

            localMediaStream?.dispose()
            localMediaStream = null

            Log.d("WebRTC","Disconnected")
        } catch (e: Exception) {
            Log.e("WebRTC","Error disconnecting: ${e.message}")
        }
    }
}