package io.lb.data.models

data class GameError(
    val type: Type
) {
    enum class Type {
        ROOM_NOT_FOUND,
    }
}
