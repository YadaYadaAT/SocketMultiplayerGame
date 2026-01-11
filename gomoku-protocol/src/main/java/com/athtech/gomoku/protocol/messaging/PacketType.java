package com.athtech.gomoku.protocol.messaging;
//STUDENTS-CODE-NUMBER : CSY-22115
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
    LOGOUT_REQUEST, // When user wants to log out
    SIGNUP_REQUEST, // When user wants to create an account

//Lobby
    LOBBY_PLAYERS_REQUEST,// LEGACY- USED BY THE CLI CLIENT VERSION TO ENSURE VALID INVITES
                            // lobby updated is maintained by the server mostly, resync responses have it anyway
                            //nevertheless to even cover the case the extreme case which happens only in very small lobbys
                            // (having no broadcast = no login/logout , no get in game or get out of game from no one)
                            // + someone login during the relog/resync phase being done, we put a lobby player request
                            // to manually fetch after resync is done.
//Statistics
    PLAYER_STATS_REQUEST, // User stats. Used as a failsafe, but this data is maintained and communicated at user login regardless.

// Invitations
    INVITE_REQUEST, // Client uses this to invite another user to a game
    INVITE_DECISION_REQUEST, // Client informs server of accepted or declined invites per user

// Game actions
    MOVE_REQUEST, // When a user performs a move in game, move data (row and column of the placed piece) is sent to the server
    GAME_QUIT_REQUEST, // Quit option: Sent when a user wants to exit a running game


//Rematch
    REMATCH_REQUEST, // Sent when a user wants to request a rematch
    REMATCH_DECISION_REQUEST, // Client informs server of accepted or declined rematch per user

//Reconnect
    RESYNC_REQUEST, // For an already existing session, this is sent automatically to the server along with a relog code if:
                    // 1. The client has not received a server response (after having sent a packet) in 15 secs or longer
                    // 2. Logged-in user has been disconnected

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
    LOGOUT_RESPONSE, // Successful or failed logout is communicated back to the user
    SIGNUP_RESPONSE, // Successful or failed signup is communicated back to the user

//Lobby
    LOBBY_PLAYERS_RESPONSE, // Server sends this to all logged-in users every time there is a change in the lobby state (e.g. a new user joining, logging in or out, entering or exiting a game).

//Statistics
    PLAYER_STATS_RESPONSE, // Data is sent back to client

// Invitations
    INVITE_RESPONSE, // Sent to the user who generated an invitation: whether invite is delivered successfully
    INVITE_NOTIFICATION_RESPONSE, // Sent to the user who receives an invitation
    INVITE_DECISION_RESPONSE, // Server accepts invite acceptance or rejection from-to a specific user. Game start is triggered if the response (sent to both users) is acceptance of the invite.

// Game actions
    MOVE_REJECTED_RESPONSE, // Sent if a player's move is invalid. Informs user that their move is rejected and allows them to re-place a piece
    GAME_START_RESPONSE, // This is sent along with the INVITE_DECISION_RESPONSE if the "accepted" boolean field is set to true. This may also be sent at a rematch request or any other scenario of the game starting.
    GAME_STATE_RESPONSE, // At every game state change (practically after every valid move request) this packet is sent back to the client
    GAME_END_RESPONSE, // Sent after game ends with a winner
    MATCH_SESSION_ENDED_RESPONSE,
    GAME_QUIT_RESPONSE, // Sent after a user tries to quit the game
    GAME_QUIT_NOTIFICATION_RESPONSE, // Informational. Sent to the player when their opponent has quit
    PLAYER_INACTIVITY_WARNING_RESPONSE, // Sent to an inactive player if it is their turn
    PLAYER_DISCONNECTED_NOTIFICATION_RESPONSE, // Informational. Sent to the player when their opponent has disconnected
    PLAYER_RECONNECTED_NOTIFICATION_RESPONSE, // Informational. Sent to the player when their opponent has reconnected
    PLAYER_RECONNECTED_RESPONSE, // Informational. Sent to a player after they reconnect

//Rematch
    REMATCH_RESPONSE, // Sent after accepting a rematch request along with a message

//Resync
    RESYNC_RESPONSE, // Sends the same data as login response for a reconnected user

// Misc / Errors
    ERROR_MESSAGE_RESPONSE, // If the server receives a packet of unsupported NetPacket Type

}
