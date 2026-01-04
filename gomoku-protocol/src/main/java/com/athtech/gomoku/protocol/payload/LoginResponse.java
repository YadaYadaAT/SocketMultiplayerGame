package com.athtech.gomoku.protocol.payload;

import java.io.Serializable;

public record LoginResponse(
        boolean success,
        String message,
        String relogCode,
        PlayerStatsResponse myStats,
        InviteNotificationResponse[] pendingInvites,
        GameStateResponse currentGameState,
        String username,
        String nickname
) implements Serializable {}