package io.lb.data.models

import io.lb.util.Constants

data class ChatMessage(
    val from: String,
    val roomName: String,
    val message: String,
    val timestamp: Long
) : BaseModel(Constants.TYPE_CHAT_MESSAGE)
