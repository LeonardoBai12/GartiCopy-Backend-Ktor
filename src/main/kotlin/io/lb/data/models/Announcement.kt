package io.lb.data.models

data class Announcement(
    val message: String,
    val timestamp: Long,
    val announcementType: Type
) : BaseModel(BaseModel.Type.ANNOUNCEMENT) {
    enum class Type {
        PLAYER_GUESSED_WORD,
        PLAYER_JOINED,
        PLAYER_LEFT,
        EVERYBODY_GUESSED_IT,
    }
}
