package io.lb.data

import io.ktor.websocket.Frame
import io.ktor.websocket.WebSocketSession
import io.lb.data.models.Announcement
import io.lb.gson
import io.lb.util.Constants
import kotlinx.coroutines.isActive

class Room(
    val name: String,
    val maxPlayers: Int,
    var players: List<Player> = emptyList()
) {
    init {
        setPhaseChangedListener { newPhase ->
            when (newPhase) {
                Phase.WAITING_FOR_PLAYERS -> waitingForPlayers()
                Phase.WAITING_FOR_START -> waitingForStart()
                Phase.NEW_ROUND -> newRound()
                Phase.GAME_RUNNING -> gameRunning()
                Phase.SHOW_WORD -> showWord()
            }
        }
    }

    private var phaseChangedListener: ((Phase) -> Unit)? =null
    var phase = Phase.WAITING_FOR_PLAYERS
        set(value) {
            synchronized(field) {
                field = value
                phaseChangedListener?.let { change ->
                    change(value)
                }
            }
        }

    private fun setPhaseChangedListener(listener: (Phase) -> Unit) {
        phaseChangedListener = listener
    }

    suspend fun addPlayer(
        clientId: String,
        userName: String,
        socket: WebSocketSession
    ): Player {
        val player = Player(userName, socket, clientId)
        players += player

        if (players.size == 1) {
            phase = Phase.WAITING_FOR_PLAYERS
        } else if (players.size == Constants.MIN_ROOM_SIZE && phase == Phase.WAITING_FOR_PLAYERS) {
            phase = Phase.WAITING_FOR_START
            players = players.shuffled()
        } else if (phase == Phase.WAITING_FOR_START && players.size == Constants.MAX_ROOM_SIZE) {
            phase = Phase.NEW_ROUND
            players = players.shuffled()
        }

        val announcement = Announcement(
            message = "$userName joined the party!",
            timestamp = System.currentTimeMillis(),
            announcementType = Announcement.Type.PLAYER_JOINED
        )

        broadcast(gson.toJson(announcement))

        return player
    }

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

    fun containsPlayers(userName: String): Boolean {
        return players.find { it.userName == userName } != null
    }

    private fun waitingForPlayers() {

    }

    private fun waitingForStart() {

    }

    private fun newRound() {

    }

    private fun gameRunning() {

    }

    private fun showWord() {

    }

    enum class Phase {
        WAITING_FOR_PLAYERS,
        WAITING_FOR_START,
        NEW_ROUND,
        GAME_RUNNING,
        SHOW_WORD,
    }
}
