package io.lb.session

import io.lb.data.Player
import io.lb.data.Room
import java.util.concurrent.ConcurrentHashMap

class DrawingServer {
    val rooms = ConcurrentHashMap<String, Room>()
    val players = ConcurrentHashMap<String, Player>()

    fun playerJoined(player: Player) {
        players[player.clientId] = player
    }

    fun roomWithClientId(clientId: String): Room? {
        val filteredRooms = rooms.filterValues { room ->
            room.players.find { player ->
                player.clientId == clientId
            } != null
        }

        return filteredRooms.values.takeIf {
            it.isNotEmpty()
        }?.toList()?.first()
    }
}
