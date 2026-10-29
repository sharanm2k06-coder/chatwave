package com.chatwave.app.data

import retrofit2.http.*

interface Api {
    @POST("api/v1/auth/register") suspend fun register(@Body b: RegisterReq): AuthResponse
    @POST("api/v1/auth/login") suspend fun login(@Body b: LoginReq): AuthResponse
    @POST("api/v1/auth/refresh") suspend fun refresh(@Body b: RefreshReq): AuthResponse
    @POST("api/v1/auth/logout") suspend fun logout(@Body b: RefreshReq)
    @GET("api/v1/users/me") suspend fun me(): User
    @GET("api/v1/users/search") suspend fun search(@Query("q") q: String): UserList
    @GET("api/v1/chats") suspend fun chats(): ChatList
    @POST("api/v1/chats/direct") suspend fun direct(@Body b: DirectReq): DirectResp
    @GET("api/v1/chats/{id}/messages") suspend fun history(
        @Path("id") id: String, @Query("before") before: Long? = null, @Query("limit") limit: Int = 30): HistoryPage
}
