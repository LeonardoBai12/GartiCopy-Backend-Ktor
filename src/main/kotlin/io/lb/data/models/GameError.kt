package io.lb.data.models

data class GameError(
    val errorType: Type
) : BaseModel(BaseModel.Type.GAME_ERROR) {
    enum class Type {
        ROOM_NOT_FOUND,
    }
}
