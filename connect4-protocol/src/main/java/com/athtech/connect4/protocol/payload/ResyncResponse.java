package com.athtech.connect4.protocol.payload;

import java.io.Serializable;

public record ResyncResponse(
        boolean success,
        String message,
        LobbyPlayersResponse lobbyPlayers,
        PlayerStatsResponse myStats,
        InviteNotificationResponse[] pendingInvites,
        GameStateResponse currentGameState,
        String relogCode
) implements Serializable{}
