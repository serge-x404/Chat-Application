package dev.serge.chatapplication.screen

import android.Manifest
import android.annotation.SuppressLint
import android.content.pm.PackageManager
import android.util.Log
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import com.google.firebase.auth.FirebaseAuth
import dev.serge.chatapplication.WebRTCManager
import dev.serge.chatapplication.screen.neobrut.BrutalButton
import dev.serge.chatapplication.screen.neobrut.BrutalLoader
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.webrtc.PeerConnection
import org.webrtc.SurfaceViewRenderer

@Composable
fun WebRTCCallScreen(
    chatId: String,
    otherUserId: String,
    otherUserName: String,
    isCaller: Boolean,
    onCallEnded: () -> Unit
) {
    val context = LocalContext.current
    val localView = remember { SurfaceViewRenderer(context) }
    val remoteView = remember { SurfaceViewRenderer(context) }

    var hasPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.RECORD_AUDIO
            ) == PackageManager.PERMISSION_GRANTED
        )
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) {isGranted->
        hasPermission = isGranted
        Log.d("WebRTC","Microphone permission: $isGranted")
    }

    LaunchedEffect(Unit) {
        if (!hasPermission) {
            permissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
        }
    }
    val currentUserId = FirebaseAuth.getInstance().currentUser?.uid ?: ""

    var connectionState by remember {
        mutableStateOf(PeerConnection.IceConnectionState.NEW)
    }
    var isMuted by remember { mutableStateOf(false) }
    var isCallActive by remember { mutableStateOf(false) }
    var signalingState by remember { mutableStateOf(PeerConnection.SignalingState.STABLE) }
    var callDuration by remember { mutableStateOf(0) }
    var isIncomingCall by remember { mutableStateOf(false) }
    var callAccepted by remember { mutableStateOf(false) }

    val webRTCManager = remember {
        WebRTCManager(
            context = context.applicationContext,
            currentUserId = currentUserId,
            otherUserId = otherUserId
        )
    }

    val eglBaseContext by webRTCManager._eglBaseContext.collectAsState()
    val videoTrack by webRTCManager._videoTrack.collectAsState()
    val remoteVideoTrack by webRTCManager._remoteVideoTrack.collectAsState()

    LaunchedEffect(eglBaseContext) {
        if (eglBaseContext != null) {
            localView.init(eglBaseContext, null)
            localView.setMirror(true)
            localView.setEnableHardwareScaler(true)
            localView.setZOrderOnTop(true)

            remoteView.init(eglBaseContext, null)
            remoteView.setMirror(false)
            remoteView.setEnableHardwareScaler(true)
        }
    }

    LaunchedEffect(videoTrack) {
        try {
            videoTrack?.addSink(localView)
        } catch (_: Exception) {}
    }

    LaunchedEffect(remoteVideoTrack) {
        try {
            remoteVideoTrack?.addSink(remoteView)
        } catch (_: Exception) {}
    }

    DisposableEffect(Unit) {
        onDispose {
            try {
                localView.clearImage()
                localView.release()
                remoteView.clearImage()
                remoteView.release()
            } catch (_: Exception) {}
        }
    }

    val scope = rememberCoroutineScope()

    LaunchedEffect(webRTCManager) {
        webRTCManager.onConnectionStateChanged = { state ->
            scope.launch(Dispatchers.Main) {
                connectionState = state
                Log.d("WebRTC", "Connection state changed: $state")

                if (state == PeerConnection.IceConnectionState.CONNECTED ||
                    state == PeerConnection.IceConnectionState.COMPLETED
                ) {
                    isCallActive = true
                } else if (state == PeerConnection.IceConnectionState.DISCONNECTED ||
                    state == PeerConnection.IceConnectionState.CLOSED ||
                    state == PeerConnection.IceConnectionState.FAILED
                ) {
                    isCallActive = false
                    onCallEnded()
                }
            }
        }

        webRTCManager.onRemoteStreamAdded = { _ ->
            scope.launch(Dispatchers.Main) {
                Log.d("WebRTC", "Remote stream added")
            }
        }

        webRTCManager.onIncomingCall = {
            scope.launch(Dispatchers.Main) {
                Log.d("WebRTC", "Incoming Call detected")
                isIncomingCall = true
            }
        }

        webRTCManager.onCallEnded = {
            scope.launch(Dispatchers.Main) {
                Log.d("WebRTC", "Call ended from manager")
                onCallEnded()
            }
        }
    }

    LaunchedEffect(hasPermission) {
        if (hasPermission) {
            try {
                Log.d("WebRTC","Setting up WebRTC")
                webRTCManager.createLocalStream()
                Log.d("WebRTC","Local stream created")

                if (isCaller) {
                    Log.d("WebRTC","I am caller - initiating")
                    webRTCManager.setCallerName(otherUserName)
                    webRTCManager.initiateCall()
                } else {
                    Log.d("WebRTC","I am receiver - waiting")
                }
            } catch (e: Exception) {
                Log.e("WebRTC", "Error initializing call: ${e.message}")
            }
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            Log.d("WebRTC","Disposing WebRTC Screen")
            try {
                webRTCManager.disconnect()
            } catch (e: Exception) {
                Log.e("WebRTC","Error: ${e.message}")
            }
        }
    }

    LaunchedEffect(isCallActive) {
        if (isCallActive) {
            while (isCallActive) {
                kotlinx.coroutines.delay(1000)
                callDuration++
            }
        }
    }

    if (isIncomingCall && !callAccepted) {
        IncomingCallScreen(
            callerName = currentUserId,
            onAccept = {
                Log.d("WebRTC","Call accepted")
                callAccepted = true
                isIncomingCall = false
                webRTCManager.acceptCall()
            },
            onReject = {
                Log.d("WebRTC","Call rejected!")
                webRTCManager.endCall(rejected = true)
                onCallEnded()
            }
        )
        return
    }

    androidx.compose.foundation.layout.Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
    ) {
        // Remote Video (Full Screen)
        AndroidView(
            factory = { remoteView },
            modifier = Modifier.fillMaxSize()
        )

        // Overlay Content
        Column(
            modifier = Modifier
                .fillMaxSize()
                .systemBarsPadding()
                .navigationBarsPadding()
                .padding(16.dp),
            verticalArrangement = Arrangement.SpaceBetween,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = otherUserName.uppercase(),
                    fontSize = 32.sp,
                    fontWeight = FontWeight.Black,
                    color = Color.White
                )

                Spacer(modifier = Modifier.height(8.dp))

                Text(
                    text = getConnectionStateText(connectionState),
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    color = getConnectionStateColor(connectionState)
                )

                Spacer(modifier = Modifier.height(16.dp))

                // Local Video (PiP)
                androidx.compose.foundation.layout.Box(
                    modifier = Modifier
                        .align(Alignment.End)
                        .size(120.dp, 180.dp)
                        .background(Color.DarkGray, RoundedCornerShape(12.dp))
                ) {
                    AndroidView(
                        factory = { localView },
                        modifier = Modifier.fillMaxSize()
                    )
                }
            }

            if (isCallActive) {
                Text(
                    text = formatCallDuration(callDuration),
                    fontSize = 48.sp,
                    fontWeight = FontWeight.Black,
                    color = Color.White
                )
            }

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                BrutalButton(
                    text = if (isMuted) "UNMUTE" else "MUTE",
                    onClick = {
                        isMuted = !isMuted
                        webRTCManager.setMicrophoneEnabled(!isMuted)
                    },
                    modifier = Modifier.fillMaxWidth(),
                    color = if (isMuted) MaterialTheme.colorScheme.error
                    else MaterialTheme.colorScheme.tertiary
                )

                BrutalButton(
                    text = "END CALL",
                    onClick = {
                        webRTCManager.endCall()
                        onCallEnded()
                    },
                    modifier = Modifier.fillMaxWidth(),
                    color = MaterialTheme.colorScheme.error
                )
            }
        }
    }
}

