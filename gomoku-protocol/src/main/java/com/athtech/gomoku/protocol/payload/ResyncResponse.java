package com.athtech.gomoku.protocol.payload;

import java.io.Serializable;

public record ResyncResponse(
        boolean success,
        String message,
        LobbyPlayersResponse lobbyPlayers,
        PlayerStatsResponse myStats,
        InviteNotificationResponse[] pendingInvites,
        GameStateResponse currentGameState,
        String relogCode,
        String nickname,
        String username
) implements Serializable{}
