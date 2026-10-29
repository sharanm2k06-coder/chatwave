package com.chatwave.app.data

import kotlinx.serialization.Serializable

@Serializable data class User(val id: String, val username: String, val displayName: String, val photoUrl: String? = null)
@Serializable data class AuthResponse(val user: User? = null, val accessToken: String, val refreshToken: String, val expiresIn: Int = 900)
@Serializable data class RegisterReq(val username: String, val password: String, val displayName: String)
@Serializable data class LoginReq(val username: String, val password: String)
@Serializable data class RefreshReq(val refreshToken: String)
@Serializable data class DirectReq(val userId: String)
@Serializable data class DirectResp(val id: String, val peer: User? = null)
@Serializable data class UserList(val items: List<User>)
@Serializable data class LastMessage(val id: String, val seq: Long, val body: String, val senderId: String, val createdAt: String)
@Serializable data class Chat(val id: String, val type: String, val name: String? = null, val peer: User? = null,
                              val lastMessage: LastMessage? = null, val unreadCount: Int = 0)
@Serializable data class ChatList(val items: List<Chat>)
@Serializable data class Message(val id: String, val seq: Long, val conversationId: String, val senderId: String,
                                 val clientMessageId: String, val body: String, val createdAt: String, val status: String = "sent")
@Serializable data class HistoryPage(val items: List<Message>, val nextBefore: Long? = null)
@Serializable data class ApiErrorBody(val error: ApiErrorDetail? = null)
@Serializable data class ApiErrorDetail(val code: String? = null, val message: String? = null)
