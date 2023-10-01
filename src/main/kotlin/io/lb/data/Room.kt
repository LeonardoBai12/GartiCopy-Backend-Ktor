package io.lb.data

import io.ktor.websocket.Frame
import io.ktor.websocket.WebSocketSession
import io.ktor.websocket.send
import io.lb.data.models.Announcement
import io.lb.data.models.ChosenWord
import io.lb.data.models.GameState
import io.lb.data.models.NewWords
import io.lb.data.models.PhaseChange
import io.lb.gson
import io.lb.util.Constants
import io.lb.util.getRandomWords
import io.lb.util.transformToUnderscores
import io.lb.util.words
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

@DelicateCoroutinesApi
class Room(
    val name: String,
    val maxPlayers: Int,
    var players: List<Player> = emptyList()
) {
    private var timerJob: Job? = null
    private var drawingPlayer: Player? = null
    private var winningPlayers = listOf<String>()
    private var word: String? = null
    private var currentWords: List<String>? = null
    private var drawingPlayerIndex = 0

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

    private fun timeAndNotify(milliseconds: Long) {
        timerJob?.cancel()
        timerJob = GlobalScope.launch {
            val phaseChange = PhaseChange(
                phase = phase,
                timestamp = milliseconds,
                drawingPlayer = drawingPlayer?.userName
            )

            repeat((milliseconds / UPDATE_TIME_FREQUENCY).toInt()) {
                if (it != 0) {
                    phaseChange.phase = null
                }
                broadcast(gson.toJson(phaseChange))
                phaseChange.timestamp -= UPDATE_TIME_FREQUENCY
                delay(UPDATE_TIME_FREQUENCY)
            }

            phase = when(phase) {
                Phase.WAITING_FOR_PLAYERS -> Phase.NEW_ROUND
                Phase.WAITING_FOR_START -> Phase.SHOW_WORD
                Phase.NEW_ROUND -> Phase.GAME_RUNNING
                Phase.GAME_RUNNING -> Phase.SHOW_WORD
                else -> Phase.WAITING_FOR_PLAYERS
            }
        }
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

    fun setWordAndSwitchToGameRunning(word: String) {
        this.word = word
        phase = Phase.GAME_RUNNING
    }

    private fun waitingForPlayers() {
        GlobalScope.launch {
            val phaseChange = PhaseChange(
                Phase.WAITING_FOR_PLAYERS,
                Phase.WAITING_FOR_PLAYERS.delay
            )
            broadcast(gson.toJson(phaseChange))
        }
    }

    private fun waitingForStart() {
        GlobalScope.launch {
            notifyPhaseChange(Phase.WAITING_FOR_START)
        }
    }

    private fun newRound() {
        currentWords = getRandomWords(3)

        val newWords = NewWords(currentWords ?: emptyList())
        nextDrawingPlayer()

        GlobalScope.launch {
            drawingPlayer?.socket?.send(Frame.Text(gson.toJson(newWords)))
            timeAndNotify(Phase.NEW_ROUND.delay)
        }
    }

    private fun nextDrawingPlayer() {
        drawingPlayer?.isDrawing = false

        if (players.isEmpty())
            return

        drawingPlayer = if (drawingPlayerIndex < players.size) {
            players[drawingPlayerIndex]
        } else players.last()

        if (drawingPlayerIndex < players.size - 1)
            drawingPlayerIndex++
        else drawingPlayerIndex = 0
    }

    private fun gameRunning() {
        winningPlayers = listOf()

        val wordToSend = word ?: currentWords?.random() ?: words.random()
        val wordWithUnderscores = wordToSend.transformToUnderscores()
        val drawingUsername = (drawingPlayer ?: players.random()).userName
        val gameStateForDrawingPlayer = GameState(
            drawingUsername,
            wordToSend
        )
        val gameStateForGuessingPlayers = GameState(
            drawingUsername,
            wordWithUnderscores
        )
        GlobalScope.launch {
            broadcastToAllExcept(
                gson.toJson(gameStateForGuessingPlayers),
                drawingPlayer?.clientId ?: players.random().clientId
            )
            drawingPlayer?.socket?.send(
                Frame.Text(
                    gson.toJson(gameStateForDrawingPlayer)
                )
            )

            timeAndNotify(Phase.GAME_RUNNING.delay)

            println("Drawing phase in room $name started. It'll last " +
                    "${Phase.GAME_RUNNING.delay / 1000}s")
        }
    }

    private fun showWord() {
        GlobalScope.launch {
            if (winningPlayers.isEmpty()) {
                drawingPlayer?.let {
                    it.score -= PENALTY_NOBODY_GUESSED_IT
                }
            }
            word?.let {
                val chosenWord = ChosenWord(it, name)
                broadcast(gson.toJson(chosenWord))
            }
            notifyPhaseChange(Phase.SHOW_WORD)
        }
    }

    private suspend fun notifyPhaseChange(phase: Phase) {
        timeAndNotify(phase.delay)
        val phaseChange = PhaseChange(phase, phase.delay)
        broadcast(gson.toJson(phaseChange))
    }

    enum class Phase(val delay: Long) {
        WAITING_FOR_PLAYERS(120000L),
        WAITING_FOR_START(10000L),
        NEW_ROUND(20000L),
        GAME_RUNNING(60000L),
        SHOW_WORD(10000L),
    }

    companion object {
        const val UPDATE_TIME_FREQUENCY = 1000L

        const val PENALTY_NOBODY_GUESSED_IT = 50
    }
}
