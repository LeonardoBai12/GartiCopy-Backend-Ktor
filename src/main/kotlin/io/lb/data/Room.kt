package io.lb.data

import io.ktor.websocket.Frame
import io.ktor.websocket.WebSocketSession
import io.lb.data.models.Announcement
import io.lb.data.models.ChatMessage
import io.lb.data.models.ChosenWord
import io.lb.data.models.DrawData
import io.lb.data.models.GameState
import io.lb.data.models.NewWords
import io.lb.data.models.PhaseChange
import io.lb.data.models.PlayerData
import io.lb.data.models.PlayersList
import io.lb.data.models.RoundDrawInfo
import io.lb.gson
import io.lb.server
import io.lb.util.Constants
import io.lb.util.getRandomWords
import io.lb.util.matchesWord
import io.lb.util.transformToUnderscores
import io.lb.util.words
import java.util.concurrent.ConcurrentHashMap
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
    private var startTime = 0L

    private val playerRemoveJobs = ConcurrentHashMap<String, Job>()
    private val leftPlayers = ConcurrentHashMap<String, Pair<Player, Int>>()

    private var currentRoundDrawData: List<String> = emptyList()
    var lastDrawData: DrawData? = null

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

    private var phaseChangedListener: ((Phase) -> Unit)? = null
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

    private suspend fun sendCurrentRoundDrawInfoToPlayer(player: Player) {
        if (phase == Phase.GAME_RUNNING || phase == Phase.SHOW_WORD) {
            player.socket.send(
                Frame.Text(gson.toJson(RoundDrawInfo(currentRoundDrawData)))
            )
        }
    }

    fun addSerializedDrawInfo(drawAction: String) {
        currentRoundDrawData += drawAction
    }

    private suspend fun finishOffDrawing() {
        lastDrawData?.takeIf {
            currentRoundDrawData.isNotEmpty() && it.motionEvent == DrawData.ACTION_MOVE
        }?.let {
            val finishDrawData = it.copy(motionEvent = DrawData.ACTION_UP)
            broadcast(gson.toJson(finishDrawData))
        }
    }

    suspend fun addPlayer(
        clientId: String,
        userName: String,
        socket: WebSocketSession
    ): Player {
        var indexToAdd = players.lastIndex
        val player = if (leftPlayers.containsKey(clientId)) {
            val leftPlayer = leftPlayers[clientId]
            leftPlayer?.first?.let {
                it.socket = socket
                it.isDrawing = drawingPlayer?.clientId == clientId
                indexToAdd = leftPlayer.second

                playerRemoveJobs[clientId]?.cancel()
                playerRemoveJobs.remove(clientId)
                leftPlayers.remove(clientId)
                it
            } ?: Player(userName, socket, clientId)
        } else {
            Player(userName, socket, clientId)
        }

        indexToAdd = when {
            players.isEmpty() -> 0
            indexToAdd >= players.size -> players.lastIndex
            else -> indexToAdd
        }

        val temporaryPlayers = players.toMutableList()
        temporaryPlayers.add(indexToAdd, player)
        players = temporaryPlayers.toList()

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

        sendWordsToPlayers(player)
        broadcastPlayersState()
        sendCurrentRoundDrawInfoToPlayer(player)
        broadcast(gson.toJson(announcement))

        return player
    }

    fun removePlayer(clientId: String) {
        val player = players.find { it.clientId == clientId } ?: return
        val index = players.indexOf(player)
        leftPlayers[clientId] = player to index
        players -= player

        playerRemoveJobs[clientId] = GlobalScope.launch {
            delay(PLAYER_REMOVE_TIME)
            val playerToRemove = leftPlayers[clientId]
            leftPlayers.remove(clientId)
            playerToRemove?.let {
                players -= it.first
            }
            playerRemoveJobs.remove(clientId)
        }

        val announcement = Announcement(
            "${player.userName} left the party :(",
            System.currentTimeMillis(),
            Announcement.Type.PLAYER_LEFT
        )

        GlobalScope.launch {
            broadcastPlayersState()
            broadcast(gson.toJson(announcement))
            if (players.size == 1) {
                phase = Phase.WAITING_FOR_PLAYERS
                timerJob?.cancel()
            } else if (players.isEmpty()) {
                kill()
                server.rooms.remove(name)
            }
        }
    }

    private fun kill() {
        playerRemoveJobs.values.forEach { it.cancel() }
        timerJob?.cancel()
    }

    private fun timeAndNotify(milliseconds: Long) {
        timerJob?.cancel()
        timerJob = GlobalScope.launch {
            startTime = System.currentTimeMillis()
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

            phase = when (phase) {
                Phase.WAITING_FOR_PLAYERS -> Phase.NEW_ROUND
                Phase.WAITING_FOR_START -> Phase.SHOW_WORD
                Phase.NEW_ROUND -> {
                    word = null
                    Phase.GAME_RUNNING
                }
                Phase.GAME_RUNNING -> {
                    finishOffDrawing()
                    Phase.SHOW_WORD
                }
                else -> Phase.WAITING_FOR_PLAYERS
            }
        }
    }

    suspend fun checkWordsAndNotifyPlayers(message: ChatMessage): Boolean {
        if (isGuessCorrect(message).not())
            return false

        val guessingTime = System.currentTimeMillis() - startTime
        val timePercentageLeft = 1F - guessingTime.toFloat() / Phase.GAME_RUNNING.delay
        val score = GUESS_SCORE_DEFAULT + GUESS_SCORE_PERCENTAGE_MULTIPLIER * timePercentageLeft
        val player = players.find { it.userName == message.from }

        player?.let {
            it.score += score.toInt()
        }

        drawingPlayer?.let {
            it.score += GUESS_SCORE_FOR_DRAWING_PLAYERS / players.size
        }

        broadcastPlayersState()

        val announcement = Announcement(
            "${message.from} has guessed it!",
            System.currentTimeMillis(),
            Announcement.Type.PLAYER_GUESSED_WORD
        )

        broadcast(gson.toJson(announcement))
        val isRoundOver = addWinningPlayer(message.from)

        if (isRoundOver) {
            val roundOverAnnouncement = Announcement(
                "Everybody guessed it! New round starting...",
                System.currentTimeMillis(),
                Announcement.Type.EVERYBODY_GUESSED_IT
            )
            broadcast(gson.toJson(roundOverAnnouncement))
        }

        return true
    }

    /**
     * Broadcasts the updated rank and scores - it needs to be called everytime the score has changed.
     */
    private suspend fun broadcastPlayersState() {
        val playersList = players.sortedByDescending { it.score }.map {
            PlayerData(it.userName, it.isDrawing, it.score, it.rank)
        }

        playersList.forEachIndexed { index, playerData ->
            playerData.rank = index + 1
        }

        broadcast(gson.toJson(PlayersList(playersList)))
    }

    private suspend fun sendWordsToPlayers(player: Player) {
        val phaseChange = PhaseChange(phase, phase.delay, drawingPlayer?.userName)

        word?.let { currentWord ->
            drawingPlayer?.let { drawingPlayer ->
                val gameState = GameState(
                    drawingPlayer.userName,
                    if (player.isDrawing || phase == Phase.SHOW_WORD)
                        currentWord
                    else currentWord.transformToUnderscores()
                )
                player.socket.send(Frame.Text(gson.toJson(gameState)))
            }
        }
        player.socket.send(Frame.Text(gson.toJson(phaseChange)))
    }

    private fun addWinningPlayer(userName: String): Boolean {
        winningPlayers += userName

        if (winningPlayers.size == players.lastIndex) {
            phase = Phase.NEW_ROUND
            return true
        }

        return false
    }

    private fun isGuessCorrect(guess: ChatMessage): Boolean {
        return guess.matchesWord(word ?: return false) &&
            winningPlayers.contains(guess.from).not() &&
            guess.from != drawingPlayer?.userName &&
            phase == Phase.GAME_RUNNING
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
        currentRoundDrawData = emptyList()
        currentWords = getRandomWords(3)

        val newWords = NewWords(currentWords ?: emptyList())
        nextDrawingPlayer()

        GlobalScope.launch {
            broadcastPlayersState()
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

        if (drawingPlayerIndex < players.lastIndex)
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

            println(
                "Drawing phase in room $name started. It'll last " +
                    "${Phase.GAME_RUNNING.delay / 1000}s"
            )
        }
    }

    private fun showWord() {
        GlobalScope.launch {
            if (winningPlayers.isEmpty()) {
                drawingPlayer?.let {
                    it.score -= PENALTY_NOBODY_GUESSED_IT
                }
            }

            broadcastPlayersState()

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

        const val PLAYER_REMOVE_TIME = 60000L

        const val PENALTY_NOBODY_GUESSED_IT = 50
        const val GUESS_SCORE_DEFAULT = 50
        const val GUESS_SCORE_PERCENTAGE_MULTIPLIER = 50
        const val GUESS_SCORE_FOR_DRAWING_PLAYERS = 50
    }
}
