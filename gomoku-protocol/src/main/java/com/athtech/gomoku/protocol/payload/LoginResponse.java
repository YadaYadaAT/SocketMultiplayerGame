package com.athtech.gomoku.protocol.payload;

import java.io.Serializable;

// If success = false, all other fields are set to null.
// If authentication is successful, all fields are sent back to the respective client
public record LoginResponse(
        boolean success, // Auth check
        String message, // "Welcome" or "rejected credentials"
        String relogCode, // Unique code used to reestablish connection (one-time use). In case of inactive user, this is automatically changed to force re-login.
        PlayerStatsResponse myStats, // E.g. Wins, Games, Losses. Only exposed to the authenticated user.
        InviteNotificationResponse[] pendingInvites, // All invites directed at the authenticated user
        GameStateResponse currentGameState, // All game data that is returned to the user. Null if user is not currently in a game
        String username, // Authenticated user's username (unique)
        String nickname // Authenticated user's nickname (non-unique)
) implements Serializable {}