package com.athtech.connect4.protocol.messaging;

public enum PacketType {
    //
    INFO,//so far the only one without payload record class ; simple strings will do as payload

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
    GAME_END,

    //Rematch
    REMATCH_REQUEST,
    REMATCH_RESPONSE,

    // Misc / Errors
    ERROR_MESSAGE,
    LOBBY_PLAYERS,
    LOBBY_UPDATE //TODO: we need to decide if we are going to use LobbyUpdate or Lobby players or neither...
}
