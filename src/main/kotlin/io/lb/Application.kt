package io.lb

import com.google.gson.Gson
import io.ktor.serialization.gson.gson
import io.ktor.server.application.Application
import io.ktor.server.application.ApplicationCallPipeline
import io.ktor.server.application.call
import io.ktor.server.application.install
import io.ktor.server.engine.embeddedServer
import io.ktor.server.netty.Netty
import io.ktor.server.plugins.callloging.CallLogging
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation
import io.ktor.server.routing.Routing
import io.ktor.server.sessions.Sessions
import io.ktor.server.sessions.cookie
import io.ktor.server.sessions.get
import io.ktor.server.sessions.sessions
import io.ktor.server.sessions.set
import io.ktor.server.websocket.WebSockets
import io.ktor.util.generateNonce
import io.lb.routes.createRoomRoute
import io.lb.routes.gameWebSocketRoute
import io.lb.routes.getRoomsRoute
import io.lb.routes.joinRoomRoute
import io.lb.session.DrawingServer
import io.lb.session.DrawingSession
import kotlinx.coroutines.DelicateCoroutinesApi

private const val SESSION_NAME = "SESSIONS"
private const val CLIENT_ID = "client_id"
@DelicateCoroutinesApi
val server = DrawingServer()
val gson = Gson()

@DelicateCoroutinesApi
fun main() {
    embeddedServer(
        factory = Netty,
        port = 8001,
        host = "0.0.0.0",
        module = Application::module
    ).start(wait = true)
}

@DelicateCoroutinesApi
fun Application.module() {
    install(Sessions) {
        cookie<DrawingSession>(SESSION_NAME)
    }
    install(WebSockets)

    intercept(ApplicationCallPipeline.Plugins) {
        call.sessions.get<DrawingSession>() ?: {
            val clientId = call.parameters[CLIENT_ID] ?: ""
            call.sessions.set(
                DrawingSession(
                    clientId = clientId,
                    sessionId = generateNonce()
                )
            )
        }
    }

    install(Routing) {
        createRoomRoute()
        getRoomsRoute()
        joinRoomRoute()
        gameWebSocketRoute()
    }

    install(ContentNegotiation) {
        gson {
        }
    }
    install(CallLogging)
}
