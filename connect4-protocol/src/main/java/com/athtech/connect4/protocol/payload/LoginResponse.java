package com.athtech.connect4.protocol.payload;

import java.io.Serializable;

public record LoginResponse(
        boolean success,
        String message,
        String relogCode,
        PlayerStatsResponse stats
) implements Serializable {}