private fun getConnectionStateText(state: PeerConnection.IceConnectionState): String {
    return when (state) {
        PeerConnection.IceConnectionState.NEW -> "Initializing..."
        PeerConnection.IceConnectionState.CHECKING -> "Checking..."
        PeerConnection.IceConnectionState.CONNECTED -> "Connected"
        PeerConnection.IceConnectionState.COMPLETED -> "Connected"
        PeerConnection.IceConnectionState.FAILED -> "Connection Failed"
        PeerConnection.IceConnectionState.DISCONNECTED -> "Disconnected"
        PeerConnection.IceConnectionState.CLOSED -> "Call Ended"
    }
}

private fun getConnectionStateColor(state: PeerConnection.IceConnectionState): Color {
    return when (state) {
        PeerConnection.IceConnectionState.CONNECTED,
        PeerConnection.IceConnectionState.COMPLETED -> Color.Green
        PeerConnection.IceConnectionState.FAILED -> Color.Red
        PeerConnection.IceConnectionState.CHECKING -> Color.Black
        else -> Color.Gray
    }
}

@SuppressLint("DefaultLocale")
private fun formatCallDuration(seconds: Int): String {
    val minutes = seconds / 60
    val secs = seconds % 60
    return String.format("%02d:%02d", minutes, secs)
}