package com.chatwave.app.feature.chat

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatScreen(title: String, vm: ChatViewModel, onBack: () -> Unit) {
    val s by vm.state.collectAsStateWithLifecycle()
    val list = rememberLazyListState()
    var draft by remember { mutableStateOf("") }
    LaunchedEffect(s.messages.size) { if (s.messages.isNotEmpty()) list.animateScrollToItem(s.messages.lastIndex) }
    Scaffold(
        topBar = { TopAppBar(
            title = { Column { Text(title); if (s.peerTyping) Text("typing…", style = MaterialTheme.typography.labelSmall) } },
            navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") } }) },
        bottomBar = {
            Row(Modifier.fillMaxWidth().navigationBarsPadding().imePadding().padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
                OutlinedTextField(draft, { draft = it; vm.typing(it.isNotEmpty()) }, Modifier.weight(1f), placeholder = { Text("Message") }, maxLines = 4)
                IconButton(onClick = { vm.send(draft); draft = ""; vm.typing(false) }, enabled = draft.isNotBlank(), modifier = Modifier.size(48.dp)) {
                    Icon(Icons.AutoMirrored.Filled.Send, "Send") }
            }
        },
    ) { pad ->
        Column(Modifier.padding(pad).fillMaxSize()) {
            if (!s.connected) Text("Connecting… messages will be sent when you're back online.", Modifier.fillMaxWidth().padding(8.dp), style = MaterialTheme.typography.labelMedium)
            when {
                s.loading -> Box(Modifier.fillMaxSize(), Alignment.Center) { CircularProgressIndicator() }
                s.error != null && s.messages.isEmpty() -> Column(Modifier.fillMaxSize(), Arrangement.Center, Alignment.CenterHorizontally) {
                    Text(s.error!!); Button(onClick = { vm.load() }) { Text("Retry") } }
                s.messages.isEmpty() -> Box(Modifier.fillMaxSize(), Alignment.Center) { Text("Say hello 👋") }
                else -> LazyColumn(state = list, contentPadding = PaddingValues(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    if (s.nextBefore != null) item { TextButton(onClick = { vm.loadOlder() }) { Text("Load older messages") } }
                    items(s.messages, key = { it.clientId }) { Bubble(it) { id -> vm.retry(id) } }
                }
            }
        }
    }
}

@Composable
private fun Bubble(m: UiMessage, onRetry: (String) -> Unit) {
    val mine = m.mine
    Column(Modifier.fillMaxWidth(), horizontalAlignment = if (mine) Alignment.End else Alignment.Start) {
        Surface(
            shape = RoundedCornerShape(16.dp),
            color = if (mine) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant,
            modifier = Modifier.widthIn(max = 300.dp).let { if (m.state == SendState.FAILED) it.clickable { onRetry(m.clientId) } else it },
        ) { Text(m.body, Modifier.padding(horizontal = 12.dp, vertical = 8.dp)) }
        if (mine) Text(
            when (m.state) { SendState.SENDING -> "Sending…"; SendState.SENT -> "Sent"; SendState.DELIVERED -> "Delivered"
                SendState.READ -> "Read"; SendState.FAILED -> "Failed — tap to retry" },
            style = MaterialTheme.typography.labelSmall,
            color = if (m.state == SendState.FAILED) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
