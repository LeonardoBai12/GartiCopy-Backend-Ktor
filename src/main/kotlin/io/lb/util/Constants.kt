package io.lb.util

object Constants {
    const val MIN_ROOM_SIZE = 2
    const val MAX_ROOM_SIZE = 8

    const val ROUTE_CREATE_ROOM = "/api/createRoom"
    const val ROUTE_GET_ROOMS = "/api/getRooms"
    const val ROUTE_JOIN_ROOM = "/api/joinRoom"

    const val ROUTE_GAME_SOCKET = "/ws/draw"

    const val PARAMETER_SEARCH_QUERY = "searchQuery"
    const val PARAMETER_USER_NAME = "userName"
    const val PARAMETER_ROOM_NAME = "roomName"

    const val TYPE = "type"

    const val PING_FREQUENCY = 3000L
}