package io.lb.routes

import com.google.gson.JsonParser
import io.ktor.server.routing.Route
import io.ktor.server.routing.route
import io.ktor.server.sessions.get
import io.ktor.server.sessions.sessions
import io.ktor.server.websocket.webSocket
import io.ktor.websocket.CloseReason
import io.ktor.websocket.DefaultWebSocketSession
import io.ktor.websocket.Frame
import io.ktor.websocket.close
import io.ktor.websocket.readText
import io.lb.data.Player
import io.lb.data.Room
import io.lb.data.models.Announcement
import io.lb.data.models.BaseModel
import io.lb.data.models.ChatMessage
import io.lb.data.models.ChosenWord
import io.lb.data.models.DrawData
import io.lb.data.models.GameError
import io.lb.data.models.GameState
import io.lb.data.models.JoinRoomHandshake
import io.lb.data.models.PhaseChange
import io.lb.gson
import io.lb.server
import io.lb.session.DrawingSession
import io.lb.util.Constants
import java.lang.Exception
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.channels.consumeEach

@DelicateCoroutinesApi
fun Route.gameWebSocketRoute() {
    route(Constants.ROUTE_GAME_SOCKET) {
        standardWebSocket { socket, clientId, message, payload ->
            when (payload) {
                is JoinRoomHandshake -> {
                    val room = server.rooms[payload.roomName]

                    room ?: run {
                        val error = GameError(GameError.Type.ROOM_NOT_FOUND)
                        socket.send(Frame.Text(gson.toJson(error)))
                        return@standardWebSocket
                    }

                    val player = Player(
                        payload.userName,
                        socket,
                        payload.clientId
                    )
                    server.playerJoined(player)

                    if (room.containsPlayers(player.userName).not()) {
                        room.addPlayer(player.clientId, player.userName, socket)
                    }
                }
                is DrawData -> {
                    val room = server.rooms[payload.roomName] ?: return@standardWebSocket

                    if (room.phase == Room.Phase.GAME_RUNNING) {
                        room.broadcastToAllExcept(message, clientId)
                    }
                }
                is ChosenWord -> {
                    val room = server.rooms[payload.roomName] ?: return@standardWebSocket
                    room.setWordAndSwitchToGameRunning(payload.chosenWord)
                }
                is ChatMessage -> {
                    val room = server.rooms[payload.roomName] ?: return@standardWebSocket
                    if (room.checkWordsAndNotifyPlayers(payload).not()) {
                        room.broadcast(message)
                    }
                }
            }
        }
    }
}

fun Route.standardWebSocket(
    handleFrame: suspend (
        socket: DefaultWebSocketSession,
        clientId: String,
        message: String,
        payload: BaseModel
    ) -> Unit
) {
    webSocket {
        val session = call.sessions.get<DrawingSession>()

        session ?: run {
            close(CloseReason(CloseReason.Codes.VIOLATED_POLICY, "No session."))
            return@webSocket
        }

        try {
            incoming.consumeEach { frame ->
                if (frame is Frame.Text) {
                    val message = frame.readText()
                    val jsonObject = JsonParser.parseString(message).asJsonObject
                    val type = when (jsonObject.get(Constants.TYPE).asString) {
                        BaseModel.Type.CHAT_MESSAGE.name -> ChatMessage::class.java
                        BaseModel.Type.DRAW_DATA.name -> DrawData::class.java
                        BaseModel.Type.ANNOUNCEMENT.name -> Announcement::class.java
                        BaseModel.Type.JOIN_ROOM_HANDSHAKE.name -> JoinRoomHandshake::class.java
                        BaseModel.Type.PHASE_CHANGE.name -> PhaseChange::class.java
                        BaseModel.Type.CHOSEN_WORD.name -> ChosenWord::class.java
                        BaseModel.Type.GAME_STATE.name -> GameState::class.java
                        else -> BaseModel::class.java
                    }
                    val payload = gson.fromJson(message, type)
                    handleFrame(this, session.clientId, message, payload)
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        } finally {
            // Handle disconnects.
        }
    }
}
