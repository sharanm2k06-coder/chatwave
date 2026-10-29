package com.chatwave.app.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.chatwave.app.AppContainer
import com.chatwave.app.feature.auth.AuthScreen
import com.chatwave.app.feature.auth.AuthViewModel
import com.chatwave.app.feature.chat.ChatScreen
import com.chatwave.app.feature.chat.ChatViewModel
import com.chatwave.app.feature.call.CallScreen
import com.chatwave.app.feature.call.CallViewModel
import com.chatwave.app.feature.home.*
import kotlinx.coroutines.launch
import java.net.URLDecoder
import java.net.URLEncoder

@Suppress("UNCHECKED_CAST")
private inline fun <reified T : ViewModel> factory(crossinline make: () -> T) = object : ViewModelProvider.Factory {
    override fun <V : ViewModel> create(modelClass: Class<V>): V = make() as V
}

@Composable
fun AppNav(c: AppContainer) {
    val nav = rememberNavController()
    val scope = rememberCoroutineScope()
    val start = if (c.tokens.isLoggedIn) "chats" else "auth"
    NavHost(nav, startDestination = start) {
        composable("auth") {
            val vm: AuthViewModel = viewModel(factory = factory { AuthViewModel(c.repository) })
            AuthScreen(vm) { nav.navigate("chats") { popUpTo("auth") { inclusive = true } } }
        }
        composable("chats") {
            val vm: ChatListViewModel = viewModel(factory = factory { ChatListViewModel(c.repository, c.socket) })
            ChatListScreen(vm,
                onOpen = { id, name, peer -> nav.navigate("chat/$id/${URLEncoder.encode(name, "UTF-8")}${peer?.let { "?peer=$it" } ?: ""}") },
                onSearch = { nav.navigate("search") },
                onGlobal = { nav.navigate("chat/00000000-0000-0000-0000-000000000001/Global%20Chat") },
                onGroup = { nav.navigate("group") },
                onProfile = { nav.navigate("profile") },
                onLogout = { scope.launch { c.socket.disconnect(); c.repository.logout(); nav.navigate("auth") { popUpTo("chats") { inclusive = true } } } })
        }
        composable("profile") {
            val vm: ProfileViewModel = viewModel(factory = factory { ProfileViewModel(c.repository, null) })
            ProfileScreen(vm, onBack = { nav.popBackStack() }, onLogout = { scope.launch { c.socket.disconnect(); c.repository.logout(); nav.navigate("auth") { popUpTo("profile") { inclusive = true } } } })
        }
        composable("group") {
            val vm: GroupViewModel = viewModel(factory = factory { GroupViewModel(c.repository) })
            GroupCreateScreen(vm, onBack = { nav.popBackStack() }, onOpen = { id, name -> nav.navigate("chat/$id/${URLEncoder.encode(name, "UTF-8")}") { popUpTo("group") { inclusive = true } } })
        }
        composable("search") {
            val vm: SearchViewModel = viewModel(factory = factory { SearchViewModel(c.repository) })
            SearchScreen(vm, onBack = { nav.popBackStack() }, onOpen = { id, name ->
                nav.popBackStack(); nav.navigate("chat/$id/${URLEncoder.encode(name, "UTF-8")}") })
        }
        composable("chat/{id}/{name}?peer={peer}", arguments = listOf(navArgument("id") { type = NavType.StringType }, navArgument("name") { type = NavType.StringType }, navArgument("peer") { type = NavType.StringType; nullable = true; defaultValue = null })) { e ->
            val id = e.arguments!!.getString("id")!!
            val name = URLDecoder.decode(e.arguments!!.getString("name")!!, "UTF-8")
            val peer = e.arguments!!.getString("peer")
            val vm: ChatViewModel = viewModel(key = id, factory = factory { ChatViewModel(id, c.repository, c.socket, c.tokens.userId) })
            ChatScreen(name, vm, { nav.popBackStack() }, peer?.let { { nav.navigate("call/$id/$it") } })
        }
        composable("call/{conversationId}/{peerId}", arguments = listOf(navArgument("conversationId") { type = NavType.StringType }, navArgument("peerId") { type = NavType.StringType })) { e ->
            val conversationId = e.arguments!!.getString("conversationId")!!
            val peerId = e.arguments!!.getString("peerId")!!
            val vm: CallViewModel = viewModel(factory = factory { CallViewModel(conversationId, peerId, c.socket, c.context) })
            CallScreen(vm) { nav.popBackStack() }
        }
    }
}
