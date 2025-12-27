package com.athtech.connect4.protocol.messaging;

public enum PacketType {





/*------------------------------------------------
//                 REQUESTS
------------------------------------------------*/


// Authentication
    LOGIN_REQUEST,
    LOGOUT_REQUEST,
    SIGNUP_REQUEST,

//Lobby
    //--No request, send On change by server

//Statistics
    PLAYER_STATS_REQUEST,

// Invitations
    INVITE_REQUEST,
    INVITE_DECISION_REQUEST,

// Game actions
    MOVE_REQUEST,


//Rematch
    REMATCH_REQUEST,
    REMATCH_DECISION_REQUEST,

//Reconnect
    RECONNECT_REQUEST,

// Misc / Errors
    //--No request

/*------------------------------------------------
//                 RESPONSE
------------------------------------------------*/

//Generic
    INFO_RESPONSE,//so far the only one without payload record class ; simple strings will do as payload

//Authentication
    LOGIN_RESPONSE,
    LOGOUT_RESPONSE,
    SIGNUP_RESPONSE,

//Lobby
    LOBBY_PLAYERS_RESPONSE,

//Statistics
    PLAYER_STATS_RESPONSE,

// Invitations
    INVITE_RESPONSE,
    INVITE_NOTIFICATION_RESPONSE,
    INVITE_DECISION_RESPONSE,

// Game actions
    MOVE_REJECTED_RESPONSE,
    GAME_STATE_RESPONSE,
    GAME_END_RESPONSE,
    PLAYER_TIMEOUT_RESPONSE,
    DISCONNECT_NOTICE_RESPONSE,

//Rematch
    REMATCH_RESPONSE,
    REMATCH_NOTIFICATION_RESPONSE,

//Reconnect
    RECONNECT_RESPONSE,

// Misc / Errors
    ERROR_MESSAGE_RESPONSE,

}
