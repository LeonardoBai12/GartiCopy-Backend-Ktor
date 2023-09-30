package io.lb.routes

import io.ktor.http.HttpStatusCode
import io.ktor.server.application.call
import io.ktor.server.request.receiveNullable
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.post
import io.ktor.server.routing.route
import io.lb.data.Room
import io.lb.data.models.BasicApiResponse
import io.lb.data.models.CreateRoomRequest
import io.lb.server
import io.lb.util.Constants

fun Route.createRoomRoute() {
    route(Constants.CREATE_ROOM_ROUTE) {
        post {
            val roomRequest = call.receiveNullable<CreateRoomRequest>()

            roomRequest ?: run {
                call.respond(HttpStatusCode.BadRequest)
                return@post
            }

            server.rooms[roomRequest.name]?.run {
                call.respond(
                    HttpStatusCode.OK,
                    BasicApiResponse(false, "Room already exists.")
                )
                return@post
            }

            if (roomRequest.maxPlayers < Constants.MIN_ROOM_SIZE) {
                call.respond(
                    HttpStatusCode.OK,
                    BasicApiResponse(
                        false,
                        "Not enough players (minimum is ${Constants.MIN_ROOM_SIZE})."
                    )
                )
                return@post
            }

            if (roomRequest.maxPlayers > Constants.MAX_ROOM_SIZE) {
                call.respond(
                    HttpStatusCode.OK,
                    BasicApiResponse(
                        false,
                        "Too many players (maximum is ${Constants.MAX_ROOM_SIZE})."
                    )
                )
                return@post
            }

            val room = Room(
                name = roomRequest.name,
                maxPlayers = roomRequest.maxPlayers
            )
            server.rooms[roomRequest.name] = room
            println("Room created: ${roomRequest.name}")

            call.respond(
                HttpStatusCode.OK,
                BasicApiResponse(true)
            )
        }
    }
}
