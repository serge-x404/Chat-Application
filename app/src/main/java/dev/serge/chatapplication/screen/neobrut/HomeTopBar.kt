package dev.serge.chatapplication.screen.neobrut

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.BottomSheet
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.edit
import com.google.firebase.auth.FirebaseAuth
import dev.serge.chatapplication.screen.auth.ChatManager
import dev.serge.chatapplication.screen.auth.GroupManager
import dev.serge.chatapplication.screen.auth.User

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BrutalHomeTopBar(
    title: String = "CHATS",
    onLogout: () -> Unit = {}
) {

    var showDialog by remember { mutableStateOf(false) }

    val context = LocalContext.current
    val sharedPreferences = context.getSharedPreferences("chatApp", Context.MODE_PRIVATE )

    val bottomSheet = rememberModalBottomSheetState()
    var showBottomSheet by remember { mutableStateOf(false) }
    val chatManager = remember { ChatManager() }
    val currentUid = FirebaseAuth.getInstance().uid ?: ""
    var user by remember { mutableStateOf<List<User>>(emptyList()) }
    val groupManager = remember { GroupManager() }

    if (showBottomSheet) {
        ModalBottomSheet(
            sheetState = bottomSheet,
            onDismissRequest = {showBottomSheet = false}
        ) {
            var groupName by rememberSaveable { mutableStateOf("") }
            var selectedMembers by remember { mutableStateOf<Set<String>>(emptySet()) }
            var isCreating by remember { mutableStateOf(false) }
            Text(
                text = "CREATE GROUP",
                style = MaterialTheme.typography.titleLarge,
                color = Color.Black,
                modifier = Modifier.align(Alignment.CenterHorizontally)
            )

            LaunchedEffect(Unit) {
                chatManager.getAllUsers(currentUid) {users ->
                    user = users
                }
            }

            BrutalUserTextField(
                value = groupName,
                onValueChange = {groupName = it},
                placeholder = "GROUP NAME"
            )

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp)
                    .padding(horizontal = 12.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    "SELECT MEMBERS",
                    fontWeight = FontWeight.Black,
                    fontSize = 14.sp,
                    color = Color.Black
                )
                Text(
                    text = "${selectedMembers.size} selected",
                    fontWeight = FontWeight.Bold,
                    fontSize = 12.sp,
                    color = Color.Black
                )
            }
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp)
                    .heightIn(max = 250.dp)
                    .border(3.dp, MaterialTheme.colorScheme.surface)
                    .background(MaterialTheme.colorScheme.tertiary)
                    .padding(8.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                items(user, key = {it.uid}) {user ->
                    MemberCheckboxItem(
                        name = user.userName,
                        isSelected = user.uid in selectedMembers,
                        onToggle = {
                            selectedMembers = if (user.uid in selectedMembers) {
                                selectedMembers - user.uid
                            }
                            else {
                                selectedMembers + user.uid
                            }
                        }
                    )
                }
            }
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 16.dp),
                horizontalArrangement = Arrangement.SpaceAround,
                verticalAlignment = Alignment.CenterVertically
            ) {
                BrutalButton(
                    text = "CANCEL",
                    onClick = {showBottomSheet = false},
                    modifier = Modifier,
                    color = MaterialTheme.colorScheme.primary
                )

                BrutalButton(
                    text = if (isCreating) "CREATING..." else "CREATE",
                    onClick = {
                        if (groupName.isBlank()) {
                            Log.e("Group", "Group name required")
                            return@BrutalButton
                        }

                        if (selectedMembers.size < 2) {
                            Log.e("Group","Select at least three members")
                            return@BrutalButton
                        }
                        isCreating = true
                        groupManager.createGroup(
                            name = groupName,
                            memberId = selectedMembers.toList(),
                            onSuccess = {groupId ->
                                isCreating = false
                                showBottomSheet = false
                            },
                            onError = {error ->
                                isCreating = false
                                Log.e("Group","Failed $error")
                            }
                        )
                    },
                    modifier = Modifier,
                    color = MaterialTheme.colorScheme.tertiary,
                )
            }
            Spacer(Modifier.height(12.dp))
        }
    }

    if (showDialog) {
        BrutalLogoutDialog(
            show = showDialog,
            onConfirm = {
                FirebaseAuth.getInstance().signOut()
                sharedPreferences.edit { putBoolean("isUserLoggedIn",false) }
                showDialog = false
                onLogout()
            },
            onDismiss = { showDialog = false }
        )
    }
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .offset(6.dp, 6.dp)
            .background(MaterialTheme.colorScheme.background)
    ) {
        Row(
            modifier = Modifier
                .offset((-6).dp, (-6).dp)
                .border(3.dp, MaterialTheme.colorScheme.surface)
                .background(MaterialTheme.colorScheme.secondary)
                .padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = title,
                fontWeight = FontWeight.Black,
                fontSize = 26.sp,
                modifier = Modifier.weight(1f)
            )
            BrutalIconButton(
                text = "ADD GROUP",
                onClick = {showBottomSheet = true}
            )
            Spacer(Modifier.width(6.dp))
            BrutalIconButton(
                text = "LOGOUT",
                onClick = { showDialog = true}
            )
        }
    }
}

@Composable
fun MemberCheckboxItem(
    name: String,
    isSelected: Boolean,
    onToggle: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onToggle() }
            .padding(8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            text = name,
            fontWeight = FontWeight.Bold,
            fontSize = 14.sp,
            color = Color.Black
        )

        if (isSelected) {
            Box(
                modifier = Modifier
                    .size(20.dp)
                    .background(Color.Black),
                contentAlignment = Alignment.Center
            ) {
                Text("✓", color = Color.White, fontSize = 12.sp)
            }
        } else {
            Box(
                modifier = Modifier
                    .size(20.dp)
                    .border(2.dp, Color.Black)
            )
        }
    }
}