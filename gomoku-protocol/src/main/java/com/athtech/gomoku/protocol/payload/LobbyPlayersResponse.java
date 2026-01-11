package com.athtech.gomoku.protocol.payload;
//STUDENTS-CODE-NUMBER : CSY-22115
import java.io.Serializable;

import java.util.Map;

public record LobbyPlayersResponse(
        Map<String, Boolean> players // username -> inGame
) implements Serializable {}
