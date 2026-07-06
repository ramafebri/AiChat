package com.rama.aichat.ui.components

import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Create
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationDrawerItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.rama.aichat.data.model.ChatSession

enum class AppNavDestination {
    Chat,
    GemmaChat,
    Skills
}

@Composable
fun AppDrawer(
    selectedDestination: AppNavDestination,
    sessions: List<ChatSession>,
    currentSessionId: String?,
    onNavigateToChat: () -> Unit,
    onNavigateToGemmaChat: () -> Unit,
    onNavigateToSkills: () -> Unit,
    onSessionClick: (String) -> Unit,
    onNewChat: () -> Unit,
    onDeleteSession: (String) -> Unit
) {
    LazyColumn {
        item {
            Text(
                text = "AiChat",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 16.dp)
            )
            HorizontalDivider()
        }

        item {
            NavigationDrawerItem(
                label = { Text("Chat") },
                selected = selectedDestination == AppNavDestination.Chat,
                onClick = onNavigateToChat,
                icon = {
                    Icon(
                        imageVector = Icons.Default.Home,
                        contentDescription = null
                    )
                },
                modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
            )
            NavigationDrawerItem(
                label = { Text("On-device (Gemma)") },
                selected = selectedDestination == AppNavDestination.GemmaChat,
                onClick = onNavigateToGemmaChat,
                icon = {
                    Icon(
                        imageVector = Icons.Default.Memory,
                        contentDescription = null
                    )
                },
                modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
            )
            NavigationDrawerItem(
                label = { Text("Skills") },
                selected = selectedDestination == AppNavDestination.Skills,
                onClick = onNavigateToSkills,
                icon = {
                    Icon(
                        imageVector = Icons.Default.Create,
                        contentDescription = null
                    )
                },
                modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
            )
            HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
        }

        item {
            Text(
                text = "Chat History",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
            )
        }

        items(sessions, key = { it.id }) { session ->
            NavigationDrawerItem(
                label = {
                    Text(
                        text = session.title,
                        maxLines = 1,
                        style = MaterialTheme.typography.bodyMedium
                    )
                },
                selected = selectedDestination == AppNavDestination.Chat &&
                        session.id == currentSessionId,
                onClick = { onSessionClick(session.id) },
                badge = {
                    IconButton(onClick = { onDeleteSession(session.id) }) {
                        Icon(
                            imageVector = Icons.Default.Delete,
                            contentDescription = "Delete session",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                },
                modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
            )
        }

        item {
            HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
            NavigationDrawerItem(
                label = { Text("New Chat") },
                selected = false,
                onClick = onNewChat,
                icon = {
                    Icon(
                        imageVector = Icons.Default.Add,
                        contentDescription = null
                    )
                },
                modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
            )
        }
    }
}
