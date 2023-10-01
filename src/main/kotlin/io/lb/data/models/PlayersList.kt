package io.lb.data.models

data class PlayersList(
    val players: List<PlayerData>
): BaseModel(Type.PLAYERS_LIST)
