package com.athtech.connect4.protocol.payload;

import java.io.Serializable;

public record ReconnectResponse(
        boolean success,
        String message,
        String[] lobbyPlayers,
        PlayerStatsResponse myStats,
        InviteNotificationResponse[] pendingInvites,
        GameStateResponse currentGameState,
        String relogCode
) implements Serializable{}
