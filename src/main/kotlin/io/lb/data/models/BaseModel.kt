package io.lb.data.models

abstract class BaseModel(val type: Type) {
    enum class Type {
        CHAT_MESSAGE,
        DRAW_DATA,
        ANNOUNCEMENT,
        JOIN_ROOM_HANDSHAKE,
        GAME_ERROR,
        PHASE_CHANGE,
        CHOSEN_WORD,
        GAME_STATE,
        NEW_WORD,
        PLAYERS_LIST,
        PING,
        DISCONNECT_REQUEST,
        DRAW_ACTION,
        ROUND_DRAW_INFO,
    }
}
