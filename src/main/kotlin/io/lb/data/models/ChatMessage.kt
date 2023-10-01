package io.lb.data.models

data class ChatMessage(
    val from: String,
    val roomName: String,
    val message: String,
    val timestamp: Long
) : BaseModel(Type.CHAT_MESSAGE)
