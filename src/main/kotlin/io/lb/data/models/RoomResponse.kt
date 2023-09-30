package io.lb.data.models

data class RoomResponse(
    val name: String,
    val maxPlayers: Int,
    val playersCount: Int
)