package io.lb.data.models

data class JoinRoomHandshake(
    val userName: String,
    val roomName: String,
    val clientId: String
) : BaseModel(Type.JOIN_ROOM_HANDSHAKE)
