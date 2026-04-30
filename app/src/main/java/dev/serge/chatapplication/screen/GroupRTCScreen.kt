package dev.serge.chatapplication.screen

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
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
import org.webrtc.SurfaceViewRenderer

@Composable
fun GroupRTCScreen(
    roomId: String,
    onCallEnded: () -> Unit
) {
    val context = LocalContext.current
    val localView = remember { SurfaceViewRenderer(context) }
    val remoteView = remember { mutableMapOf<String, SurfaceViewRenderer>() }

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
    var isIncomingCall by remember { mutableStateOf(false) }
    var callAccepted by remember { mutableStateOf(false) }

    val manager = remember {
        GroupRTCManager(
            context = context.applicationContext,
            currentUserId = currentUserId,
            roomId = roomId
        )
    }

    LaunchedEffect(Unit) {
        val egl = manager.eglBase.eglBaseContext

        localView.init(egl,null)
        localView.setMirror(true)

        manager.onRemoteVideoTrack = { userId, track ->
            val renderer = SurfaceViewRenderer(context).apply {
                init(egl,null)
                setMirror(false)
            }

            remoteView[userId] = renderer
            track.addSink(renderer)
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            try {
                localView.release()
                remoteView.values.forEach {
                    it.release()
                }
                manager.leaveRoom()
            } catch (_: Exception) { }
        }
    }

    LaunchedEffect(hasPermission) {
        if (hasPermission) {
            manager.joinRoom()
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
    ) {
        LazyVerticalGrid(
            columns = GridCells.Adaptive(150.dp),
            modifier = Modifier.fillMaxSize()
        ) {
            items(remoteView.keys.toList()) {userId ->
                val renderer = remoteView[userId]

                renderer?.let {
                    AndroidView(
                        factory = {_: Context -> it },
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
                .padding(16.dp)
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
                .padding(16.dp),
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
                    manager.setCameraEnabled(isVideoEnabled)
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