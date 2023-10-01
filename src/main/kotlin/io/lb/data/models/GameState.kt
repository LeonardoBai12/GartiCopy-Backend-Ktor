package io.lb.data.models

data class GameState(
    val drawingPlayer: String,
    val word: String
) : BaseModel(Type.GAME_STATE)
