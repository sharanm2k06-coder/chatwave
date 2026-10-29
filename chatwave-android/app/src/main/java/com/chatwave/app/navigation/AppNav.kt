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
                onOpen = { id, name -> nav.navigate("chat/$id/${URLEncoder.encode(name, "UTF-8")}") },
                onSearch = { nav.navigate("search") },
                onLogout = { scope.launch { c.socket.disconnect(); c.repository.logout(); nav.navigate("auth") { popUpTo("chats") { inclusive = true } } } })
        }
        composable("search") {
            val vm: SearchViewModel = viewModel(factory = factory { SearchViewModel(c.repository) })
            SearchScreen(vm, onBack = { nav.popBackStack() }, onOpen = { id, name ->
                nav.popBackStack(); nav.navigate("chat/$id/${URLEncoder.encode(name, "UTF-8")}") })
        }
        composable("chat/{id}/{name}", arguments = listOf(navArgument("id") { type = NavType.StringType }, navArgument("name") { type = NavType.StringType })) { e ->
            val id = e.arguments!!.getString("id")!!
            val name = URLDecoder.decode(e.arguments!!.getString("name")!!, "UTF-8")
            val vm: ChatViewModel = viewModel(key = id, factory = factory { ChatViewModel(id, c.repository, c.socket, c.tokens.userId) })
            ChatScreen(name, vm) { nav.popBackStack() }
        }
    }
}
