package com.athtech.gomoku.protocol.messaging;

public enum PacketType {


// Every enumeration of PacketType corresponds to one of the available payloads (see com.athtech.gomoku.protocol.payload).
// Explanation for each of them will be in this enum class. The actual payloads only include explanatory comments for the code where required.
    // All requests are Client -> Server
    // All responses are Server -> Client

/*------------------------------------------------
//                 REQUESTS
------------------------------------------------*/

    HANDSHAKE_REQUEST, // Client sends handshake request to check for active connection.

    //LOBBY
    LOBBY_CHAT_MESSAGE_REQUEST, // Payload of this request includes the message to be sent. Lobby message is not saved in server - only broadcast

// Authentication
    LOGIN_REQUEST, // Sends username and password information to server for authentication
    LOGOUT_REQUEST,
    SIGNUP_REQUEST,

//Lobby
    LOBBY_PLAYERS_REQUEST,//lobby updated is maintained by the server mostly, resync responses have it anyway
                            //nevertheless to even cover the case the extreme case which happens only in very small lobbys
                            // (having no broadcast = no login/logout , no get in game or get out of game from no one)
                            // + someone login during the relog/resync phase being done, we put a lobby player request
                            // to manually fetch after resync is done.
//Statistics
    PLAYER_STATS_REQUEST,

// Invitations
    INVITE_REQUEST,
    INVITE_DECISION_REQUEST,

// Game actions
    MOVE_REQUEST,
    GAME_QUIT_REQUEST,


//Rematch
    REMATCH_REQUEST,
    REMATCH_DECISION_REQUEST,

//Reconnect
    RESYNC_REQUEST,

// Misc / Errors
    //--No request

/*------------------------------------------------
//                 RESPONSE
------------------------------------------------*/



//Generic
    INFO_RESPONSE,//so far the only one without payload record class ; simple strings will do as payload
    HANDSHAKE_RESPONSE, // Server sends handshake response to confirm active connection.

//LOBBY
    LOBBY_CHAT_MESSAGE_RESPONSE, // Server broadcasts the response to all the "out" sockets of active Client Handlers, including the message, a timestamp and the username of the server

//Authentication
    LOGIN_RESPONSE, // Server sends different response depending on authentication success or failure. Includes all required authenticated user's data in case of successful authentication.
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
    GAME_START_RESPONSE,
    GAME_STATE_RESPONSE,
    GAME_END_RESPONSE,
    PLAYER_TIMEOUT_RESPONSE,
    DISCONNECT_NOTICE_RESPONSE,
    MATCH_SESSION_ENDED_RESPONSE,
    GAME_QUIT_RESPONSE,
    GAME_QUIT_NOTIFICATION_RESPONSE,
    PLAYER_INACTIVITY_WARNING_RESPONSE,
    PLAYER_DISCONNECTED_NOTIFICATION_RESPONSE,
    PLAYER_RECONNECTED_NOTIFICATION_RESPONSE,
    PLAYER_RECONNECTED_RESPONSE,

//Rematch
    REMATCH_RESPONSE,

//Resync
    RESYNC_RESPONSE,

// Misc / Errors
    ERROR_MESSAGE_RESPONSE,

}
