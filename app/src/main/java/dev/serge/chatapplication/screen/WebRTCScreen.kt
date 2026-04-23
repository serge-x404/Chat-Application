package dev.serge.chatapplication.screen

import android.annotation.SuppressLint
import android.util.Log
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
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.google.firebase.auth.FirebaseAuth
import dev.serge.chatapplication.WebRTCManager
import dev.serge.chatapplication.screen.neobrut.BrutalButton
import org.webrtc.PeerConnection

@Composable
fun WebRTCCallScreen(
    chatId: String,
    otherUserName: String,
    otherUserId: String,
    onCallEnded: () -> Unit
) {
    val context = LocalContext.current
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

    val onIncomingCallCallback = remember { {isIncomingCall = true} }

    LaunchedEffect(webRTCManager) {
        webRTCManager.onConnectionStateChanged = { state ->
            connectionState = state
            Log.d("WebRTC", "Connection state changed: $state")
            if (state == PeerConnection.IceConnectionState.CONNECTED ||
                state == PeerConnection.IceConnectionState.COMPLETED
            ) {
                isCallActive = true
            }
        }

//        webRTCManager.onSignalingStateChanged = { state: PeerConnection.SignalingState ->
//            signalingState = state
//        }

        webRTCManager.onRemoteStreamAdded = { _ ->
            Log.d("WebRTC", "Remote stream added")
        }

        webRTCManager.onIncomingCall = {
            Log.d("WebRTC","Incoming Call detected")
            isIncomingCall = true
        }
    }

    DisposableEffect(Unit) {
        try {
            Log.d("WebRTC","Setting up WebRTC")
            webRTCManager.createLocalStream()
            Log.d("WebRTC","Local stream created")

            webRTCManager.initiateCall()
        } catch (e: Exception) {
            Log.e("WebRTC", "Error initializing call: ${e.message}")
            e.printStackTrace()
        }

        onDispose {
            Log.d("WebRTC","Disposing WebRTC")
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
            callerName = otherUserName,
            onAccept = {
                Log.d("WebRTC","Call accepted")
                callAccepted = true
                isIncomingCall = false
            },
            onReject = {
                Log.d("WebRTC","Call rejected!")
                webRTCManager.endCall()
                onCallEnded()
            }
        )
        return
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .systemBarsPadding()
            .navigationBarsPadding()
            .background(MaterialTheme.colorScheme.background)
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
                color = MaterialTheme.colorScheme.onBackground
            )

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = getConnectionStateText(connectionState),
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
                color = getConnectionStateColor(connectionState)
            )

            Spacer(modifier = Modifier.height(16.dp))

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(
                        MaterialTheme.colorScheme.surfaceContainer,
                        shape = RoundedCornerShape(8.dp)
                    )
                    .padding(12.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                CircularProgressIndicator(
                    modifier = Modifier.size(24.dp),
                    color = if (isCallActive) Color.Green else Color.Yellow,
                    strokeWidth = 2.dp
                )

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = if (isCallActive) "Connected" else "Connecting...",
                        fontWeight = FontWeight.Bold,
                        fontSize = 12.sp
                    )
                    Text(
                        text = "Signaling: $signalingState",
                        fontWeight = FontWeight.Normal,
                        fontSize = 10.sp,
                        color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f)
                    )
                }
            }
        }

        if (isCallActive) {
            Text(
                text = formatCallDuration(callDuration),
                fontSize = 48.sp,
                fontWeight = FontWeight.Black,
                color = MaterialTheme.colorScheme.onBackground
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
        PeerConnection.IceConnectionState.CHECKING -> Color.Yellow
        else -> Color.Gray
    }
}

@SuppressLint("DefaultLocale")
private fun formatCallDuration(seconds: Int): String {
    val minutes = seconds / 60
    val secs = seconds % 60
    return String.format("%02d:%02d", minutes, secs)
}