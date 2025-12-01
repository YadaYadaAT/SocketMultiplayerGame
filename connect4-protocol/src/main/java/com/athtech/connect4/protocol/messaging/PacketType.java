package com.athtech.connect4.protocol.messaging;

public enum PacketType {
    ERROR,
    GAME_STATE,
    MOVE_RESULT,
    CHAT,
    LOGIN_REQUEST,
    LOGIN_RESPONSE,
    SIGNUP_REQUEST,
    SIGNUP_RESPONSE
}
