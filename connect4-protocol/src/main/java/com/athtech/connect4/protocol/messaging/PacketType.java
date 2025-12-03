package com.athtech.connect4.protocol.messaging;

public enum PacketType {
    // Authentication
    LOGIN_REQUEST,
    LOGIN_RESPONSE,
    SIGNUP_REQUEST,
    SIGNUP_RESPONSE,

    // Invitations
    INVITE_REQUEST,
    INVITE_RESPONSE,
    INVITE_NOTIFICATION,
    INVITE_DECISION_REQUEST,
    INVITE_DECISION_RESPONSE,

    // Game actions
    MOVE_REQUEST,
    GAME_STATE,

    // Misc / Errors
    ERROR_MESSAGE,
    LOBBY_PLAYERS
}
