package io.lb.data.models

data class PlayerData(
    val userName: String,
    var isDrawData: Boolean = false,
    var score: Int = 0,
    var rank: Int = 0
)
