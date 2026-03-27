package com.airmesh.ui.screens.chatlist

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.airmesh.data.db.ConversationSummary
import com.airmesh.ui.components.NearbyDevicesRow
import com.airmesh.ui.theme.AirMeshBlack
import com.airmesh.ui.theme.Gold
import java.text.SimpleDateFormat
import java.util.*

private val dateFormat = SimpleDateFormat("MMM d", Locale.getDefault())
private val timeFormat = SimpleDateFormat("HH:mm", Locale.getDefault())

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatListScreen(
    onOpenConversation: (String) -> Unit,
    onNewChat: () -> Unit,
    viewModel: ChatListViewModel = hiltViewModel()
) {
    val username by viewModel.username.collectAsState()
    val nearbyDevices by viewModel.nearbyDevices.collectAsState()
    val conversations by viewModel.conversations.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("AirMesh", color = Gold, style = MaterialTheme.typography.titleLarge)
                        if (username.isNotBlank()) {
                            Text(
                                text = username,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = onNewChat,
                containerColor = Gold,
                contentColor = AirMeshBlack
            ) {
                Icon(Icons.Default.Edit, contentDescription = "New chat")
            }
        }
    ) { padding ->
        if (username.isBlank()) {
            Box(
                modifier = Modifier.fillMaxSize().padding(padding),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("Welcome to AirMesh", style = MaterialTheme.typography.headlineMedium, color = Gold)
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        "Set your username in Settings to start chatting",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            return@Scaffold
        }

        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            item {
                NearbyDevicesRow(devices = nearbyDevices)
            }

            if (conversations.isEmpty()) {
                item {
                    Box(
                        modifier = Modifier.fillMaxWidth().padding(48.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "No conversations yet.\nTap + to start one.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            } else {
                items(conversations) { conv ->
                    ConversationRow(
                        summary = conv,
                        onClick = { onOpenConversation(conv.peer) }
                    )
                    HorizontalDivider(
                        color = MaterialTheme.colorScheme.surfaceVariant,
                        thickness = 0.5.dp,
                        modifier = Modifier.padding(start = 72.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun ConversationRow(
    summary: ConversationSummary,
    onClick: () -> Unit
) {
    val timeStr = remember(summary.lastTs) {
        val now = System.currentTimeMillis()
        val diff = now - summary.lastTs
        if (diff < 24 * 60 * 60 * 1000) timeFormat.format(Date(summary.lastTs))
        else dateFormat.format(Date(summary.lastTs))
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .size(50.dp)
                .clip(CircleShape)
                .background(Gold)
        ) {
            Text(
                text = summary.peer.take(1).uppercase(),
                color = AirMeshBlack,
                fontSize = 20.sp
            )
        }
        Spacer(modifier = Modifier.width(14.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = summary.peer,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        Text(
            text = timeStr,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun remember(key: Long, block: () -> String): String {
    return androidx.compose.runtime.remember(key) { block() }
}
