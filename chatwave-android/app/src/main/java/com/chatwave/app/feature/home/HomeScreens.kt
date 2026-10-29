package com.chatwave.app.feature.home

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.chatwave.app.data.Chat
import com.chatwave.app.data.User

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatListScreen(vm: ChatListViewModel, onOpen: (String, String) -> Unit, onSearch: () -> Unit, onGlobal: () -> Unit, onGroup: () -> Unit, onProfile: () -> Unit, onLogout: () -> Unit) {
    val s by vm.state.collectAsStateWithLifecycle()
    Scaffold(
        topBar = { TopAppBar(title = { Text("ChatWave") }, actions = {
            IconButton(onClick = onProfile) { Icon(Icons.Default.Person, "Profile") }
            IconButton(onClick = onLogout) { Icon(Icons.AutoMirrored.Filled.Logout, "Log out") } }) },
        bottomBar = {
            NavigationBar {
                NavigationBarItem(selected = true, onClick = {}, icon = { Icon(Icons.Default.Groups, "Chats") }, label = { Text("Chats") })
                NavigationBarItem(selected = false, onClick = onProfile, icon = { Icon(Icons.Default.Person, "Profile") }, label = { Text("Profile") })
            }
        },
        floatingActionButton = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                SmallFloatingActionButton(onClick = onGlobal) { Text("#") }
                SmallFloatingActionButton(onClick = onGroup) { Icon(Icons.Default.Groups, "New group") }
                FloatingActionButton(onClick = onSearch) { Icon(Icons.Default.Edit, "New chat") }
            }
        },
    ) { pad ->
        Column(Modifier.padding(pad).fillMaxSize()) {
            if (!s.connected && !s.loading) Text(if (s.offline) "Offline" else "Reconnecting…", Modifier.fillMaxWidth().padding(8.dp), color = MaterialTheme.colorScheme.error)
            when {
                s.loading -> Box(Modifier.fillMaxSize(), Alignment.Center) { CircularProgressIndicator() }
                s.error != null && s.chats.isEmpty() -> Column(Modifier.fillMaxSize(), Arrangement.Center, Alignment.CenterHorizontally) {
                    Text(s.error!!); Button(onClick = { vm.refresh() }) { Text("Retry") } }
                s.chats.isEmpty() -> Box(Modifier.fillMaxSize().padding(32.dp), Alignment.Center) {
                    Text("No conversations yet. Tap the pencil to find someone by username and say hi.") }
                else -> LazyColumn { items(s.chats, key = { it.id }) { ChatRow(it, onOpen) } }
            }

        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProfileScreen(vm: ProfileViewModel, onBack: () -> Unit, onLogout: () -> Unit) {
    val s by vm.state.collectAsStateWithLifecycle()
    Scaffold(topBar = { TopAppBar(title = { Text("Profile") }, navigationIcon = {
        IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") }
    }, actions = { IconButton(onClick = onLogout) { Icon(Icons.AutoMirrored.Filled.Logout, "Log out") } }) }) { pad ->
        Column(Modifier.padding(pad).padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("Your account", style = MaterialTheme.typography.headlineSmall)
            OutlinedTextField(s.displayName, { vm.update(displayName = it) }, label = { Text("Display name") }, singleLine = true, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(s.username, { vm.update(username = it) }, label = { Text("Username") }, singleLine = true, modifier = Modifier.fillMaxWidth())
            s.error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            if (s.saved) Text("Profile updated", color = MaterialTheme.colorScheme.primary)
            Button(onClick = vm::save, enabled = !s.loading, modifier = Modifier.fillMaxWidth()) { Text(if (s.loading) "Saving…" else "Save changes") }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GroupCreateScreen(vm: GroupViewModel, onBack: () -> Unit, onOpen: (String, String) -> Unit) {
    val s by vm.state.collectAsStateWithLifecycle()
    LaunchedEffect(s.createdId) { s.createdId?.let { onOpen(it, s.name) } }
    Scaffold(topBar = { TopAppBar(title = { Text("New group") }, navigationIcon = {
        IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") }
    }) }) { pad ->
        Column(Modifier.padding(pad).padding(16.dp).fillMaxSize(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            OutlinedTextField(s.name, vm::name, label = { Text("Group name") }, singleLine = true, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(s.query, vm::search, label = { Text("Add members by username") }, singleLine = true, modifier = Modifier.fillMaxWidth())
            s.error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            LazyColumn(Modifier.weight(1f)) { items(s.results, key = { it.id }) { u ->
                ListItem(headlineContent = { Text(u.displayName) }, supportingContent = { Text("@${u.username}") },
                    trailingContent = { Checkbox(u.id in s.selected, { vm.toggle(u) }) },
                    modifier = Modifier.clickable { vm.toggle(u) }) } }
            Button(onClick = vm::create, enabled = !s.loading, modifier = Modifier.fillMaxWidth()) { Text(if (s.loading) "Creating…" else "Create group") }
        }
    }
}

@Composable
private fun ChatRow(c: Chat, onOpen: (String, String) -> Unit) {
    val title = c.peer?.displayName ?: c.name ?: "Conversation"
    ListItem(
        headlineContent = { Text(title, maxLines = 1) },
        supportingContent = { Text(c.lastMessage?.body ?: "No messages yet", maxLines = 1, overflow = TextOverflow.Ellipsis) },
        trailingContent = { if (c.unreadCount > 0) Badge { Text(c.unreadCount.toString()) } },
        modifier = Modifier.clickable { onOpen(c.id, title) }.heightIn(min = 56.dp),
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SearchScreen(vm: SearchViewModel, onBack: () -> Unit, onOpen: (String, String) -> Unit) {
    val s by vm.state.collectAsStateWithLifecycle()
    LaunchedEffect(s.openChatId) { s.openChatId?.let { onOpen(it, s.openChatName ?: ""); vm.consumedOpen() } }
    Scaffold(topBar = { TopAppBar(title = { Text("Find people") }, navigationIcon = {
        IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") } }) }) { pad ->
        Column(Modifier.padding(pad).padding(16.dp).fillMaxSize()) {
            OutlinedTextField(s.query, vm::onQuery, label = { Text("Username or name") }, singleLine = true, modifier = Modifier.fillMaxWidth())
            s.error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            if (s.loading) LinearProgressIndicator(Modifier.fillMaxWidth().padding(top = 8.dp))
            if (s.searched && s.results.isEmpty()) Text("No users found.", Modifier.padding(top = 16.dp))
            LazyColumn { items(s.results, key = { it.id }) { u: User ->
                ListItem(headlineContent = { Text(u.displayName) }, supportingContent = { Text("@${u.username}") },
                    modifier = Modifier.clickable { vm.open(u) }.heightIn(min = 56.dp)) } }
        }
    }
}
