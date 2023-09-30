package io.lb.data

import io.ktor.websocket.Frame
import kotlinx.coroutines.isActive

class Room(
    val name: String,
    val maxPlayers: Int,
    var players: List<Player> = emptyList()
) {
    suspend fun broadcast(message: String) {
        players.forEach { player ->
            player.socket.takeIf {
                it.isActive
            }?.send(Frame.Text(message))
        }
    }

    suspend fun broadcastToAllExcept(message: String, clientId: String) {
        players.forEach { player ->
            player.socket.takeIf {
                it.isActive && player.clientId != clientId
            }?.send(Frame.Text(message))
        }
    }
}
