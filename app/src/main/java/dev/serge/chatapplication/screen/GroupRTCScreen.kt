package dev.serge.chatapplication.screen

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.os.Handler
import android.os.Looper
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import com.google.firebase.auth.FirebaseAuth
import dev.serge.chatapplication.GroupRTCManager
import dev.serge.chatapplication.screen.neobrut.BrutalButton
import org.webrtc.PeerConnection
import org.webrtc.RendererCommon
import org.webrtc.SurfaceViewRenderer

@Composable
fun GroupRTCScreen(
    roomId: String,
    isCaller: Boolean,
    onCallEnded: () -> Unit
) {
    val context = LocalContext.current
    val localView = remember { SurfaceViewRenderer(context) }
    val remoteView = remember { mutableStateOf<Map<String, SurfaceViewRenderer>>(emptyMap()) }

    var hasPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.RECORD_AUDIO
            ) == PackageManager.PERMISSION_GRANTED
        )
    }

    var cameraPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.CAMERA
            ) == PackageManager.PERMISSION_GRANTED
        )
    }

    val audioLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) {isGranted ->
        hasPermission = isGranted
    }

    val videoLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) {isGranted ->
        cameraPermission = isGranted
    }

    LaunchedEffect(Unit) {
        if (!hasPermission) {
            audioLauncher.launch(Manifest.permission.RECORD_AUDIO)
        }
        if (!cameraPermission) {
            videoLauncher.launch(Manifest.permission.CAMERA)
        }
    }

    val currentUserId = FirebaseAuth.getInstance().currentUser?.uid ?: ""

    var connectionState by remember {
        mutableStateOf(PeerConnection.IceConnectionState.NEW)
    }
    var isMuted by remember { mutableStateOf(false) }
    var isVideoEnabled by remember { mutableStateOf(false) }
    var isCallActive by remember { mutableStateOf(false) }
    var callDuration by remember { mutableStateOf(0) }
    var callAccepted by remember { mutableStateOf(isCaller) }

    val manager = remember {
        GroupRTCManager(
            context = context.applicationContext,
            currentUserId = currentUserId,
            roomId = roomId
        ).apply {
            this.onCallEnded = {
                onCallEnded()
            }
        }
    }

    LaunchedEffect(Unit) {
        val egl = manager.eglBase.eglBaseContext

        localView.init(egl,null)
        localView.setMirror(true)
        localView.setScalingType(RendererCommon.ScalingType.SCALE_ASPECT_FILL)

        manager.onRemoteVideoTrack = { userId, track ->
            Handler(Looper.getMainLooper()).post {
                val renderer = SurfaceViewRenderer(context).apply {
                    init(egl, null)
                    setMirror(false)
                    setScalingType(RendererCommon.ScalingType.SCALE_ASPECT_FILL)
                }
                track.addSink(renderer)
                remoteView.value = remoteView.value.toMutableMap().apply {
                    put(userId, renderer)
                }
            }
        }

        manager.onParticipationLeft = { userId ->
            Handler(Looper.getMainLooper()).post {
                val renderer = remoteView.value[userId]
                renderer?.release()
                remoteView.value = remoteView.value.toMutableMap().apply {
                    remove(userId)
                }
            }
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            try {
                remoteView.value.values.forEach { renderer ->
                    try { renderer.release() } catch (_: Exception) {}
                }

                localView.release()

                manager.leaveRoom()
            } catch (_: Exception) {}
        }
    }

    LaunchedEffect(hasPermission, callAccepted) {
        if (hasPermission && callAccepted) {
            val track = manager.startLocalVideo()
            track?.addSink(localView)
            isVideoEnabled = true

            manager.joinRoom()
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.surfaceContainer)
    ) {
        if (!callAccepted) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .background(MaterialTheme.colorScheme.surfaceContainer)
                    .padding(24.dp),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                BrutalButton(
                    text = "ACCEPT CALL",
                    onClick = {
                        callAccepted = true
                        isVideoEnabled = true
                        manager.joinRoom()
                    },
                    modifier = Modifier.fillMaxWidth(),
                    color = MaterialTheme.colorScheme.tertiary
                )
                
                androidx.compose.foundation.layout.Spacer(modifier = Modifier.size(16.dp))

                BrutalButton(
                    text = "DECLINE",
                    onClick = {
                        onCallEnded()
                    },
                    modifier = Modifier.fillMaxWidth(),
                    color = MaterialTheme.colorScheme.error
                )
            }
        } else {
            LazyVerticalGrid(
                columns = GridCells.Adaptive(150.dp),
                modifier = Modifier
                    .fillMaxSize()
                    .systemBarsPadding()
                    .navigationBarsPadding()
            ) {
                items(remoteView.value.keys.toList(), key = { it }) {userId ->
                    val renderer = remoteView.value[userId]
                    renderer?.let {
                        AndroidView(
                            factory = {_-> it },
                            modifier = Modifier
                                .padding(4.dp)
                                .aspectRatio(1f)
                                .background(Color.DarkGray)
                        )
                    }
                }
            }
            Box(
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(end = 12.dp, bottom = 180.dp)
                    .size(120.dp, 160.dp)
                    .background(Color.Gray, RoundedCornerShape(12.dp))
            ) {
                AndroidView(
                    factory = {localView},
                    modifier = Modifier.fillMaxSize()
                )
            }

            Column(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(16.dp, 16.dp, 16.dp, 20.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {

                BrutalButton(
                    text = if (isMuted) "UNMUTE" else "MUTE",
                    onClick = {
                        isMuted = !isMuted
                        manager.setMicrophoneEnabled(!isMuted)
                    },
                    modifier = Modifier.fillMaxWidth(),
                    color = if (isMuted)
                        MaterialTheme.colorScheme.error
                    else
                        MaterialTheme.colorScheme.tertiary
                )

                BrutalButton(
                    text = if (isVideoEnabled) "VIDEO OFF" else "VIDEO ON",
                    onClick = {
                        isVideoEnabled = !isVideoEnabled
                    },
                    modifier = Modifier.fillMaxWidth(),
                    color = if (isVideoEnabled)
                        MaterialTheme.colorScheme.error
                    else
                        MaterialTheme.colorScheme.tertiary
                )

                BrutalButton(
                    text = "LEAVE",
                    onClick = {
                        manager.leaveRoom()
                        onCallEnded()
                    },
                    modifier = Modifier.fillMaxWidth(),
                    color = MaterialTheme.colorScheme.error
                )
            }
        }
    }
}

private fun getConnectionState(state: PeerConnection.IceConnectionState): String {
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
        PeerConnection.IceConnectionState.CHECKING -> Color.Black
        PeerConnection.IceConnectionState.DISCONNECTED,
        PeerConnection.IceConnectionState.FAILED -> Color.Red
        else -> Color.Gray
    }
}
@SuppressLint("DefaultLocale")
private fun formatCallDuration(seconds: Int): String {
    val minutes = seconds/ 60
    val seconds = seconds % 60
    return String.format("%02d:%02d", minutes, seconds)
}