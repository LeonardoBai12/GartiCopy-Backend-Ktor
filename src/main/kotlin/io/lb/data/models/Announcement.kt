package io.lb.data.models

import io.lb.util.Constants

data class Announcement(
    val message: String,
    val timestamp: Long,
    val announcementType: Type
) : BaseModel(Constants.TYPE_ANNOUNCEMENT) {
    enum class Type {
        PLAYER_GUESSED_WORD,
        PLAYER_JOINED,
        PLAYER_LEFT,
        EVERYBODY_GUESSED_IT,
    }
}
