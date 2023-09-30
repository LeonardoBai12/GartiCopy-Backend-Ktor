package io.lb.data.models

import io.lb.util.Constants

data class JoinRoomHandshake(
    val userName: String,
    val roomName: String,
    val clientId: String
) : BaseModel(Constants.TYPE_JOIN_ROOM_HANDSHAKE)
