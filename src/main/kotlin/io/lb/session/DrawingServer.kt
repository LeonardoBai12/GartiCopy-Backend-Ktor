package io.lb.session

import io.lb.data.Player
import io.lb.data.Room
import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.DelicateCoroutinesApi

@DelicateCoroutinesApi
class DrawingServer {
    val rooms = ConcurrentHashMap<String, Room>()
    val players = ConcurrentHashMap<String, Player>()

    fun playerJoined(player: Player) {
        players[player.clientId] = player
    }

    fun getRoomWithClientId(clientId: String): Room? {
        val filteredRooms = rooms.filterValues { room ->
            room.players.find { player ->
                player.clientId == clientId
            } != null
        }

        return filteredRooms.values.takeIf {
            it.isNotEmpty()
        }?.toList()?.first()
    }

    fun playerLeft(clientId: String, immediatelyDisconnect: Boolean = false) {
        val playersRoom = getRoomWithClientId(clientId)
        if (immediatelyDisconnect) {
            println("Closing connection to ${players[clientId]?.userName}")
            playersRoom?.removePlayer(clientId)
            players.remove(clientId)
        }
    }
}
