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

    const val TYPE_CHAT_MESSAGE = "TYPE_CHAT_MESSAGE"
    const val TYPE_DRAW_DATA = "TYPE_DRAWING_DATA"
    const val TYPE_ANNOUNCEMENT = "TYPE_ANNOUNCEMENT"
    const val TYPE_JOIN_ROOM_HANDSHAKE = "TYPE_JOIN_ROOM_HANDSHAKE"
    const val TYPE_GAME_ERROR = "TYPE_GAME_ERROR"

    const val TYPE = "type"
}