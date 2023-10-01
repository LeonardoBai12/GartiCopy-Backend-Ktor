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
import kotlinx.coroutines.DelicateCoroutinesApi

@DelicateCoroutinesApi
fun Route.createRoomRoute() {
    route(Constants.ROUTE_CREATE_ROOM) {
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

@DelicateCoroutinesApi
fun Route.getRoomsRoute() {
    route(Constants.ROUTE_GET_ROOMS) {
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

@DelicateCoroutinesApi
fun Route.joinRoomRoute() {
    route(Constants.ROUTE_JOIN_ROOM) {
        get {
            val userName = call.parameters[Constants.PARAMETER_USER_NAME]
            val roomName = call.parameters[Constants.PARAMETER_ROOM_NAME]

            if (userName == null || roomName == null) {
                call.respond(HttpStatusCode.BadRequest)
                return@get
            }

            val room = server.rooms[roomName]

            when {
                room == null -> {
                    call.respond(
                        HttpStatusCode.OK,
                        BasicApiResponse(
                            false,
                            "Room not found."
                        )
                    )
                }

                room.containsPlayers(userName) -> {
                    call.respond(
                        HttpStatusCode.OK,
                        BasicApiResponse(
                            false,
                            "A player with this user name already joined."
                        )
                    )
                }

                room.players.size > Constants.MAX_ROOM_SIZE -> {
                    call.respond(
                        HttpStatusCode.OK,
                        BasicApiResponse(
                            false,
                            "This room is already full."
                        )
                    )
                }

                else -> {
                    call.respond(HttpStatusCode.OK, BasicApiResponse(true))
                }
            }
        }
    }
}
