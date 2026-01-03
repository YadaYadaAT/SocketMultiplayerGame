package com.athtech.gomoku.protocol.messaging;

public enum MatchEndReason {
    // Draw
    DRAW,                // Both players tie

    // Wins
    WIN_NORMAL,          // Won by normal gameplay
    WIN_QUIT,            // Opponent quit
    WIN_TIMEOUT,         // Opponent AFK / inactivity
    WIN_DISCONNECT,      // Opponent disconnected


    // Losses
    LOSS_NORMAL,         // Lost by normal gameplay
    LOSS_QUIT,           // You quit
    LOSS_TIMEOUT,        // You AFK / inactive
    LOSS_DISCONNECT,     // You disconnected
    WIN_INACTIVE_CLEANUP,  // Server removed match due to inactivity/timeout
    LOSS_INACTIVE_CLEANUP,  // Corresponding loss for opponent
    // Others
    UNKNOWN;            // Fallback for safety


    // helper to check if this is a winning type
    public boolean isWinType() {
        return this == WIN_NORMAL || this == WIN_QUIT || this == WIN_TIMEOUT || this == WIN_DISCONNECT || this == WIN_INACTIVE_CLEANUP;
    }

    // helper to get the corresponding loss for the opponent
    public MatchEndReason correspondingLoss() {
        return switch (this) {
            case WIN_NORMAL -> LOSS_NORMAL;
            case WIN_QUIT -> LOSS_QUIT;
            case WIN_TIMEOUT -> LOSS_TIMEOUT;
            case WIN_DISCONNECT -> LOSS_DISCONNECT;
            case WIN_INACTIVE_CLEANUP -> LOSS_INACTIVE_CLEANUP;
            default -> UNKNOWN;
        };
    }

    // helper to get the corresponding win for the opponent
    public MatchEndReason correspondingWin() {
        return switch (this) {
            case LOSS_NORMAL -> WIN_NORMAL;
            case LOSS_QUIT -> WIN_QUIT;
            case LOSS_TIMEOUT -> WIN_TIMEOUT;
            case LOSS_DISCONNECT -> WIN_DISCONNECT;
            case LOSS_INACTIVE_CLEANUP -> WIN_INACTIVE_CLEANUP;
            default -> UNKNOWN;
        };
    }
}