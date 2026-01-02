package com.athtech.connect4.protocol.payload;

import java.io.Serializable;

import java.util.Map;

public record LobbyPlayersResponse(
        Map<String, Boolean> players // username -> inGame
) implements Serializable {}
