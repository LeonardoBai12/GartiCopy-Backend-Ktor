package io.lb.routes

import io.ktor.http.HttpStatusCode
import io.ktor.server.application.call
import io.ktor.server.request.receiveNullable
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.routing.route
import io.lb.data.Room
import io.lb.data.models.BasicApiResponse
import io.lb.data.models.CreateRoomRequest
import io.lb.data.models.RoomResponse
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

fun Route.getRooms() {
    route(Constants.GET_ROOMS_ROUTE) {
        get {
            val searchQuery = call.parameters[Constants.PARAMETER_SEARCH_QUERY]

            searchQuery ?: run {
                call.respond(HttpStatusCode.BadRequest)
                return@get
            }

            val roomResult = server.rooms.filterKeys {
                it.contains(searchQuery, ignoreCase = true)
            }

            val roomResponse = roomResult.values.map {
                RoomResponse(it.name, it.maxPlayers, it.players.size)
            }.sortedBy { it.name }

            call.respond(HttpStatusCode.OK, roomResponse)
        }
    }
}
