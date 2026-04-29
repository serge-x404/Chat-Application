package dev.serge.chatapplication

import android.content.Context
import android.util.Log
import com.google.firebase.database.ChildEventListener
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.FirebaseDatabase
import org.webrtc.AudioTrack
import org.webrtc.Camera1Enumerator
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
            val enumerator = Camera1Enumerator(true)
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

            peerConnections.values.forEach { peerConnection ->
                localAudioTrack?.let { peerConnection.addTrack(it) }
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
                    when (val track = receiver.track()) {
                        is AudioTrack -> {
                            remoteAudioTrack[userId] = track
                            onRemoteAudioTrack?.invoke(userId, track)
                        }
                        is VideoTrack -> {
                            remoteVideoTrack[userId] = track
                            onRemoteVideoTrack?.invoke(userId, track)
                        }
                    }
                }
                override fun onSignalingChange(state: PeerConnection.SignalingState) {}

                override fun onIceConnectionChange(state: PeerConnection.IceConnectionState) {
                    if (state == PeerConnection.IceConnectionState.DISCONNECTED ||
                        state == PeerConnection.IceConnectionState.CLOSED ||
                        state == PeerConnection.IceConnectionState.FAILED
                        ) {
                        removeParticipant(userId)
                    }
                }

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
        localVideoTrack?.let { pc?.addTrack(it) }

        peerConnections[userId] = pc!!
        return pc
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
                    onParticipationLeft?.invoke(userId)
                }

                override fun onChildChanged(p0: DataSnapshot, p1: String?) {}
                override fun onChildMoved(p0: DataSnapshot, p1: String?) {}
                override fun onCancelled(p0: DatabaseError) {}
            })
    }

    private fun removeParticipant(userId: String) {
        peerConnections[userId]?.close()
        peerConnections[userId]?.dispose()
        peerConnections.remove(userId)
        remoteAudioTrack.remove(userId)
        remoteVideoTrack.remove(userId)
        Log.d("GroupRTC","Removed participant: $userId")
    }

    private fun sendIceCandidate(target: String, candidate: IceCandidate) {
        if (isDisposed) return
        val data = mapOf(
            "candidate" to candidate.sdp,
            "sdpMid" to candidate.sdpMid,
            "sdpMLineIndex" to candidate.sdpMLineIndex
        )

        db.child("groupCalls/$roomId/ice/${currentUserId}_$target")
            .push()
            .setValue(data)
    }

    fun setMicrophoneEnabled(enabled: Boolean) {
        localAudioTrack?.setEnabled(enabled)
    }

    fun setCameraEnabled(enabled: Boolean) {
        localVideoTrack?.setEnabled(enabled)
    }

    fun leaveRoom() {
        if (isDisposed) return
        try {
            db.child("groupCalls/$roomId/participants/$currentUserId").removeValue()
            db.child("groupCalls/$roomId/offers").orderByKey()
                .startAt(currentUserId).get()

            peerConnections.values.forEach {
                it.close()
                it.dispose()
            }
            peerConnections.clear()

            localAudioTrack?.dispose()
            localAudioTrack = null

            localVideoTrack?.dispose()
            localVideoTrack = null

            stopLocalVideo()

            factory?.dispose()
            factory = null

            eglBase.release()
        } catch (e: Exception) {
            Log.e("GroupRTC","Error leaving ${e.message}")
        }

        db.child("groupCalls/$roomId/participants/$currentUserId").removeValue()
    }
}