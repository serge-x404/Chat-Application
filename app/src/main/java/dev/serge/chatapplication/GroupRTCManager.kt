package dev.serge.chatapplication

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.util.Log
import com.google.firebase.database.ChildEventListener
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.getValue
import org.webrtc.AudioTrack
import org.webrtc.Camera1Enumerator
import org.webrtc.Camera2Enumerator
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
import org.webrtc.VideoTrack

class GroupRTCManager(
    private val context: Context,
    private val currentUserId: String,
    private val roomId: String
) {
    private val db = FirebaseDatabase.getInstance().reference

    private var factory: PeerConnectionFactory? = null
    private val peerConnections = mutableMapOf<String, PeerConnection>()

    var remoteAudioTrack = mutableMapOf<String, AudioTrack>()
    var remoteVideoTrack = mutableMapOf<String, VideoTrack>()

    private var localAudioTrack: AudioTrack? = null
    private var localVideoTrack: VideoTrack? = null
    private var videoCapturer: VideoCapturer? = null
    private var surfaceTextureHelper: SurfaceTextureHelper? = null

    val eglBase: EglBase = EglBase.create()

    private val iceServers = listOf(
        PeerConnection.IceServer.builder("stun:stun.l.google.com:19302").createIceServer(),
        PeerConnection.IceServer.builder("stun:stun1.l.google.com:19302").createIceServer()
    )

    var onParticipationJoined: ((String) -> Unit)? = null
    var onParticipationLeft: ((String) -> Unit)? = null
    var onCallEnded: (() -> Unit)? = null
    var onRemoteVideoTrack: ((String, VideoTrack) -> Unit)? = null
    var onRemoteAudioTrack: ((String, AudioTrack) -> Unit)? = null

    private var isDisposed = false

    init {
        initFactory()
        createLocalAudio()
        listenForSignals()
        listenForParticipants()
    }

    private fun initFactory() {
        val options = PeerConnectionFactory.InitializationOptions.builder(context)
            .createInitializationOptions()

        PeerConnectionFactory.initialize(options)

        factory = PeerConnectionFactory.builder()
            .setOptions(PeerConnectionFactory.Options())
            .setVideoEncoderFactory(DefaultVideoEncoderFactory(eglBase.eglBaseContext, true, true))
            .setVideoDecoderFactory(DefaultVideoDecoderFactory(eglBase.eglBaseContext))
            .createPeerConnectionFactory()
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

    fun startLocalVideo(): VideoTrack? {
        if (factory == null) return null
        try {
            val enumerator = Camera2Enumerator(context)
//                Camera1Enumerator(true)
            val deviceName = enumerator.deviceNames.firstOrNull {
                enumerator.isFrontFacing(it)
            } ?:  return null

            videoCapturer = enumerator.createCapturer(deviceName, null)

            surfaceTextureHelper = SurfaceTextureHelper.create(
                "GroupVideoCapture",
                eglBase.eglBaseContext
            )

            val videoSource = factory?.createVideoSource(false)
            videoCapturer?.initialize(
                surfaceTextureHelper,
                context,
                videoSource?.capturerObserver
            )
            videoCapturer?.startCapture(720,1280,30)

            localVideoTrack = factory?.createVideoTrack("video_$currentUserId",videoSource)
            localVideoTrack?.setEnabled(true)

            // Add track to existing connections
            peerConnections.values.forEach { pc ->
                try {
                    localVideoTrack?.let { pc.addTrack(it, listOf("group_stream_$currentUserId")) }
                } catch (_: Exception) {}
            }
            return localVideoTrack
        } catch (e: Exception) {return null}
    }

    fun stopLocalVideo() {
        videoCapturer?.stopCapture()
        videoCapturer?.dispose()
        videoCapturer = null
        localVideoTrack?.setEnabled(false)
        localVideoTrack?.dispose()
        localVideoTrack = null
        surfaceTextureHelper?.dispose()
        surfaceTextureHelper = null
    }

    fun joinRoom() {
        if (isDisposed) return
        val participantRef = db.child("groupCalls/$roomId/participants/$currentUserId")
        participantRef.setValue(true)
        participantRef.onDisconnect().removeValue()

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
            .addOnFailureListener { Log.e("GroupRTC","Failed participants: ${it.message}") }
        Log.d("GroupRTC","Joined room $roomId as $currentUserId")
    }

    private fun createConnection(userId: String): PeerConnection? {
        if (peerConnections.containsKey(userId)) return peerConnections[userId]

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
                    val track = receiver.track()
                    Log.d("GroupRTC", "onAddTrack from $userId: ${track?.kind()}")
                    when (track) {
                        is AudioTrack -> {
                            track.setEnabled(true)
                            remoteAudioTrack[userId] = track
                            onRemoteAudioTrack?.invoke(userId, track)
                        }
                        is VideoTrack -> {
                            track.setEnabled(true)
                            remoteVideoTrack[userId] = track
                            onRemoteVideoTrack?.invoke(userId, track)
                        }
                    }
                }
                override fun onSignalingChange(state: PeerConnection.SignalingState) {}

                override fun onIceConnectionChange(state: PeerConnection.IceConnectionState) {
                    if (isDisposed) return
                    Log.d("GroupRTC", "Connection state for $userId: $state")
                    if (state == PeerConnection.IceConnectionState.FAILED) {
                        removeParticipant(userId)
                    }
                }

                override fun onIceConnectionReceivingChange(p0: Boolean) {}

                override fun onIceGatheringChange(state: PeerConnection.IceGatheringState) {}

                override fun onIceCandidatesRemoved(p0: Array<out IceCandidate?>?) {}

                override fun onAddStream(p0: MediaStream?) {}

                override fun onRemoveStream(p0: MediaStream?) {}

                override fun onDataChannel(p0: DataChannel?) {}

                override fun onRenegotiationNeeded() {
                    createOffer(userId)
                }
            }
        ) ?: return null

        localAudioTrack?.let { 
            try { pc.addTrack(it, listOf("group_stream_$currentUserId")) } catch (_: Exception) {}
        }
        localVideoTrack?.let { 
            try { pc.addTrack(it, listOf("group_stream_$currentUserId")) } catch (_: Exception) {}
        }

        peerConnections[userId] = pc
        return pc
    }

    private fun createOffer(targetUser: String) {
        val pc = peerConnections[targetUser] ?: return

        val constraints = MediaConstraints().apply {
            mandatory.add(MediaConstraints.KeyValuePair("OfferToReceiveVideo", "true"))
            mandatory.add(MediaConstraints.KeyValuePair("OfferToReceiveAudio", "true"))
        }

        pc.createOffer(object : SdpObserver{
            override fun onCreateSuccess(sdp: SessionDescription) {
                pc.setLocalDescription(this, sdp)
            }

            override fun onSetSuccess() {
                val offer = pc.localDescription?.description ?: return
                db.child("groupCalls/$roomId/offers/${currentUserId}_$targetUser")
                    .setValue(offer)
                Log.d("GroupRTC", "Offer sent to $targetUser")
            }

            override fun onCreateFailure(p0: String) {
                Log.e("GroupRTC", "Create Offer failed: $p0")
            }
            override fun onSetFailure(p0: String) {
                Log.e("GroupRTC", "Set Local Description failed: $p0")
            }
        }, constraints)
    }

    private fun createAnswer(fromUser: String) {
        val pc = peerConnections[fromUser] ?: return

        val constraints = MediaConstraints().apply {
            mandatory.add(MediaConstraints.KeyValuePair("OfferToReceiveVideo", "true"))
            mandatory.add(MediaConstraints.KeyValuePair("OfferToReceiveAudio", "true"))
        }

        pc.createAnswer(object : SdpObserver {
            override fun onCreateSuccess(sdp: SessionDescription) {
                pc.setLocalDescription(this, sdp)
            }

            override fun onSetSuccess() {
                val answer = pc.localDescription?.description ?: return
                db.child("groupCalls/$roomId/answers/${currentUserId}_$fromUser")
                    .setValue(answer)
                Log.d("GroupRTC", "Answer sent to $fromUser")
            }

            override fun onCreateFailure(p0: String) {
                Log.e("GroupRTC", "Create Answer failed: $p0")
            }
            override fun onSetFailure(p0: String) {
                Log.e("GroupRTC", "Set Local Description failed: $p0")
            }
        }, constraints)
    }

    private fun handleRemoteOffer(from: String, offer: String) {
        createConnection(from)
        val pc = peerConnections[from] ?: return

        pc.setRemoteDescription(object : SdpObserver {
            override fun onCreateSuccess(p0: SessionDescription?) {}
            override fun onSetSuccess() {
                createAnswer(from)
            }
            override fun onCreateFailure(p0: String?) {}
            override fun onSetFailure(p0: String?) {
                Log.e("GroupRTC", "Set Remote Offer failed: $p0")
            }
        }, SessionDescription(SessionDescription.Type.OFFER, offer))
    }

    private fun handleRemoteAnswer(from: String, answer: String) {
        val pc = peerConnections[from] ?: return
        pc.setRemoteDescription(object : SdpObserver {
            override fun onCreateSuccess(sdp: SessionDescription) {}
            override fun onCreateFailure(p0: String) {}
            override fun onSetSuccess() {
                Log.d("GroupRTC", "Remote Answer set for $from")
            }
            override fun onSetFailure(p0: String) {
                Log.e("GroupRTC", "Set Remote Answer failed: $p0")
            }
        }, SessionDescription(SessionDescription.Type.ANSWER, answer))
    }

    private fun listenForSignals() {

        db.child("groupCalls/$roomId/offers")
            .addChildEventListener(object : ChildEventListener {
                override fun onChildAdded(snapshot: DataSnapshot, prev: String?) {
                    processOffer(snapshot)
                }

                override fun onChildChanged(snapshot: DataSnapshot, prev: String?) {
                    processOffer(snapshot)
                }

                private fun processOffer(snapshot: DataSnapshot) {
                    if (isDisposed) return
                    val key = snapshot.key ?: return
                    val parts = key.split("_")
                    if (parts.size < 2) return
                    val from = parts[0]
                    val to = parts[1]

                    if (to != currentUserId) return
                    val offer = snapshot.getValue(String::class.java) ?: return
                    handleRemoteOffer(from, offer)
                }

                override fun onChildRemoved(snapshot: DataSnapshot) {
                    val userId = snapshot.key ?: return
                    removeParticipant(userId)
                }
                override fun onChildMoved(p0: DataSnapshot, p1: String?) {}
                override fun onCancelled(p0: DatabaseError) {}
            })

        db.child("groupCalls/$roomId/answers")
            .addChildEventListener(object : ChildEventListener{
                override fun onChildAdded(snapshot: DataSnapshot, prev: String?) {
                    processAnswer(snapshot)
                }

                override fun onChildChanged(snapshot: DataSnapshot, prev: String?) {
                    processAnswer(snapshot)
                }

                private fun processAnswer(snapshot: DataSnapshot) {
                    val key = snapshot.key ?: return
                    val parts = key.split("_")
                    if (parts.size < 2) return
                    val from = parts[0]
                    val to = parts[1]

                    if (to != currentUserId) return
                    val answer = snapshot.getValue(String::class.java) ?: return
                    handleRemoteAnswer(from, answer)
                }

                override fun onChildMoved(p0: DataSnapshot, p1: String?) {}
                override fun onChildRemoved(p0: DataSnapshot) {}
                override fun onCancelled(p0: DatabaseError) {}
            })

        db.child("groupCalls/$roomId/ice")
            .addChildEventListener(object : ChildEventListener {
                override fun onChildAdded(snapshot: DataSnapshot, prev: String?) {
                    val key = snapshot.key ?: return
                    val parts = key.split("_")
                    if (parts.size < 2) return
                    val from = parts[0]
                    val to = parts[1]

                    if (to != currentUserId) return

                    // Listen for candidates under this specific connection node
                    snapshot.ref.addChildEventListener(object : ChildEventListener {
                        override fun onChildAdded(candSnap: DataSnapshot, p1: String?) {
                            val data = candSnap.value as? Map<*,*> ?: return
                            val sdpMid = data["sdpMid"] as? String ?: return
                            val sdpMLineIndex = (data["sdpMLineIndex"] as? Number)?.toInt() ?: return
                            val candidateStr = data["candidate"] as? String ?: return

                            val candidate = IceCandidate(sdpMid, sdpMLineIndex, candidateStr)
                            peerConnections[from]?.addIceCandidate(candidate)
                            Log.d("GroupRTC", "Added ICE candidate from $from")
                        }
                        override fun onChildChanged(p0: DataSnapshot, p1: String?) {}
                        override fun onChildMoved(p0: DataSnapshot, p1: String?) {}
                        override fun onChildRemoved(p0: DataSnapshot) {}
                        override fun onCancelled(p0: DatabaseError) {}
                    })
                }

                override fun onChildChanged(p0: DataSnapshot, p1: String?) {}
                override fun onChildMoved(p0: DataSnapshot, p1: String?) {}
                override fun onChildRemoved(p0: DataSnapshot) {}
                override fun onCancelled(p0: DatabaseError) {}
            })
    }

    private fun listenForParticipants() {

        db.child("groupCalls/$roomId/participants")
            .addChildEventListener(object : ChildEventListener {
                override fun onChildAdded(snapshot: DataSnapshot, prev: String?) {
                    if (isDisposed) return
                    val userId = snapshot.key ?: return
                    if (userId == currentUserId) return
                    Log.d("GroupRTC","$userId joined")
                    onParticipationJoined?.invoke(userId)
                }

                override fun onChildRemoved(snapshot: DataSnapshot) {
                    val userId = snapshot.key ?: return
                    Log.d("GroupRTC","$userId left")
                    removeParticipant(userId)
                    
                    // If anyone leaves, end the call for everyone
                    onCallEnded?.invoke()
                }

                override fun onChildChanged(p0: DataSnapshot, p1: String?) {}
                override fun onChildMoved(p0: DataSnapshot, p1: String?) {}
                override fun onCancelled(p0: DatabaseError) {}
            })
    }

    private fun removeParticipant(userId: String) {
        Handler(Looper.getMainLooper()).post {
            val pc = peerConnections.remove(userId)
            if (pc != null) {
                try {
                    pc.close()
                    pc.dispose()
                } catch (e: Exception) {
                    Log.e("GroupRTC", "Error closing peer connection for $userId: ${e.message}")
                }
                onParticipationLeft?.invoke(userId)
            }
            remoteAudioTrack.remove(userId)
            remoteVideoTrack.remove(userId)
            Log.d("GroupRTC", "Removed participant: $userId")
        }
    }

    private fun sendIceCandidate(target: String, candidate: IceCandidate) {
        if (isDisposed) return
        try {
            val data = mapOf(
                "candidate" to candidate.sdp,
                "sdpMid" to candidate.sdpMid,
                "sdpMLineIndex" to candidate.sdpMLineIndex
            )

            db.child("groupCalls/$roomId/ice/${currentUserId}_$target")
                .push()
                .setValue(data)
        } catch (_: Exception) {}
    }

    fun setMicrophoneEnabled(enabled: Boolean) {
        localAudioTrack?.setEnabled(enabled)
    }

    fun setCameraEnabled(enabled: Boolean) {
        localVideoTrack?.setEnabled(enabled)
    }

    fun leaveRoom() {
        if (isDisposed) return
        isDisposed = true
        try {
            db.child("groupCalls/$roomId/participants/$currentUserId").removeValue()

            peerConnections.values.forEach {
                try {
                    it.close()
                    it.dispose()
                } catch (e: Exception) {
                    Log.e("GroupRTC","Error while closing connection: ${e.message}")
                }
            }

            peerConnections.clear()

            try {
                videoCapturer?.stopCapture()
            } catch (_: Exception) {}
            videoCapturer?.dispose()
            videoCapturer = null

            surfaceTextureHelper?.dispose()
            surfaceTextureHelper = null

            localVideoTrack?.setEnabled(false)
            localVideoTrack?.dispose()
            localVideoTrack = null

            localAudioTrack?.setEnabled(false)
            localAudioTrack?.dispose()
            localAudioTrack = null

            factory?.dispose()
            factory = null

            eglBase.release()
        } catch (e: Exception) {
            Log.e("GroupRTC","Error leaving ${e.message}")
        }

        db.child("groupCalls/$roomId/participants/$currentUserId").removeValue()
    }
}
